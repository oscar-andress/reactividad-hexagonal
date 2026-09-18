package demo.reactividad.infrastructure.adapter.out.persistence.repository;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import demo.reactividad.infrastructure.adapter.out.persistence.entity.MenuEntity;

public interface MenuR2dbcRepository extends ReactiveCrudRepository<MenuEntity, UUID> {
}
