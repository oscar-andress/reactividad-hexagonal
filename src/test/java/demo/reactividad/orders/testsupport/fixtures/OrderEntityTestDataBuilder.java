package demo.reactividad.orders.testsupport.fixtures;

import java.time.LocalDateTime;
import java.util.UUID;

import demo.reactividad.orders.infrastructure.adapter.out.persistence.entity.OrderEntity;

public final class OrderEntityTestDataBuilder {

    private UUID id;
    private UUID menuId = UUID.randomUUID();
    private String menuTitleSnapshot = "DEVOS";
    private int quantity = 1;
    private LocalDateTime createdAt;

    private OrderEntityTestDataBuilder() {
    }

    public static OrderEntityTestDataBuilder anOrderEntity() {
        return new OrderEntityTestDataBuilder();
    }

    public OrderEntityTestDataBuilder withId(UUID id) {
        this.id = id;
        return this;
    }

    public OrderEntityTestDataBuilder withMenuId(UUID menuId) {
        this.menuId = menuId;
        return this;
    }

    public OrderEntityTestDataBuilder withMenuTitleSnapshot(String menuTitleSnapshot) {
        this.menuTitleSnapshot = menuTitleSnapshot;
        return this;
    }

    public OrderEntityTestDataBuilder withQuantity(int quantity) {
        this.quantity = quantity;
        return this;
    }

    public OrderEntityTestDataBuilder withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public OrderEntity build() {
        return new OrderEntity(this.id, this.menuId, this.menuTitleSnapshot, this.quantity, this.createdAt);
    }
}
