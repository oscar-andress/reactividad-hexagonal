package demo.reactividad.infrastructure.adapter.in.web.dto.response;

import java.util.UUID;

public record FoodTypeResponseDTO(
    UUID foodTypeId,
    String foodTypeName,
    boolean active
) {

}
