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
    private final String menuTitleSnapshot;
    private final int quantity;
    private final LocalDateTime createdAt;
    private final String idempotencyKey;

    public Order(UUID id, MenuId menuId, String menuTitleSnapshot, int quantity, LocalDateTime createdAt,
            String idempotencyKey) {
        if (menuId == null) {
            throw new InvalidOrderException("An order must reference a menu");
        }
        if (menuTitleSnapshot == null || menuTitleSnapshot.isBlank()) {
            throw new InvalidOrderException("An order must capture a menu title snapshot");
        }
        if (quantity < MINIMUM_QUANTITY) {
            throw new InvalidOrderException("Order quantity must be at least " + MINIMUM_QUANTITY);
        }
        if (idempotencyKey != null && idempotencyKey.isBlank()) {
            throw new InvalidOrderException("Idempotency key must not be blank when present");
        }
        this.id = id;
        this.menuId = menuId;
        this.menuTitleSnapshot = menuTitleSnapshot;
        this.quantity = quantity;
        this.createdAt = createdAt;
        this.idempotencyKey = idempotencyKey;
    }

    public Order(MenuId menuId, String menuTitleSnapshot, int quantity, String idempotencyKey) {
        this(null, menuId, menuTitleSnapshot, quantity, null, idempotencyKey);
    }
}
