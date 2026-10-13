package demo.reactividad.orders.infrastructure.adapter.out.menu;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import demo.reactividad.application.port.in.MenuUseCases;
import demo.reactividad.domain.exception.MenuCodeException;
import demo.reactividad.domain.exception.MenuNotFoundException;
import demo.reactividad.orders.application.port.out.MenuLookupPort;
import demo.reactividad.orders.application.port.out.OrderRepositoryPort;
import demo.reactividad.orders.domain.model.MenuId;
import demo.reactividad.orders.domain.model.Order;
import demo.reactividad.orders.infrastructure.adapter.out.persistence.entity.OrderEntity;
import demo.reactividad.orders.infrastructure.adapter.out.persistence.repository.OrderR2dbcRepository;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import reactor.core.Disposable;
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

    // Hallazgo real (ver docs/resilience.md): el bean del bulkhead es un singleton del
    // contexto de Spring, compartido entre TODOS los métodos de esta clase -- una
    // suscripción con Mono.never() que nunca se cancela deja el permiso ocupado para
    // siempre, y rompe cualquier test posterior que use el mismo carril, de forma
    // intermitente según el orden real de ejecución. Se liberan acá explícitamente.
    private final List<Disposable> leakedSubscriptions = new ArrayList<>();

    @AfterEach
    void releaseBulkheadPermits() {
        this.leakedSubscriptions.forEach(Disposable::dispose);
        this.leakedSubscriptions.clear();
    }

    @Test
    void findMenuSnapshot_WhenConcurrentCallsExceedBulkheadLimit_RejectsExcessWithBulkheadFullException() {
        UUID rawMenuId = UUID.randomUUID();
        MenuId menuId = new MenuId(rawMenuId);
        when(this.menuUseCases.getMenu(rawMenuId)).thenReturn(Mono.never());

        // El bulkhead de orders-menu-lookup permite 3 llamadas concurrentes (ver
        // application.properties) — las ocupamos todas con un Menu que nunca responde.
        for (int i = 0; i < 3; i++) {
            this.leakedSubscriptions.add(this.menuLookupPort.findMenuSnapshot(menuId).subscribe());
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
            this.leakedSubscriptions.add(this.menuLookupPort.findMenuSnapshot(menuId).subscribe());
        }

        Order order = new Order(menuId, "Menu del dia", 1, null);
        OrderEntity savedEntity = new OrderEntity(UUID.randomUUID(), rawMenuId, "Menu del dia", 1, null, null);
        when(this.orderR2dbcRepository.save(any())).thenReturn(Mono.just(savedEntity));

        // Guardar un pedido no depende para nada del bulkhead de orders-menu-lookup —
        // debería completar de inmediato aunque ese otro carril esté completamente lleno.
        this.orderRepositoryPort.save(order)
                .as(StepVerifier::create)
                .expectNextCount(1)
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }

    @Test
    void findMenuSnapshot_WhenDelegateFailsTransiently_RetriesUntilSuccess() {
        UUID rawMenuId = UUID.randomUUID();
        MenuId menuId = new MenuId(rawMenuId);
        AtomicInteger attempts = new AtomicInteger(0);
        when(this.menuUseCases.getMenu(rawMenuId)).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() < 3) {
                return Mono.error(new RuntimeException("transient menu-service failure"));
            }
            return Mono.error(new MenuNotFoundException("not used", MenuCodeException.NOT_FOUND.name()));
        });

        // El 3er intento devuelve "no existe" en vez de un menú real -- solo nos
        // interesa confirmar que el delegate se llamó 3 veces (2 fallos + el intento
        // que finalmente "resuelve" el flujo, aunque sea con un resultado vacío).
        this.menuLookupPort.findMenuSnapshot(menuId)
                .as(StepVerifier::create)
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        verify(this.menuUseCases, times(3)).getMenu(rawMenuId);
    }

    @Test
    void findMenuSnapshot_WhenMenuDoesNotExist_NeverRetries() {
        UUID rawMenuId = UUID.randomUUID();
        MenuId menuId = new MenuId(rawMenuId);
        when(this.menuUseCases.getMenu(rawMenuId))
                .thenReturn(Mono.error(new MenuNotFoundException("not found", MenuCodeException.NOT_FOUND.name())));

        this.menuLookupPort.findMenuSnapshot(menuId)
                .as(StepVerifier::create)
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        // "No existe" se traduce a Mono.empty() DENTRO del adapter, antes de que @Retry
        // pueda verlo como un error -- si esto alguna vez reintentara, el mock se
        // llamaría más de una vez.
        verify(this.menuUseCases, times(1)).getMenu(rawMenuId);
    }

    @Test
    void findMenuSnapshot_WhenMenuNeverResponds_FailsWithTimeoutAfterExhaustingRetries() {
        UUID rawMenuId = UUID.randomUUID();
        MenuId menuId = new MenuId(rawMenuId);
        when(this.menuUseCases.getMenu(rawMenuId)).thenReturn(Mono.never());

        this.menuLookupPort.findMenuSnapshot(menuId)
                .as(StepVerifier::create)
                .expectError()
                .verify(Duration.ofSeconds(10));

        // timeout 2s * hasta 3 intentos -- si esto no reintentara, se llamaría solo una vez.
        verify(this.menuUseCases, atLeast(2)).getMenu(rawMenuId);
    }
}
