package demo.reactividad.orders.application.port.in;

import demo.reactividad.orders.domain.model.Order;
import reactor.core.publisher.Mono;

public interface OrderUseCases {
    Mono<Order> createOrder(Order order);
}
