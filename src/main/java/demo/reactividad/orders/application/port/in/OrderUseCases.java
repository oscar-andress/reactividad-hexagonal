package demo.reactividad.orders.application.port.in;

import demo.reactividad.orders.domain.model.MenuId;
import demo.reactividad.orders.domain.model.Order;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OrderUseCases {
    Mono<Order> createOrder(MenuId menuId, int quantity);
    Flux<Order> streamOrders();
}
