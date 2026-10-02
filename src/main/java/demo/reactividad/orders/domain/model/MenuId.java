package demo.reactividad.orders.domain.model;

import java.util.UUID;

public record MenuId(UUID value) {

    public MenuId {
        if (value == null) {
            throw new IllegalArgumentException("MenuId value must not be null");
        }
    }
}
