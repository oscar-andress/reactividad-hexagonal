package demo.reactividad.infrastructure.adapter.out.persistence;

import static demo.reactividad.testsupport.fixtures.MenuTestDataBuilder.aMenu;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import demo.reactividad.application.port.out.MenuRepositoryPort;
import demo.reactividad.domain.exception.MenuUnavailableException;
import demo.reactividad.domain.model.Menu;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

// Mismo adaptador que ResilientMenuRepositoryAdapterTest, pero necesita un contexto real de Spring:
// @Retry/@Bulkhead solo actúan a través del proxy AOP, no sobre un "new ResilientMenuRepositoryAdapter(...)".
@SpringBootTest
class ResilientMenuRepositoryAdapterResilienceTest {

    @MockitoBean
    private MenuRepositoryAdapter delegate;

    @Autowired
    private MenuRepositoryPort menuRepositoryPort;

    @Test
    void save_WhenDelegateFailsTransiently_RetriesUntilSuccess() {
        Menu menu = aMenu().build();
        AtomicInteger attempts = new AtomicInteger();
        when(this.delegate.save(menu)).thenAnswer(invocation -> {
            int attempt = attempts.incrementAndGet();
            if (attempt < 3) {
                return Mono.error(new RuntimeException("db down, intento " + attempt));
            }
            return Mono.just(menu);
        });

        this.menuRepositoryPort.save(menu)
                .as(StepVerifier::create)
                .expectNext(menu)
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        assertEquals(3, attempts.get(), "Debería reintentar hasta el 3er intento, que es el que configuramos como max-attempts");
    }

    @Test
    void save_WhenConcurrentCallsExceedBulkheadLimit_RejectsExcessWithBulkheadFullException() {
        Menu menu = aMenu().build();
        when(this.delegate.save(menu)).thenReturn(Mono.never());

        // El bulkhead permite 4 llamadas concurrentes (resilience4j.bulkhead.instances.menu-service-reactivo
        // .max-concurrent-calls=4 en application.properties) — las ocupamos todas a propósito.
        for (int i = 0; i < 4; i++) {
            this.menuRepositoryPort.save(menu).subscribe();
        }

        this.menuRepositoryPort.save(menu)
                .as(StepVerifier::create)
                .expectError(BulkheadFullException.class)
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void findById_WhenDelegateFails_TranslatesToMenuUnavailableExceptionViaFallback() {
        UUID menuId = UUID.randomUUID();
        when(this.delegate.findById(menuId)).thenReturn(Mono.error(new RuntimeException("db down")));

        this.menuRepositoryPort.findById(menuId)
                .as(StepVerifier::create)
                .expectError(MenuUnavailableException.class)
                .verify(Duration.ofSeconds(5));
    }
}
