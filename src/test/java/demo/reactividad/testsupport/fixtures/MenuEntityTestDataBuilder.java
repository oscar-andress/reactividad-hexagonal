package demo.reactividad.testsupport.fixtures;

import java.time.LocalDateTime;
import java.util.UUID;

import demo.reactividad.infrastructure.adapter.out.persistence.entity.MenuEntity;

public final class MenuEntityTestDataBuilder {

    private UUID id;
    private String title = "DEVOS";
    private String description = "Menu de prueba";
    private LocalDateTime createdAt;

    private MenuEntityTestDataBuilder() {
    }

    public static MenuEntityTestDataBuilder aMenuEntity() {
        return new MenuEntityTestDataBuilder();
    }

    public MenuEntityTestDataBuilder withId(UUID id) {
        this.id = id;
        return this;
    }

    public MenuEntityTestDataBuilder withTitle(String title) {
        this.title = title;
        return this;
    }

    public MenuEntityTestDataBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public MenuEntityTestDataBuilder withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public MenuEntity build() {
        return new MenuEntity(this.id, this.title, this.description, this.createdAt);
    }
}
