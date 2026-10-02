package demo.reactividad.orders.infrastructure.adapter.in.web.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import demo.reactividad.orders.domain.model.MenuId;
import demo.reactividad.orders.domain.model.Order;
import demo.reactividad.orders.infrastructure.adapter.in.web.dto.response.OrderResponseDTO;

class OrderWebMapperTest {

    private final OrderWebMapper mapper = new OrderWebMapper();

    @Test
    void toResponseDTO_MapsAllFields() {
        UUID id = UUID.randomUUID();
        MenuId menuId = new MenuId(UUID.randomUUID());
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        Order order = new Order(id, menuId, "Menu del dia", 3, createdAt);

        OrderResponseDTO dto = this.mapper.toResponseDTO(order);

        assertEquals(id, dto.orderId());
        assertEquals(menuId.value(), dto.menuId());
        assertEquals("Menu del dia", dto.menuTitle());
        assertEquals(3, dto.quantity());
        assertEquals(createdAt, dto.createdAt());
    }
}
