package demo.reactividad.application.port.out;

import java.util.List;
import java.util.UUID;

import demo.reactividad.domain.model.Menu;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MenuRepositoryPort {
    Mono<Menu> findById(UUID menuId);
    Mono<Menu> save(Menu menu);
    Mono<Void> deleteById(UUID menuId);
    Flux<Menu> saveAll(List<Menu> menus);
}
