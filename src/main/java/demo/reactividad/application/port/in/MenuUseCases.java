package demo.reactividad.application.port.in;

import java.util.UUID;

import demo.reactividad.domain.model.Menu;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MenuUseCases {
    Mono<Menu> getMenu(UUID menuId);
    Mono<Menu> createMenu(Menu menu);
    Mono<Void> deleteMenu(UUID menuId);
    Flux<Menu> streamMenus();
    Flux<Menu> createMenus(Flux<Menu> menus);
    Mono<Menu> updateMenu(Menu menu);
}
