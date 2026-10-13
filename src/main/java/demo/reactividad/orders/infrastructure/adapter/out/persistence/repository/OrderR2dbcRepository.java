package demo.reactividad.orders.infrastructure.adapter.out.persistence.repository;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import demo.reactividad.orders.infrastructure.adapter.out.persistence.entity.OrderEntity;
import reactor.core.publisher.Mono;

public interface OrderR2dbcRepository extends ReactiveCrudRepository<OrderEntity, UUID> {
    Mono<OrderEntity> findByIdempotencyKey(String idempotencyKey);
}
