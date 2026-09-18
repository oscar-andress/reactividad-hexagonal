package demo.reactividad.infrastructure.adapter.out.event;

import org.springframework.stereotype.Component;

import demo.reactividad.application.port.out.MenuEventPublisher;
import demo.reactividad.domain.model.Menu;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class ReactorMenuEventPublisher implements MenuEventPublisher {

    private final Sinks.Many<Menu> sink = Sinks.many().replay().limit(1);

    @Override
    public void publish(Menu menu) {
        this.sink.tryEmitNext(menu);
    }

    @Override
    public Flux<Menu> subscribe() {
        return this.sink.asFlux();
    }
}
