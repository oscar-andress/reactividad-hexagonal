package demo.reactividad.orders.application.port.out;

import demo.reactividad.orders.domain.model.MenuId;
import demo.reactividad.orders.domain.model.MenuSnapshot;
import reactor.core.publisher.Mono;

public interface MenuLookupPort {
    Mono<MenuSnapshot> findMenuSnapshot(MenuId menuId);
}
