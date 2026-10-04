package demo.reactividad.application.usecase;

import static demo.reactividad.testsupport.fixtures.MenuTestDataBuilder.aMenu;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;

import demo.reactividad.application.port.out.FoodTypeRepositoryPort;
import demo.reactividad.application.port.out.ImageStoragePort;
import demo.reactividad.application.port.out.MenuBatchFailurePolicy;
import demo.reactividad.application.port.out.MenuEventPublisher;
import demo.reactividad.application.port.out.MenuRepositoryPort;
import demo.reactividad.domain.exception.MenuNotFoundException;
import demo.reactividad.domain.exception.MenuUnavailableException;
import demo.reactividad.domain.model.FoodType;
import demo.reactividad.domain.model.Menu;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MenuUseCasesServiceTest {

    private static final UUID MENU_ID = UUID.randomUUID();

    @Mock
    private MenuRepositoryPort menuRepositoryPort;

    @Mock
    private FoodTypeRepositoryPort foodTypeRepositoryPort;

    @Mock
    private MenuEventPublisher menuEventPublisher;

    @Mock
    private ImageStoragePort imageStoragePort;

    @Mock
    private MenuBatchFailurePolicy menuBatchFailurePolicy;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private MenuUseCasesService menuUseCasesService;

    @BeforeEach
    void setUp() {
        this.menuUseCasesService = new MenuUseCasesService(
                this.menuRepositoryPort, this.foodTypeRepositoryPort, this.menuEventPublisher, this.imageStoragePort,
                this.menuBatchFailurePolicy, this.meterRegistry);
    }

    @Test
    void getMenu_WhenMenuExists_ReturnsMenuWithFoodTypes() {
        Menu menu = aMenu().withId(MENU_ID).build();
        FoodType foodType = new FoodType(UUID.randomUUID(), "Vegano", true);
        when(this.menuRepositoryPort.findById(MENU_ID)).thenReturn(Mono.just(menu));
        when(this.foodTypeRepositoryPort.findFoodTypeByMenuId(MENU_ID)).thenReturn(Flux.just(foodType));

        this.menuUseCasesService.getMenu(MENU_ID)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    org.junit.jupiter.api.Assertions.assertEquals("DEVOS", result.getTitle());
                    org.junit.jupiter.api.Assertions.assertEquals(Set.of(foodType), result.getFoodTypes());
                })
                .expectComplete()
                .verify();
    }

    @Test
    void getMenu_WhenMenuHasImage_ReturnsPresignedUrl() {
        Menu menu = aMenu().withId(MENU_ID).withImageKey(MENU_ID.toString()).build();
        String presignedUrl = "http://localhost:4566/imagenes-menu/" + MENU_ID + "?X-Amz-Signature=abc";
        when(this.menuRepositoryPort.findById(MENU_ID)).thenReturn(Mono.just(menu));
        when(this.foodTypeRepositoryPort.findFoodTypeByMenuId(MENU_ID)).thenReturn(Flux.empty());
        when(this.imageStoragePort.generatePresignedUrl(MENU_ID.toString(), Duration.ofMinutes(15)))
                .thenReturn(Mono.just(presignedUrl));

        this.menuUseCasesService.getMenu(MENU_ID)
                .as(StepVerifier::create)
                .assertNext(result ->
                        org.junit.jupiter.api.Assertions.assertEquals(presignedUrl, result.getImageUrl()))
                .expectComplete()
                .verify();
    }

    @Test
    void getMenu_WhenMenuHasNoImage_SkipsPresignedUrlGeneration() {
        Menu menu = aMenu().withId(MENU_ID).build();
        when(this.menuRepositoryPort.findById(MENU_ID)).thenReturn(Mono.just(menu));
        when(this.foodTypeRepositoryPort.findFoodTypeByMenuId(MENU_ID)).thenReturn(Flux.empty());

        this.menuUseCasesService.getMenu(MENU_ID)
                .as(StepVerifier::create)
                .assertNext(result -> org.junit.jupiter.api.Assertions.assertEquals(null, result.getImageUrl()))
                .expectComplete()
                .verify();

        verify(this.imageStoragePort, never()).generatePresignedUrl(any(), any());
    }

    @Test
    void getMenu_WhenMenuDoesNotExist_ThrowsMenuNotFoundException() {
        when(this.menuRepositoryPort.findById(MENU_ID)).thenReturn(Mono.empty());

        this.menuUseCasesService.getMenu(MENU_ID)
                .as(StepVerifier::create)
                .expectError(MenuNotFoundException.class)
                .verify();
    }

    @Test
    void createMenu_Success_PublishesEvent() {
        Menu menu = aMenu().build();
        Menu savedMenu = aMenu().withId(MENU_ID).build();
        when(this.menuRepositoryPort.save(menu)).thenReturn(Mono.just(savedMenu));

        this.menuUseCasesService.createMenu(menu)
                .as(StepVerifier::create)
                .expectNext(savedMenu)
                .expectComplete()
                .verify();

        verify(this.menuEventPublisher, times(1)).publish(savedMenu);
    }

    @Test
    void deleteMenu_AfterSuccessfulDelete_StillThrowsNotFound() {
        // Pre-existing behavior carried over from MenuServiceImpl: deleteById returns Mono<Void>,
        // which never emits onNext, so switchIfEmpty always fires even when the delete succeeded.
        when(this.menuRepositoryPort.deleteById(MENU_ID)).thenReturn(Mono.empty());

        this.menuUseCasesService.deleteMenu(MENU_ID)
                .as(StepVerifier::create)
                .expectError(MenuNotFoundException.class)
                .verify();
    }

    @Test
    void streamMenus_DelegatesToEventPublisher() {
        Menu menu = aMenu().withId(MENU_ID).build();
        when(this.menuEventPublisher.subscribe()).thenReturn(Flux.just(menu));

        this.menuUseCasesService.streamMenus()
                .as(StepVerifier::create)
                .expectNext(menu)
                .expectComplete()
                .verify();
    }

    @Test
    void createMenus_WhenBatchSaveFails_DelegatesToFailurePolicyInsteadOfSwallowingSilently() {
        Menu menu = aMenu().build();
        RuntimeException saveError = new RuntimeException("db down");
        when(this.menuRepositoryPort.saveAll(List.of(menu))).thenReturn(Flux.error(saveError));
        when(this.menuBatchFailurePolicy.onBatchFailure(List.of(menu), saveError)).thenReturn(Flux.empty());

        StepVerifier.withVirtualTime(() -> this.menuUseCasesService.createMenus(Flux.just(menu)))
                .thenAwait(Duration.ofSeconds(2))
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        verify(this.menuRepositoryPort, times(1)).saveAll(any());
        verify(this.menuBatchFailurePolicy, times(1)).onBatchFailure(List.of(menu), saveError);
        verify(this.menuEventPublisher, never()).publish(any());
    }

    @Test
    void createMenus_LimitsHowManyBatchesAreSavedConcurrently() {
        int batchSize = 500;
        int batchCount = 9; // > 8 (el límite configurado) para forzar que la saturación sea observable
        List<Menu> menus = IntStream.range(0, batchSize * batchCount)
                .mapToObj(i -> aMenu().build())
                .toList();

        AtomicInteger currentConcurrency = new AtomicInteger();
        AtomicInteger maxObservedConcurrency = new AtomicInteger();
        when(this.menuRepositoryPort.saveAll(any())).thenAnswer(invocation -> {
            List<Menu> batch = invocation.getArgument(0);
            int current = currentConcurrency.incrementAndGet();
            maxObservedConcurrency.accumulateAndGet(current, Math::max);
            return Mono.delay(Duration.ofMillis(100))
                    .thenMany(Flux.fromIterable(batch))
                    .doFinally(signalType -> currentConcurrency.decrementAndGet());
        });

        StepVerifier.withVirtualTime(() -> this.menuUseCasesService.createMenus(Flux.fromIterable(menus)))
                .thenAwait(Duration.ofSeconds(1))
                .thenCancel()
                .verify(Duration.ofSeconds(5));

        org.junit.jupiter.api.Assertions.assertEquals(8, maxObservedConcurrency.get(),
                "El límite de concurrencia configurado para guardar batches debería respetarse");
    }

    @Test
    void updateMenu_Success_PreservesVersionImageKeyAndCreatedAtThenPublishesEvent() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        Menu existingMenu = aMenu()
                .withId(MENU_ID)
                .withCreatedAt(createdAt)
                .withVersion(2L)
                .withImageKey("existing-image-key")
                .build();
        Menu incoming = aMenu().withId(MENU_ID).withTitle("New title").withDescription("New description").build();
        Menu savedMenu = aMenu()
                .withId(MENU_ID)
                .withTitle("New title")
                .withDescription("New description")
                .withCreatedAt(createdAt)
                .build();

        when(this.menuRepositoryPort.findById(MENU_ID)).thenReturn(Mono.just(existingMenu));
        when(this.menuRepositoryPort.save(any(Menu.class))).thenReturn(Mono.just(savedMenu));

        this.menuUseCasesService.updateMenu(incoming)
                .as(StepVerifier::create)
                .expectNext(savedMenu)
                .expectComplete()
                .verify();

        ArgumentCaptor<Menu> savedArgumentCaptor = ArgumentCaptor.forClass(Menu.class);
        verify(this.menuRepositoryPort).save(savedArgumentCaptor.capture());
        Menu savedArgument = savedArgumentCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("New title", savedArgument.getTitle());
        org.junit.jupiter.api.Assertions.assertEquals("New description", savedArgument.getDescription());
        org.junit.jupiter.api.Assertions.assertEquals(2L, savedArgument.getVersion());
        org.junit.jupiter.api.Assertions.assertEquals("existing-image-key", savedArgument.getImageKey());
        org.junit.jupiter.api.Assertions.assertEquals(createdAt, savedArgument.getCreatedAt());
        verify(this.menuEventPublisher, times(1)).publish(savedMenu);
    }

    @Test
    void updateMenu_WhenMenuDoesNotExist_ThrowsMenuNotFoundException() {
        Menu incoming = aMenu().withId(MENU_ID).withTitle("New title").withDescription("New description").build();
        when(this.menuRepositoryPort.findById(MENU_ID)).thenReturn(Mono.empty());

        this.menuUseCasesService.updateMenu(incoming)
                .as(StepVerifier::create)
                .expectError(MenuNotFoundException.class)
                .verify();

        verify(this.menuRepositoryPort, never()).save(any());
    }

    @Test
    void updateMenu_WhenSaveFailsWithOptimisticLock_MapsToMenuUnavailableException() {
        Menu existingMenu = aMenu().withId(MENU_ID).build();
        Menu incoming = aMenu().withId(MENU_ID).withTitle("New title").withDescription("New description").build();

        when(this.menuRepositoryPort.findById(MENU_ID)).thenReturn(Mono.just(existingMenu));
        when(this.menuRepositoryPort.save(any(Menu.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("stale version")));

        this.menuUseCasesService.updateMenu(incoming)
                .as(StepVerifier::create)
                .expectError(MenuUnavailableException.class)
                .verify();
    }

    @Test
    void uploadMenuImage_Success_StoresImageKeyAndPublishesEvent() {
        Menu existingMenu = aMenu().withId(MENU_ID).build();
        byte[] content = "image-bytes".getBytes();
        Menu updatedMenu = aMenu().withId(MENU_ID).withImageKey(MENU_ID.toString()).build();
        String presignedUrl = "http://localhost:4566/imagenes-menu/" + MENU_ID;

        when(this.menuRepositoryPort.findById(MENU_ID)).thenReturn(Mono.just(existingMenu));
        when(this.imageStoragePort.upload(MENU_ID.toString(), content, "image/png")).thenReturn(Mono.empty());
        when(this.menuRepositoryPort.save(any(Menu.class))).thenReturn(Mono.just(updatedMenu));
        when(this.imageStoragePort.generatePresignedUrl(MENU_ID.toString(), Duration.ofMinutes(15)))
                .thenReturn(Mono.just(presignedUrl));

        this.menuUseCasesService.uploadMenuImage(MENU_ID, content, "image/png")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    org.junit.jupiter.api.Assertions.assertEquals(MENU_ID.toString(), result.getImageKey());
                    org.junit.jupiter.api.Assertions.assertEquals(presignedUrl, result.getImageUrl());
                })
                .expectComplete()
                .verify();

        ArgumentCaptor<Menu> savedArgumentCaptor = ArgumentCaptor.forClass(Menu.class);
        verify(this.menuRepositoryPort).save(savedArgumentCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(MENU_ID.toString(), savedArgumentCaptor.getValue().getImageKey());
        verify(this.menuEventPublisher, times(1)).publish(any(Menu.class));
    }

    @Test
    void uploadMenuImage_WhenSaveFailsWithOptimisticLock_MapsToMenuUnavailableException() {
        Menu existingMenu = aMenu().withId(MENU_ID).build();
        byte[] content = "image-bytes".getBytes();

        when(this.menuRepositoryPort.findById(MENU_ID)).thenReturn(Mono.just(existingMenu));
        when(this.imageStoragePort.upload(MENU_ID.toString(), content, "image/png")).thenReturn(Mono.empty());
        when(this.menuRepositoryPort.save(any(Menu.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("stale version")));
        when(this.imageStoragePort.delete(MENU_ID.toString())).thenReturn(Mono.empty());

        this.menuUseCasesService.uploadMenuImage(MENU_ID, content, "image/png")
                .as(StepVerifier::create)
                .expectError(MenuUnavailableException.class)
                .verify();
    }

    @Test
    void uploadMenuImage_WhenMenuDoesNotExist_ThrowsMenuNotFoundException() {
        byte[] content = "image-bytes".getBytes();
        when(this.menuRepositoryPort.findById(MENU_ID)).thenReturn(Mono.empty());

        this.menuUseCasesService.uploadMenuImage(MENU_ID, content, "image/png")
                .as(StepVerifier::create)
                .expectError(MenuNotFoundException.class)
                .verify();

        verify(this.imageStoragePort, never()).upload(any(), any(), any());
    }

    @Test
    void uploadMenuImage_WhenSaveFails_DeletesUploadedImageAndPropagatesOriginalError() {
        Menu existingMenu = aMenu().withId(MENU_ID).build();
        byte[] content = "image-bytes".getBytes();
        RuntimeException saveError = new RuntimeException("db down");

        when(this.menuRepositoryPort.findById(MENU_ID)).thenReturn(Mono.just(existingMenu));
        when(this.imageStoragePort.upload(MENU_ID.toString(), content, "image/png")).thenReturn(Mono.empty());
        when(this.menuRepositoryPort.save(any(Menu.class))).thenReturn(Mono.error(saveError));
        when(this.imageStoragePort.delete(MENU_ID.toString())).thenReturn(Mono.empty());

        this.menuUseCasesService.uploadMenuImage(MENU_ID, content, "image/png")
                .as(StepVerifier::create)
                .expectErrorMatches(error -> error == saveError)
                .verify();

        verify(this.imageStoragePort, times(1)).delete(MENU_ID.toString());
        org.junit.jupiter.api.Assertions.assertEquals(1,
                this.meterRegistry.get("menu.image.orphan_cleanup.attempts").counter().count());
        // "failures" ni siquiera se registra hasta que algo lo incrementa por primera vez:
        // como el delete tuvo éxito en este test, ese contador todavía no existe.
        org.junit.jupiter.api.Assertions.assertEquals(0,
                this.meterRegistry.find("menu.image.orphan_cleanup.failures").counters().size());
    }

    @Test
    void uploadMenuImage_WhenSaveAndCompensationDeleteBothFail_StillPropagatesOriginalSaveError() {
        Menu existingMenu = aMenu().withId(MENU_ID).build();
        byte[] content = "image-bytes".getBytes();
        RuntimeException saveError = new RuntimeException("db down");
        RuntimeException deleteError = new RuntimeException("s3 down");

        when(this.menuRepositoryPort.findById(MENU_ID)).thenReturn(Mono.just(existingMenu));
        when(this.imageStoragePort.upload(MENU_ID.toString(), content, "image/png")).thenReturn(Mono.empty());
        when(this.menuRepositoryPort.save(any(Menu.class))).thenReturn(Mono.error(saveError));
        when(this.imageStoragePort.delete(MENU_ID.toString())).thenReturn(Mono.error(deleteError));

        this.menuUseCasesService.uploadMenuImage(MENU_ID, content, "image/png")
                .as(StepVerifier::create)
                .expectErrorMatches(error -> error == saveError)
                .verify();

        org.junit.jupiter.api.Assertions.assertEquals(1,
                this.meterRegistry.get("menu.image.orphan_cleanup.attempts").counter().count());
        org.junit.jupiter.api.Assertions.assertEquals(1,
                this.meterRegistry.get("menu.image.orphan_cleanup.failures").counter().count());
    }
}
