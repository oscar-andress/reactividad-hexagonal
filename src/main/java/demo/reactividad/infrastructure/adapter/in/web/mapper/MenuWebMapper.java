package demo.reactividad.infrastructure.adapter.in.web.mapper;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import demo.reactividad.domain.model.Menu;
import demo.reactividad.infrastructure.adapter.in.web.dto.request.MenuCreateRequestDTO;
import demo.reactividad.infrastructure.adapter.in.web.dto.request.MenuUpdateRequestDTO;
import demo.reactividad.infrastructure.adapter.in.web.dto.response.FoodTypeResponseDTO;
import demo.reactividad.infrastructure.adapter.in.web.dto.response.MenuResponseDTO;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class MenuWebMapper {

    private final FoodTypeWebMapper foodTypeWebMapper;

    public MenuResponseDTO toResponseDTO(Menu menu) {
        Set<FoodTypeResponseDTO> foodTypes = this.foodTypeWebMapper.toResponseDTOs(menu.getFoodTypes());
        return new MenuResponseDTO(
            menu.getId(),
            menu.getTitle(),
            menu.getDescription(),
            menu.getCreatedAt(),
            foodTypes);
    }

    public Menu toDomain(MenuCreateRequestDTO request) {
        return new Menu(request.menuTitle(), request.menuDescription());
    }

    public Menu toDomain(UUID menuId, MenuUpdateRequestDTO request) {
        return new Menu(menuId, request.menuTitle(), request.menuDescription());
    }
}
