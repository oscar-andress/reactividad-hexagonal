package demo.reactividad.application.usecase;

import java.time.Duration;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import demo.reactividad.application.port.in.MenuUseCases;
import demo.reactividad.application.port.out.FoodTypeRepositoryPort;
import demo.reactividad.application.port.out.ImageStoragePort;
import demo.reactividad.application.port.out.MenuEventPublisher;
import demo.reactividad.application.port.out.MenuRepositoryPort;
import demo.reactividad.domain.exception.MenuCodeException;
import demo.reactividad.domain.exception.MenuNotFoundException;
import demo.reactividad.domain.exception.MenuUnavailableException;
import demo.reactividad.domain.model.Menu;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuUseCasesService implements MenuUseCases {

    private static final Duration IMAGE_URL_EXPIRATION = Duration.ofMinutes(15);

    private final MenuRepositoryPort menuRepositoryPort;
    private final FoodTypeRepositoryPort foodTypeRepositoryPort;
    private final MenuEventPublisher menuEventPublisher;
    private final ImageStoragePort imageStoragePort;

    @Override
    @Transactional(readOnly = true)
    public Mono<Menu> getMenu(UUID menuId) {
        return this.menuRepositoryPort.findById(menuId)
                .switchIfEmpty(Mono.error(() -> new MenuNotFoundException("Menu with id " + menuId + " not found",
                        MenuCodeException.NOT_FOUND.name())))
                .flatMap(menu -> this.foodTypeRepositoryPort.findFoodTypeByMenuId(menuId)
                        .collect(Collectors.toSet())
                        .map(foodTypes -> {
                            menu.setFoodTypes(foodTypes);
                            return menu;
                        }))
                .flatMap(this::withPresignedImageUrlSafely);
    }

    private Mono<Menu> withPresignedImageUrl(Menu menu) {
        if (menu.getImageKey() == null) {
            return Mono.just(menu);
        }
        return this.imageStoragePort.generatePresignedUrl(menu.getImageKey(), IMAGE_URL_EXPIRATION)
                .map(presignedUrl -> {
                    menu.setImageUrl(presignedUrl);
                    return menu;
                });
    }

    @Override
    @Transactional
    public Mono<Menu> createMenu(Menu menu) {
        return this.menuRepositoryPort.save(menu)
                .doOnNext(this.menuEventPublisher::publish);
    }

    @Override
    @Transactional
    public Mono<Void> deleteMenu(UUID menuId) {
        return this.menuRepositoryPort.deleteById(menuId)
                .switchIfEmpty(Mono.error(() -> new MenuNotFoundException("Menu with id " + menuId + " not found",
                        MenuCodeException.NOT_FOUND.name())));
    }

    @Override
    public Flux<Menu> streamMenus() {
        return this.menuEventPublisher.subscribe();
    }

    @Override
    public Flux<Menu> createMenus(Flux<Menu> menus) {
        return menus
                .doOnNext(menu -> log.info("Recieved {}", menu))
                .buffer(500)
                .flatMap(batch -> this.menuRepositoryPort.saveAll(batch)
                        .onErrorResume(error -> {
                            log.error("Error saving menus list: {}", batch);
                            return Flux.empty();
                        }))
                .delayElements(Duration.ofSeconds(2));
    }

    @Override
    @Transactional
    public Mono<Menu> updateMenu(Menu menu) {
        return this.menuRepositoryPort
                .findById(menu.getId())
                .switchIfEmpty(Mono.error(() -> new MenuNotFoundException("Menu with id " + menu.getId() + " not found",
                        MenuCodeException.NOT_FOUND.name())))
                .flatMap(existingMenu -> {
                    Menu updated = existingMenu.withUpdatedDetails(menu.getTitle(), menu.getDescription());
                    return this.menuRepositoryPort.save(updated);
                })
                .doOnNext(this.menuEventPublisher::publish)
                .onErrorMap(OptimisticLockingFailureException.class,
                        ex -> new MenuUnavailableException(
                                "Menu with id " + menu.getId() + " was updated concurrently, please retry",
                                MenuCodeException.CONFLICT.name()));
    }

    @Override
    @Transactional
    public Mono<Menu> uploadMenuImage(UUID menuId, byte[] imageContent, String contentType) {
        return this.menuRepositoryPort
                .findById(menuId)
                .switchIfEmpty(Mono.error(() -> new MenuNotFoundException(
                        "Menu with id " + menuId + " not found", MenuCodeException.NOT_FOUND.name())))
                .flatMap(menu -> uploadAndTagImage(menu, menuId, imageContent, contentType))
                .flatMap(this::saveWithImageCompensation)
                .flatMap(this::withPresignedImageUrlSafely)
                .doOnNext(this.menuEventPublisher::publish)
                .onErrorMap(OptimisticLockingFailureException.class,
                        ex -> new MenuUnavailableException(
                                "Menu with id " + menuId + " was updated concurrently, please retry",
                                MenuCodeException.CONFLICT.name()));
    }

    private Mono<Menu> uploadAndTagImage(Menu menu, UUID menuId, byte[] imageContent, String contentType) {
        return this.imageStoragePort.upload(menuId.toString(), imageContent, contentType)
                .doOnSuccess(unused -> menu.setImageKey(menuId.toString()))
                .thenReturn(menu);
    }

    private Mono<Menu> saveWithImageCompensation(Menu menu) {
        return this.menuRepositoryPort.save(menu)
                .onErrorResume(error -> compensateImageUpload(menu.getImageKey())
                        .then(Mono.error(error)));
    }

    private Mono<Void> compensateImageUpload(String imageKey) {
        return this.imageStoragePort.delete(imageKey)
                .onErrorResume(deleteError -> {
                    log.error("Failed to delete orphaned image {} after a failed save", imageKey, deleteError);
                    return Mono.empty();
                });
    }

    private Mono<Menu> withPresignedImageUrlSafely(Menu menu) {
        return withPresignedImageUrl(menu)
                .doOnError(error -> log.error(
                        "Failed to generate presigned URL for menu {}, returning menu without it",
                        menu.getId(), error))
                .onErrorReturn(menu);
    }
}
