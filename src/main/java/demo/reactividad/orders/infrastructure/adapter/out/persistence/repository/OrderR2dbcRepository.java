package demo.reactividad.orders.infrastructure.adapter.out.persistence.repository;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import demo.reactividad.orders.infrastructure.adapter.out.persistence.entity.OrderEntity;

public interface OrderR2dbcRepository extends ReactiveCrudRepository<OrderEntity, UUID> {
}
