package demo.reactividad.orders.infrastructure.adapter.out.persistence.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import demo.reactividad.orders.domain.model.MenuId;
import demo.reactividad.orders.domain.model.Order;
import demo.reactividad.orders.infrastructure.adapter.out.persistence.entity.OrderEntity;

class OrderPersistenceMapperTest {

    private final OrderPersistenceMapper mapper = new OrderPersistenceMapper();

    @Test
    void toDomain_MapsAllFields() {
        UUID id = UUID.randomUUID();
        UUID menuId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        OrderEntity entity = new OrderEntity(id, menuId, 2, createdAt);

        Order order = this.mapper.toDomain(entity);

        assertEquals(id, order.getId());
        assertEquals(menuId, order.getMenuId().value());
        assertEquals(2, order.getQuantity());
        assertEquals(createdAt, order.getCreatedAt());
    }

    @Test
    void toEntity_MapsAllFields() {
        UUID id = UUID.randomUUID();
        MenuId menuId = new MenuId(UUID.randomUUID());
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        Order order = new Order(id, menuId, 2, createdAt);

        OrderEntity entity = this.mapper.toEntity(order);

        assertEquals(id, entity.getId());
        assertEquals(menuId.value(), entity.getMenuId());
        assertEquals(2, entity.getQuantity());
        assertEquals(createdAt, entity.getCreatedAt());
    }
}
