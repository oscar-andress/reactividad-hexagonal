package demo.reactividad.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import demo.reactividad.application.port.out.MenuRepositoryPort;
import demo.reactividad.domain.exception.MenuCodeException;
import demo.reactividad.domain.exception.MenuUnavailableException;
import demo.reactividad.domain.model.Menu;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// Decorates MenuRepositoryAdapter with circuit-breaking, kept out of the use-case layer.
@Component
@Primary
@RequiredArgsConstructor
public class ResilientMenuRepositoryAdapter implements MenuRepositoryPort {

    private final MenuRepositoryAdapter delegate;

    @Override
    @CircuitBreaker(name = "menu-service-reactivo", fallbackMethod = "fallbackFindById")
    public Mono<Menu> findById(UUID menuId) {
        return this.delegate.findById(menuId);
    }

    Mono<Menu> fallbackFindById(UUID menuId, Throwable throwable) {
        return Mono.error(new MenuUnavailableException("Service unavailable",
                MenuCodeException.UNAVAILABLE.name()));
    }

    @Override
    public Mono<Menu> save(Menu menu) {
        return this.delegate.save(menu);
    }

    @Override
    public Mono<Void> deleteById(UUID menuId) {
        return this.delegate.deleteById(menuId);
    }

    @Override
    public Flux<Menu> saveAll(List<Menu> menus) {
        return this.delegate.saveAll(menus);
    }
}
