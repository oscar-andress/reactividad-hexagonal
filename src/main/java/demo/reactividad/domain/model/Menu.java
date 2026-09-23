package demo.reactividad.domain.model;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class Menu {

    private UUID id;
    private String title;
    private String description;
    private LocalDateTime createdAt;
    private Long version;
    private String imageKey;
    // Presigned display URL, computed per read (not persisted); null until a use case populates it.
    private String imageUrl;
    private Set<FoodType> foodTypes = Set.of();

    public Menu(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public Menu(UUID id, String title, String description, Long version, String imageKey, LocalDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.version = version;
        this.imageKey = imageKey;
        this.createdAt = createdAt;
    }

    public Menu(UUID id, String title, String description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }

    public Menu(UUID id, String title, String description, LocalDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.createdAt = createdAt;
    }

    public Menu withUpdatedDetails(String title, String description) {
        Menu updated = new Menu(this.id, title, description, this.version, this.imageKey, this.createdAt);
        return updated;
    }
}
