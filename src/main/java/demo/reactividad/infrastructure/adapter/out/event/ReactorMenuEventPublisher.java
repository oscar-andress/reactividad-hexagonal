package demo.reactividad.infrastructure.adapter.out.event;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import demo.reactividad.application.port.out.MenuEventPublisher;
import demo.reactividad.domain.model.Menu;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Slf4j
@Component
public class ReactorMenuEventPublisher implements MenuEventPublisher {

    private static final Duration CONCURRENT_EMIT_RETRY_BUDGET = Duration.ofMillis(200);

    private final Sinks.Many<Menu> sink = Sinks.many().replay().limit(1);
    private final AtomicLong failedEmissionCount = new AtomicLong();

    @Override
    public void publish(Menu menu) {
        try {
            this.sink.emitNext(menu, Sinks.EmitFailureHandler.busyLooping(CONCURRENT_EMIT_RETRY_BUDGET));
        } catch (Sinks.EmissionException exception) {
            this.failedEmissionCount.incrementAndGet();
            log.error("Failed to publish menu event for menu {}: {}", menu.getId(), exception.getReason());
        }
    }

    @Override
    public Flux<Menu> subscribe() {
        return this.sink.asFlux();
    }

    long getFailedEmissionCount() {
        return this.failedEmissionCount.get();
    }
}
