package demo.reactividad.domain.model;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FoodType {

    private final UUID id;
    private final String name;
    private final boolean active;
}
