package demo.reactividad.orders.infrastructure.adapter.out.menu;

import org.springframework.stereotype.Component;

import demo.reactividad.application.port.in.MenuUseCases;
import demo.reactividad.domain.exception.MenuNotFoundException;
import demo.reactividad.orders.application.port.out.MenuLookupPort;
import demo.reactividad.orders.domain.model.MenuId;
import demo.reactividad.orders.domain.model.MenuSnapshot;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MenuLookupPortAdapter implements MenuLookupPort {

    private static final String RESILIENCE_INSTANCE_NAME = "orders-menu-lookup";

    private final MenuUseCases menuUseCases;

    @Override
    @Bulkhead(name = RESILIENCE_INSTANCE_NAME)
    public Mono<MenuSnapshot> findMenuSnapshot(MenuId menuId) {
        return this.menuUseCases.getMenu(menuId.value())
                .map(menu -> new MenuSnapshot(menuId, menu.getTitle()))
                .onErrorResume(MenuNotFoundException.class, error -> Mono.empty());
    }
}
