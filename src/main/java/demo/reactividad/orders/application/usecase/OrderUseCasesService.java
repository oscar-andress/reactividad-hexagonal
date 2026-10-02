package demo.reactividad.orders.application.usecase;

import org.springframework.stereotype.Service;

import demo.reactividad.orders.application.port.in.OrderUseCases;
import demo.reactividad.orders.application.port.out.MenuLookupPort;
import demo.reactividad.orders.application.port.out.OrderEventPublisher;
import demo.reactividad.orders.application.port.out.OrderRepositoryPort;
import demo.reactividad.orders.domain.exception.MenuNotFoundForOrderException;
import demo.reactividad.orders.domain.model.MenuId;
import demo.reactividad.orders.domain.model.Order;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class OrderUseCasesService implements OrderUseCases {

    private final OrderRepositoryPort orderRepositoryPort;
    private final MenuLookupPort menuLookupPort;
    private final OrderEventPublisher orderEventPublisher;

    @Override
    public Mono<Order> createOrder(MenuId menuId, int quantity) {
        return this.menuLookupPort.findMenuSnapshot(menuId)
                .switchIfEmpty(Mono.error(() -> new MenuNotFoundForOrderException(
                        "Cannot order menu " + menuId.value() + ": it does not exist")))
                .map(snapshot -> new Order(menuId, snapshot.title(), quantity))
                .flatMap(this.orderRepositoryPort::save)
                .doOnNext(this.orderEventPublisher::publish);
    }

    @Override
    public Flux<Order> streamOrders() {
        return this.orderEventPublisher.subscribe();
    }
}
