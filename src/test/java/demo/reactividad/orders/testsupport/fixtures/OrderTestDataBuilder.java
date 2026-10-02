package demo.reactividad.orders.testsupport.fixtures;

import java.time.LocalDateTime;
import java.util.UUID;

import demo.reactividad.orders.domain.model.MenuId;
import demo.reactividad.orders.domain.model.Order;

public final class OrderTestDataBuilder {

    private UUID id;
    private MenuId menuId = new MenuId(UUID.randomUUID());
    private int quantity = 1;
    private LocalDateTime createdAt;

    private OrderTestDataBuilder() {
    }

    public static OrderTestDataBuilder anOrder() {
        return new OrderTestDataBuilder();
    }

    public OrderTestDataBuilder withId(UUID id) {
        this.id = id;
        return this;
    }

    public OrderTestDataBuilder withMenuId(MenuId menuId) {
        this.menuId = menuId;
        return this;
    }

    public OrderTestDataBuilder withQuantity(int quantity) {
        this.quantity = quantity;
        return this;
    }

    public OrderTestDataBuilder withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public Order build() {
        return new Order(this.id, this.menuId, this.quantity, this.createdAt);
    }
}
