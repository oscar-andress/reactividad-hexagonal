package demo.reactividad.infrastructure.adapter.out.persistence.mapper;

import org.springframework.stereotype.Component;

import demo.reactividad.domain.model.FoodType;
import demo.reactividad.infrastructure.adapter.out.persistence.entity.FoodTypeEntity;

@Component
public class FoodTypePersistenceMapper {

    public FoodType toDomain(FoodTypeEntity entity) {
        return new FoodType(entity.getFoodTypeId(), entity.getFoodTypeName(), entity.isActive());
    }

    public FoodTypeEntity toEntity(FoodType foodType) {
        return new FoodTypeEntity(foodType.getId(), foodType.getName(), foodType.isActive());
    }
}
