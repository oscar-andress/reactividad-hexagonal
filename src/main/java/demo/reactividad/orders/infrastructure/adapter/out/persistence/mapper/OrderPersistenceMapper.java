package demo.reactividad.orders.infrastructure.adapter.out.persistence.mapper;

import org.springframework.stereotype.Component;

import demo.reactividad.orders.domain.model.MenuId;
import demo.reactividad.orders.domain.model.Order;
import demo.reactividad.orders.infrastructure.adapter.out.persistence.entity.OrderEntity;

@Component
public class OrderPersistenceMapper {

    public Order toDomain(OrderEntity entity) {
        return new Order(entity.getId(), new MenuId(entity.getMenuId()), entity.getQuantity(), entity.getCreatedAt());
    }

    public OrderEntity toEntity(Order order) {
        return new OrderEntity(order.getId(), order.getMenuId().value(), order.getQuantity(), order.getCreatedAt());
    }
}
