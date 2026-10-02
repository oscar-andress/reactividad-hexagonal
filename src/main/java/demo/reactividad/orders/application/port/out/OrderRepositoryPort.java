package demo.reactividad.orders.application.port.out;

import demo.reactividad.orders.domain.model.Order;
import reactor.core.publisher.Mono;

public interface OrderRepositoryPort {
    Mono<Order> save(Order order);
}
