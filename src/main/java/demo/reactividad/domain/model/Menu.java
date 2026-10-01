package demo.reactividad.domain.model;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class Menu {

    private final UUID id;
    private final String title;
    private final String description;
    private final LocalDateTime createdAt;
    private final Long version;
    private final String imageKey;
    // Presigned display URL, computed per read (not persisted); null until a use case populates it.
    private final String imageUrl;
    private final Set<FoodType> foodTypes;

    public Menu(UUID id, String title, String description, LocalDateTime createdAt,
            Long version, String imageKey, String imageUrl, Set<FoodType> foodTypes) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.createdAt = createdAt;
        this.version = version;
        this.imageKey = imageKey;
        this.imageUrl = imageUrl;
        this.foodTypes = foodTypes != null ? foodTypes : Set.of();
    }

    public Menu(String title, String description) {
        this(null, title, description, null, null, null, null, Set.of());
    }

    public Menu(UUID id, String title, String description) {
        this(id, title, description, null, null, null, null, Set.of());
    }

    public Menu(UUID id, String title, String description, LocalDateTime createdAt) {
        this(id, title, description, createdAt, null, null, null, Set.of());
    }

    public Menu withUpdatedDetails(String title, String description) {
        return new Menu(this.id, title, description, this.createdAt,
                this.version, this.imageKey, this.imageUrl, this.foodTypes);
    }

    public Menu withFoodTypes(Set<FoodType> foodTypes) {
        return new Menu(this.id, this.title, this.description, this.createdAt,
                this.version, this.imageKey, this.imageUrl, foodTypes);
    }

    public Menu withImageKey(String imageKey) {
        return new Menu(this.id, this.title, this.description, this.createdAt,
                this.version, imageKey, this.imageUrl, this.foodTypes);
    }

    public Menu withImageUrl(String imageUrl) {
        return new Menu(this.id, this.title, this.description, this.createdAt,
                this.version, this.imageKey, imageUrl, this.foodTypes);
    }
}
