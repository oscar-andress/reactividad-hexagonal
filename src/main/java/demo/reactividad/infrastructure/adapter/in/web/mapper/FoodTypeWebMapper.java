package demo.reactividad.infrastructure.adapter.in.web.mapper;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import demo.reactividad.domain.model.FoodType;
import demo.reactividad.infrastructure.adapter.in.web.dto.response.FoodTypeResponseDTO;

@Component
public class FoodTypeWebMapper {

    public FoodTypeResponseDTO toResponseDTO(FoodType foodType) {
        return new FoodTypeResponseDTO(
            foodType.getId(),
            foodType.getName(),
            foodType.isActive());
    }

    public Set<FoodTypeResponseDTO> toResponseDTOs(Set<FoodType> foodTypes) {
        return foodTypes.stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toSet());
    }
}
