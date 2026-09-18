package demo.reactividad.infrastructure.adapter.in.web.dto.response;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record MenuResponseDTO(
    UUID menuId,
    String menuTitle,
    String menuDescription,
    LocalDateTime menuCreatedAt,
    Set<FoodTypeResponseDTO> foodTypes
) {

}
