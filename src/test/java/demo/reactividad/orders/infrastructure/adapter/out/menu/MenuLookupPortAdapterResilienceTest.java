package demo.reactividad.orders.infrastructure.adapter.out.menu;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import demo.reactividad.application.port.in.MenuUseCases;
import demo.reactividad.orders.application.port.out.MenuLookupPort;
import demo.reactividad.orders.application.port.out.OrderRepositoryPort;
import demo.reactividad.orders.domain.model.MenuId;
import demo.reactividad.orders.domain.model.Order;
import demo.reactividad.orders.infrastructure.adapter.out.persistence.entity.OrderEntity;
import demo.reactividad.orders.infrastructure.adapter.out.persistence.repository.OrderR2dbcRepository;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

// Necesita un contexto real de Spring: @Bulkhead solo actúa a través del proxy AOP
// (ver docs/resilience.md). Separado de MenuLookupPortAdapterTest para no pagar el
// costo del contexto en los tests rápidos que solo prueban la traducción.
@SpringBootTest
class MenuLookupPortAdapterResilienceTest {

    @MockitoBean
    private MenuUseCases menuUseCases;

    @MockitoBean
    private OrderR2dbcRepository orderR2dbcRepository;

    @Autowired
    private MenuLookupPort menuLookupPort;

    @Autowired
    private OrderRepositoryPort orderRepositoryPort;

    @Test
    void findMenuSnapshot_WhenConcurrentCallsExceedBulkheadLimit_RejectsExcessWithBulkheadFullException() {
        UUID rawMenuId = UUID.randomUUID();
        MenuId menuId = new MenuId(rawMenuId);
        when(this.menuUseCases.getMenu(rawMenuId)).thenReturn(Mono.never());

        // El bulkhead de orders-menu-lookup permite 3 llamadas concurrentes (ver
        // application.properties) — las ocupamos todas con un Menu que nunca responde.
        for (int i = 0; i < 3; i++) {
            this.menuLookupPort.findMenuSnapshot(menuId).subscribe();
        }

        this.menuLookupPort.findMenuSnapshot(menuId)
                .as(StepVerifier::create)
                .expectError(BulkheadFullException.class)
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void save_WhileMenuLookupBulkheadIsSaturated_StillCompletesImmediately() {
        UUID rawMenuId = UUID.randomUUID();
        MenuId menuId = new MenuId(rawMenuId);
        when(this.menuUseCases.getMenu(rawMenuId)).thenReturn(Mono.never());
        for (int i = 0; i < 3; i++) {
            this.menuLookupPort.findMenuSnapshot(menuId).subscribe();
        }

        Order order = new Order(menuId, "Menu del dia", 1);
        OrderEntity savedEntity = new OrderEntity(UUID.randomUUID(), rawMenuId, "Menu del dia", 1, null);
        when(this.orderR2dbcRepository.save(any())).thenReturn(Mono.just(savedEntity));

        // Guardar un pedido no depende para nada del bulkhead de orders-menu-lookup —
        // debería completar de inmediato aunque ese otro carril esté completamente lleno.
        this.orderRepositoryPort.save(order)
                .as(StepVerifier::create)
                .expectNextCount(1)
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }
}
