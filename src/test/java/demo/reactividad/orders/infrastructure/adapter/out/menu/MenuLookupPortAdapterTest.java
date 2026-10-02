package demo.reactividad.orders.infrastructure.adapter.out.menu;

import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import demo.reactividad.application.port.in.MenuUseCases;
import demo.reactividad.domain.exception.MenuCodeException;
import demo.reactividad.domain.exception.MenuNotFoundException;
import demo.reactividad.domain.model.Menu;
import demo.reactividad.orders.domain.model.MenuId;
import demo.reactividad.orders.domain.model.MenuSnapshot;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MenuLookupPortAdapterTest {

    @Mock
    private MenuUseCases menuUseCases;

    private MenuLookupPortAdapter menuLookupPortAdapter;

    @BeforeEach
    void setUp() {
        this.menuLookupPortAdapter = new MenuLookupPortAdapter(this.menuUseCases);
    }

    @Test
    void findMenuSnapshot_WhenMenuExists_TranslatesMenuIntoMenuSnapshot() {
        UUID rawMenuId = UUID.randomUUID();
        MenuId menuId = new MenuId(rawMenuId);
        Menu menu = new Menu(rawMenuId, "Menu del dia", "Descripcion");
        when(this.menuUseCases.getMenu(rawMenuId)).thenReturn(Mono.just(menu));

        this.menuLookupPortAdapter.findMenuSnapshot(menuId)
                .as(StepVerifier::create)
                .expectNext(new MenuSnapshot(menuId, "Menu del dia"))
                .expectComplete()
                .verify();
    }

    @Test
    void findMenuSnapshot_WhenMenuDoesNotExist_TranslatesExceptionIntoEmpty() {
        UUID rawMenuId = UUID.randomUUID();
        MenuId menuId = new MenuId(rawMenuId);
        when(this.menuUseCases.getMenu(rawMenuId)).thenReturn(Mono.error(
                new MenuNotFoundException("not found", MenuCodeException.NOT_FOUND.name())));

        this.menuLookupPortAdapter.findMenuSnapshot(menuId)
                .as(StepVerifier::create)
                .expectComplete()
                .verify();
    }
}
