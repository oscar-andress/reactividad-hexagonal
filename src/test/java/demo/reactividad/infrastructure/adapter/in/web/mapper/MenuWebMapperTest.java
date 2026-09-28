package demo.reactividad.infrastructure.adapter.in.web.mapper;

import static demo.reactividad.testsupport.fixtures.MenuTestDataBuilder.aMenu;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import demo.reactividad.domain.model.FoodType;
import demo.reactividad.domain.model.Menu;
import demo.reactividad.infrastructure.adapter.in.web.dto.request.MenuCreateRequestDTO;
import demo.reactividad.infrastructure.adapter.in.web.dto.response.MenuResponseDTO;

class MenuWebMapperTest {

    private final MenuWebMapper mapper = new MenuWebMapper(new FoodTypeWebMapper());

    @Test
    void toResponseDTO_MapsMenuAndItsFoodTypes() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Menu menu = aMenu()
                .withId(id)
                .withCreatedAt(now)
                .withFoodTypes(Set.of(new FoodType(UUID.randomUUID(), "Vegano", true)))
                .build();

        MenuResponseDTO dto = this.mapper.toResponseDTO(menu);

        Assertions.assertEquals(id, dto.menuId());
        Assertions.assertEquals("DEVOS", dto.menuTitle());
        Assertions.assertEquals("Menu de prueba", dto.menuDescription());
        Assertions.assertEquals(now, dto.menuCreatedAt());
        Assertions.assertEquals(1, dto.foodTypes().size());
    }

    @Test
    void toDomain_MapsRequestFields() {
        MenuCreateRequestDTO request = new MenuCreateRequestDTO("DEVOS", "Menu de prueba");

        Menu menu = this.mapper.toDomain(request);

        Assertions.assertEquals("DEVOS", menu.getTitle());
        Assertions.assertEquals("Menu de prueba", menu.getDescription());
    }
}
