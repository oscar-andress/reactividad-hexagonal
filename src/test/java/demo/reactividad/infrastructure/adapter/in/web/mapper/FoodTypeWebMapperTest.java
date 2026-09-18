package demo.reactividad.infrastructure.adapter.in.web.mapper;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import demo.reactividad.domain.model.FoodType;
import demo.reactividad.infrastructure.adapter.in.web.dto.response.FoodTypeResponseDTO;

class FoodTypeWebMapperTest {

    private final FoodTypeWebMapper mapper = new FoodTypeWebMapper();

    @Test
    void toResponseDTO_MapsAllFields() {
        UUID id = UUID.randomUUID();
        FoodType foodType = new FoodType(id, "Vegano", true);

        FoodTypeResponseDTO dto = this.mapper.toResponseDTO(foodType);

        Assertions.assertEquals(id, dto.foodTypeId());
        Assertions.assertEquals("Vegano", dto.foodTypeName());
        Assertions.assertTrue(dto.active());
    }

    @Test
    void toResponseDTOs_MapsEachElement() {
        FoodType foodType = new FoodType(UUID.randomUUID(), "Vegano", true);

        Set<FoodTypeResponseDTO> dtos = this.mapper.toResponseDTOs(Set.of(foodType));

        Assertions.assertEquals(1, dtos.size());
        Assertions.assertEquals("Vegano", dtos.iterator().next().foodTypeName());
    }
}
