package demo.reactividad.infrastructure.adapter.in.web.dto.response;

import java.util.UUID;

public record FoodTypeSuggestionResponseDTO(
    UUID foodTypeId,
    String foodTypeName,
    double confidence
) {

}
