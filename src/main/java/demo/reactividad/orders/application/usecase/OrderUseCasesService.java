package demo.reactividad.orders.application.usecase;

import org.springframework.stereotype.Service;

import demo.reactividad.orders.application.port.in.OrderUseCases;
import demo.reactividad.orders.application.port.out.OrderRepositoryPort;
import demo.reactividad.orders.domain.model.Order;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class OrderUseCasesService implements OrderUseCases {

    private final OrderRepositoryPort orderRepositoryPort;

    @Override
    public Mono<Order> createOrder(Order order) {
        return this.orderRepositoryPort.save(order);
    }
}
