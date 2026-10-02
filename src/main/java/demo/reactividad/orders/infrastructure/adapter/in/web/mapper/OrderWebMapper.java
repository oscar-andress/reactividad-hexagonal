package demo.reactividad.orders.infrastructure.adapter.in.web.mapper;

import org.springframework.stereotype.Component;

import demo.reactividad.orders.domain.model.Order;
import demo.reactividad.orders.infrastructure.adapter.in.web.dto.response.OrderResponseDTO;

@Component
public class OrderWebMapper {

    public OrderResponseDTO toResponseDTO(Order order) {
        return new OrderResponseDTO(
            order.getId(),
            order.getMenuId().value(),
            order.getMenuTitleSnapshot(),
            order.getQuantity(),
            order.getCreatedAt());
    }
}
