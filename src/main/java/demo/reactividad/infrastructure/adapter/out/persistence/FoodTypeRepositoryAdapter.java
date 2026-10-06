package demo.reactividad.infrastructure.adapter.out.persistence;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import demo.reactividad.application.port.out.FoodTypeRepositoryPort;
import demo.reactividad.domain.model.FoodType;
import demo.reactividad.infrastructure.adapter.out.persistence.mapper.FoodTypePersistenceMapper;
import demo.reactividad.infrastructure.adapter.out.persistence.repository.FoodTypeR2dbcRepository;
import demo.reactividad.infrastructure.adapter.out.persistence.repository.MenuFoodTypeR2dbcRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class FoodTypeRepositoryAdapter implements FoodTypeRepositoryPort {

    private final FoodTypeR2dbcRepository foodTypeR2dbcRepository;
    private final MenuFoodTypeR2dbcRepository menuFoodTypeR2dbcRepository;
    private final FoodTypePersistenceMapper foodTypePersistenceMapper;

    @Override
    public Flux<FoodType> findFoodTypeByMenuId(UUID menuId) {
        return this.foodTypeR2dbcRepository
                .findFoodTypeByMenuId(menuId)
                .map(this.foodTypePersistenceMapper::toDomain);
    }

    @Override
    public Flux<FoodType> findByIds(Set<UUID> foodTypeIds) {
        if (foodTypeIds.isEmpty()) {
            return Flux.empty();
        }
        return this.foodTypeR2dbcRepository
                .findByIds(foodTypeIds)
                .map(this.foodTypePersistenceMapper::toDomain);
    }

    @Override
    public Flux<FoodType> findAllActive() {
        return this.foodTypeR2dbcRepository
                .findAllActive()
                .map(this.foodTypePersistenceMapper::toDomain);
    }

    @Override
    public Mono<Void> replaceMenuFoodTypes(UUID menuId, Set<UUID> foodTypeIds) {
        return this.menuFoodTypeR2dbcRepository.deleteByMenuId(menuId)
                .thenMany(Flux.fromIterable(foodTypeIds))
                .concatMap(foodTypeId -> this.menuFoodTypeR2dbcRepository.insert(menuId, foodTypeId))
                .then();
    }
}
