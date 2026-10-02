package demo.reactividad.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import demo.reactividad.application.port.out.MenuRepositoryPort;
import demo.reactividad.domain.exception.MenuCodeException;
import demo.reactividad.domain.exception.MenuUnavailableException;
import demo.reactividad.domain.model.Menu;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// Decorates MenuRepositoryAdapter with circuit-breaking/retry/bulkhead, kept out of the use-case layer.
@Component
@Primary
@RequiredArgsConstructor
public class ResilientMenuRepositoryAdapter implements MenuRepositoryPort {

    private static final String RESILIENCE_INSTANCE_NAME = "menu-service-reactivo";

    private final MenuRepositoryAdapter delegate;

    @Override
    @CircuitBreaker(name = RESILIENCE_INSTANCE_NAME, fallbackMethod = "fallbackFindById")
    public Mono<Menu> findById(UUID menuId) {
        return this.delegate.findById(menuId);
    }

    Mono<Menu> fallbackFindById(UUID menuId, Throwable throwable) {
        return Mono.error(new MenuUnavailableException("Service unavailable",
                MenuCodeException.UNAVAILABLE.name()));
    }

    @Override
    @Retry(name = RESILIENCE_INSTANCE_NAME)
    @Bulkhead(name = RESILIENCE_INSTANCE_NAME)
    public Mono<Menu> save(Menu menu) {
        // Mono.defer es obligatorio para que @Retry funcione: sin él, el delegate se
        // invoca una sola vez y cada "reintento" solo resuscribe al mismo resultado ya
        // resuelto (ver docs/resilience.md). Con defer, cada intento vuelve a llamar
        // al delegate de verdad.
        return Mono.defer(() -> this.delegate.save(menu));
    }

    @Override
    @Retry(name = RESILIENCE_INSTANCE_NAME)
    @Bulkhead(name = RESILIENCE_INSTANCE_NAME)
    public Mono<Void> deleteById(UUID menuId) {
        return Mono.defer(() -> this.delegate.deleteById(menuId));
    }

    @Override
    @Retry(name = RESILIENCE_INSTANCE_NAME)
    @Bulkhead(name = RESILIENCE_INSTANCE_NAME)
    public Flux<Menu> saveAll(List<Menu> menus) {
        return Flux.defer(() -> this.delegate.saveAll(menus));
    }
}
