package demo.reactividad.orders.testsupport.fixtures;

import java.util.UUID;

import demo.reactividad.orders.infrastructure.adapter.in.web.dto.request.OrderCreateRequestDTO;

public final class OrderCreateRequestDTOTestDataBuilder {

    private UUID menuId = UUID.randomUUID();
    private int quantity = 1;

    private OrderCreateRequestDTOTestDataBuilder() {
    }

    public static OrderCreateRequestDTOTestDataBuilder anOrderCreateRequestDTO() {
        return new OrderCreateRequestDTOTestDataBuilder();
    }

    public OrderCreateRequestDTOTestDataBuilder withMenuId(UUID menuId) {
        this.menuId = menuId;
        return this;
    }

    public OrderCreateRequestDTOTestDataBuilder withQuantity(int quantity) {
        this.quantity = quantity;
        return this;
    }

    public OrderCreateRequestDTO build() {
        return new OrderCreateRequestDTO(this.menuId, this.quantity);
    }
}
