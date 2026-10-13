package demo.reactividad.orders.infrastructure.adapter.out.menu;

import org.springframework.stereotype.Component;

import demo.reactividad.application.port.in.MenuUseCases;
import demo.reactividad.domain.exception.MenuNotFoundException;
import demo.reactividad.orders.application.port.out.MenuLookupPort;
import demo.reactividad.orders.domain.model.MenuId;
import demo.reactividad.orders.domain.model.MenuSnapshot;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MenuLookupPortAdapter implements MenuLookupPort {

    private static final String RESILIENCE_INSTANCE_NAME = "orders-menu-lookup";

    private final MenuUseCases menuUseCases;

    @Override
    @Retry(name = RESILIENCE_INSTANCE_NAME)
    @TimeLimiter(name = RESILIENCE_INSTANCE_NAME)
    @Bulkhead(name = RESILIENCE_INSTANCE_NAME)
    public Mono<MenuSnapshot> findMenuSnapshot(MenuId menuId) {
        // Mono.defer es obligatorio para que @Retry funcione (ver docs/resilience.md):
        // sin él, getMenu(...) se invoca una sola vez y cada "reintento" solo
        // resuscribe al mismo resultado ya resuelto. La traducción de "no existe" vive
        // DENTRO del defer, antes de que @Retry pueda verla -- así nunca se reintenta
        // un menú que legítimamente no existe.
        return Mono.defer(() -> this.menuUseCases.getMenu(menuId.value())
                .map(menu -> new MenuSnapshot(menuId, menu.getTitle()))
                .onErrorResume(MenuNotFoundException.class, error -> Mono.empty()));
    }
}
