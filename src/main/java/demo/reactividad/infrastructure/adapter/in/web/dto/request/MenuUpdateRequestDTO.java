package demo.reactividad.infrastructure.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MenuUpdateRequestDTO (
    @NotBlank(message = "menuTitle must not be blank")
    @Size(max = MenuUpdateRequestDTO.MAX_FIELD_LENGTH, message = "menuTitle must be at most 50 characters")
    String menuTitle,

    @NotBlank(message = "menuDescription must not be blank")
    @Size(max = MenuUpdateRequestDTO.MAX_FIELD_LENGTH, message = "menuDescription must be at most 50 characters")
    String menuDescription
) {

    static final int MAX_FIELD_LENGTH = 50;
}
