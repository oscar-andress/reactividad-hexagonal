package demo.reactividad.orders.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

import demo.reactividad.orders.domain.exception.InvalidOrderException;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class Order {

    private static final int MINIMUM_QUANTITY = 1;

    private final UUID id;
    private final MenuId menuId;
    private final int quantity;
    private final LocalDateTime createdAt;

    public Order(UUID id, MenuId menuId, int quantity, LocalDateTime createdAt) {
        if (menuId == null) {
            throw new InvalidOrderException("An order must reference a menu");
        }
        if (quantity < MINIMUM_QUANTITY) {
            throw new InvalidOrderException("Order quantity must be at least " + MINIMUM_QUANTITY);
        }
        this.id = id;
        this.menuId = menuId;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    public Order(MenuId menuId, int quantity) {
        this(null, menuId, quantity, null);
    }
}
