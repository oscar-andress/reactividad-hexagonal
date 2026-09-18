package demo.reactividad.application.usecase;

import java.time.Duration;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import demo.reactividad.application.port.in.MenuUseCases;
import demo.reactividad.application.port.out.FoodTypeRepositoryPort;
import demo.reactividad.application.port.out.MenuEventPublisher;
import demo.reactividad.application.port.out.MenuRepositoryPort;
import demo.reactividad.domain.exception.MenuCodeException;
import demo.reactividad.domain.exception.MenuNotFoundException;
import demo.reactividad.domain.model.Menu;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuUseCasesService implements MenuUseCases {

    private final MenuRepositoryPort menuRepositoryPort;
    private final FoodTypeRepositoryPort foodTypeRepositoryPort;
    private final MenuEventPublisher menuEventPublisher;

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
                        }));
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
}
