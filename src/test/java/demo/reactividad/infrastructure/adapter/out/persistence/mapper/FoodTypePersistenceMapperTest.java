package demo.reactividad.infrastructure.adapter.out.persistence.mapper;

import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import demo.reactividad.domain.model.FoodType;
import demo.reactividad.infrastructure.adapter.out.persistence.entity.FoodTypeEntity;

class FoodTypePersistenceMapperTest {

    private final FoodTypePersistenceMapper mapper = new FoodTypePersistenceMapper();

    @Test
    void toDomain_MapsAllFields() {
        UUID id = UUID.randomUUID();
        FoodTypeEntity entity = new FoodTypeEntity(id, "Vegano", true);

        FoodType foodType = this.mapper.toDomain(entity);

        Assertions.assertEquals(id, foodType.getId());
        Assertions.assertEquals("Vegano", foodType.getName());
        Assertions.assertTrue(foodType.isActive());
    }

    @Test
    void toEntity_MapsAllFields() {
        UUID id = UUID.randomUUID();
        FoodType foodType = new FoodType(id, "Vegano", true);

        FoodTypeEntity entity = this.mapper.toEntity(foodType);

        Assertions.assertEquals(id, entity.getFoodTypeId());
        Assertions.assertEquals("Vegano", entity.getFoodTypeName());
        Assertions.assertTrue(entity.isActive());
    }
}
