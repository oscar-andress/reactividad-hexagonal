package demo.reactividad.infrastructure.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import demo.reactividad.application.port.out.FoodTypeRepositoryPort;
import demo.reactividad.domain.model.FoodType;
import demo.reactividad.infrastructure.adapter.out.persistence.mapper.FoodTypePersistenceMapper;
import demo.reactividad.infrastructure.adapter.out.persistence.repository.FoodTypeR2dbcRepository;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class FoodTypeRepositoryAdapter implements FoodTypeRepositoryPort {

    private final FoodTypeR2dbcRepository foodTypeR2dbcRepository;
    private final FoodTypePersistenceMapper foodTypePersistenceMapper;

    @Override
    public Flux<FoodType> findFoodTypeByMenuId(UUID menuId) {
        return this.foodTypeR2dbcRepository
                .findFoodTypeByMenuId(menuId)
                .map(this.foodTypePersistenceMapper::toDomain);
    }
}
