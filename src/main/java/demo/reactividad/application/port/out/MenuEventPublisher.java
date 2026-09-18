package demo.reactividad.application.port.out;

import demo.reactividad.domain.model.Menu;
import reactor.core.publisher.Flux;

public interface MenuEventPublisher {
    void publish(Menu menu);
    Flux<Menu> subscribe();
}
