package demo.reactividad.orders.application.port.out;

import demo.reactividad.orders.domain.model.Order;
import reactor.core.publisher.Flux;

public interface OrderEventPublisher {
    void publish(Order order);
    Flux<Order> subscribe();
}
