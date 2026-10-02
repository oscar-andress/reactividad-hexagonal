package demo.reactividad.orders.infrastructure.adapter.in.web.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderResponseDTO(
    UUID orderId,
    UUID menuId,
    String menuTitle,
    int quantity,
    LocalDateTime createdAt
) {

}
