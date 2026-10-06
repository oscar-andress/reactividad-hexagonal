package demo.reactividad.application.port.out;

import java.util.Set;
import java.util.UUID;

import demo.reactividad.domain.model.FoodType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface FoodTypeRepositoryPort {
    Flux<FoodType> findFoodTypeByMenuId(UUID menuId);

    Flux<FoodType> findByIds(Set<UUID> foodTypeIds);

    Flux<FoodType> findAllActive();

    Mono<Void> replaceMenuFoodTypes(UUID menuId, Set<UUID> foodTypeIds);
}
