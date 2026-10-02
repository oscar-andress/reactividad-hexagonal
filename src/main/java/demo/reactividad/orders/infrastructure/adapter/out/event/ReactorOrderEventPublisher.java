package demo.reactividad.orders.infrastructure.adapter.out.event;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import demo.reactividad.orders.application.port.out.OrderEventPublisher;
import demo.reactividad.orders.domain.model.Order;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Slf4j
@Component
public class ReactorOrderEventPublisher implements OrderEventPublisher {

    private static final Duration CONCURRENT_EMIT_RETRY_BUDGET = Duration.ofMillis(200);

    private final Sinks.Many<Order> sink = Sinks.many().replay().limit(1);
    private final AtomicLong failedEmissionCount = new AtomicLong();

    @Override
    public void publish(Order order) {
        try {
            this.sink.emitNext(order, Sinks.EmitFailureHandler.busyLooping(CONCURRENT_EMIT_RETRY_BUDGET));
        } catch (Sinks.EmissionException exception) {
            this.failedEmissionCount.incrementAndGet();
            log.error("Failed to publish order event for order {}: {}", order.getId(), exception.getReason());
        }
    }

    @Override
    public Flux<Order> subscribe() {
        return this.sink.asFlux();
    }

    long getFailedEmissionCount() {
        return this.failedEmissionCount.get();
    }
}
