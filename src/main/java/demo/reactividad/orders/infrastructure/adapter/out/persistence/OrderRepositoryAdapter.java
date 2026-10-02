package demo.reactividad.orders.infrastructure.adapter.out.persistence;

import org.springframework.stereotype.Component;

import demo.reactividad.orders.application.port.out.OrderRepositoryPort;
import demo.reactividad.orders.domain.model.Order;
import demo.reactividad.orders.infrastructure.adapter.out.persistence.mapper.OrderPersistenceMapper;
import demo.reactividad.orders.infrastructure.adapter.out.persistence.repository.OrderR2dbcRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class OrderRepositoryAdapter implements OrderRepositoryPort {

    private final OrderR2dbcRepository orderR2dbcRepository;
    private final OrderPersistenceMapper orderPersistenceMapper;

    @Override
    public Mono<Order> save(Order order) {
        return this.orderR2dbcRepository.save(this.orderPersistenceMapper.toEntity(order))
                .map(this.orderPersistenceMapper::toDomain);
    }
}
