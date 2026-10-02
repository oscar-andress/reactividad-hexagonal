package demo.reactividad.orders.infrastructure.adapter.out.menu;

import org.springframework.stereotype.Component;

import demo.reactividad.application.port.in.MenuUseCases;
import demo.reactividad.domain.exception.MenuNotFoundException;
import demo.reactividad.orders.application.port.out.MenuLookupPort;
import demo.reactividad.orders.domain.model.MenuId;
import demo.reactividad.orders.domain.model.MenuSnapshot;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MenuLookupPortAdapter implements MenuLookupPort {

    private final MenuUseCases menuUseCases;

    @Override
    public Mono<MenuSnapshot> findMenuSnapshot(MenuId menuId) {
        return this.menuUseCases.getMenu(menuId.value())
                .map(menu -> new MenuSnapshot(menuId, menu.getTitle()))
                .onErrorResume(MenuNotFoundException.class, error -> Mono.empty());
    }
}
