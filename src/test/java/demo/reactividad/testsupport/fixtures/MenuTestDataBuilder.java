package demo.reactividad.testsupport.fixtures;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import demo.reactividad.domain.model.FoodType;
import demo.reactividad.domain.model.Menu;

public final class MenuTestDataBuilder {

    private UUID id;
    private String title = "DEVOS";
    private String description = "Menu de prueba";
    private LocalDateTime createdAt;
    private Long version;
    private String imageKey;
    private Set<FoodType> foodTypes = Set.of();

    private MenuTestDataBuilder() {
    }

    public static MenuTestDataBuilder aMenu() {
        return new MenuTestDataBuilder();
    }

    public MenuTestDataBuilder withId(UUID id) {
        this.id = id;
        return this;
    }

    public MenuTestDataBuilder withTitle(String title) {
        this.title = title;
        return this;
    }

    public MenuTestDataBuilder withDescription(String description) {
        this.description = description;
        return this;
    }

    public MenuTestDataBuilder withCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
        return this;
    }

    public MenuTestDataBuilder withVersion(Long version) {
        this.version = version;
        return this;
    }

    public MenuTestDataBuilder withImageKey(String imageKey) {
        this.imageKey = imageKey;
        return this;
    }

    public MenuTestDataBuilder withFoodTypes(Set<FoodType> foodTypes) {
        this.foodTypes = foodTypes;
        return this;
    }

    public Menu build() {
        return new Menu(this.id, this.title, this.description, this.createdAt,
                this.version, this.imageKey, null, this.foodTypes);
    }
}
