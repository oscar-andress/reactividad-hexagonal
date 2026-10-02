package demo.reactividad.orders.infrastructure.adapter.in.web.dto.request;

import java.util.UUID;

public record OrderCreateRequestDTO(
    UUID menuId,
    int quantity
) {

}
