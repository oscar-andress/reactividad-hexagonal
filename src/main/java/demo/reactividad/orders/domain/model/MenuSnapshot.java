package demo.reactividad.orders.domain.model;

public record MenuSnapshot(MenuId menuId, String title) {

    public MenuSnapshot {
        if (menuId == null) {
            throw new IllegalArgumentException("MenuSnapshot menuId must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("MenuSnapshot title must not be blank");
        }
    }
}
