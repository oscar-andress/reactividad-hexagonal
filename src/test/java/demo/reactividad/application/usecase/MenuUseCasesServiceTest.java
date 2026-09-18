package demo.reactividad.application.usecase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import demo.reactividad.application.port.out.FoodTypeRepositoryPort;
import demo.reactividad.application.port.out.MenuEventPublisher;
import demo.reactividad.application.port.out.MenuRepositoryPort;
import demo.reactividad.domain.exception.MenuNotFoundException;
import demo.reactividad.domain.model.FoodType;
import demo.reactividad.domain.model.Menu;
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

    private MenuUseCasesService menuUseCasesService;

    @BeforeEach
    void setUp() {
        this.menuUseCasesService = new MenuUseCasesService(
                this.menuRepositoryPort, this.foodTypeRepositoryPort, this.menuEventPublisher);
    }

    @Test
    void getMenu_WhenMenuExists_ReturnsMenuWithFoodTypes() {
        Menu menu = new Menu(MENU_ID, "DEVOS", "Menu de prueba", null);
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
    void getMenu_WhenMenuDoesNotExist_ThrowsMenuNotFoundException() {
        when(this.menuRepositoryPort.findById(MENU_ID)).thenReturn(Mono.empty());

        this.menuUseCasesService.getMenu(MENU_ID)
                .as(StepVerifier::create)
                .expectError(MenuNotFoundException.class)
                .verify();
    }

    @Test
    void createMenu_Success_PublishesEvent() {
        Menu menu = new Menu("DEVOS", "Menu de prueba");
        Menu savedMenu = new Menu(MENU_ID, "DEVOS", "Menu de prueba", null);
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
        Menu menu = new Menu(MENU_ID, "DEVOS", "Menu de prueba", null);
        when(this.menuEventPublisher.subscribe()).thenReturn(Flux.just(menu));

        this.menuUseCasesService.streamMenus()
                .as(StepVerifier::create)
                .expectNext(menu)
                .expectComplete()
                .verify();
    }

    @Test
    void createMenus_SavesInBatchesAndSwallowsBatchErrors() {
        Menu menu = new Menu("DEVOS", "Menu de prueba");
        when(this.menuRepositoryPort.saveAll(List.of(menu)))
                .thenReturn(Flux.error(new RuntimeException("db down")));

        StepVerifier.withVirtualTime(() -> this.menuUseCasesService.createMenus(Flux.just(menu)))
                .thenAwait(Duration.ofSeconds(2))
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        verify(this.menuRepositoryPort, times(1)).saveAll(any());
        verify(this.menuEventPublisher, never()).publish(any());
    }
}
