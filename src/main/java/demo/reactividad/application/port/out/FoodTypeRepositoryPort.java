package demo.reactividad.application.port.out;

import java.util.UUID;

import demo.reactividad.domain.model.FoodType;
import reactor.core.publisher.Flux;

public interface FoodTypeRepositoryPort {
    Flux<FoodType> findFoodTypeByMenuId(UUID menuId);
}
