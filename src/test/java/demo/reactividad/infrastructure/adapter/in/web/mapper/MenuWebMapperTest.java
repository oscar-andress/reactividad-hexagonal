package demo.reactividad.infrastructure.adapter.in.web.mapper;

import static demo.reactividad.testsupport.fixtures.MenuCreateRequestDTOTestDataBuilder.aMenuCreateRequestDTO;
import static demo.reactividad.testsupport.fixtures.MenuTestDataBuilder.aMenu;
import static demo.reactividad.testsupport.fixtures.MenuUpdateRequestDTOTestDataBuilder.aMenuUpdateRequestDTO;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import demo.reactividad.domain.model.FoodType;
import demo.reactividad.domain.model.Menu;
import demo.reactividad.infrastructure.adapter.in.web.dto.request.MenuCreateRequestDTO;
import demo.reactividad.infrastructure.adapter.in.web.dto.request.MenuUpdateRequestDTO;
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
        MenuCreateRequestDTO request = aMenuCreateRequestDTO().build();

        Menu menu = this.mapper.toDomain(request);

        Assertions.assertEquals("DEVOS", menu.getTitle());
        Assertions.assertEquals("Menu de prueba", menu.getDescription());
    }

    @Test
    void toDomain_MapsUpdateRequestFieldsWithGivenId() {
        UUID id = UUID.randomUUID();
        MenuUpdateRequestDTO request = aMenuUpdateRequestDTO().withTitle("New title").withDescription("New description").build();

        Menu menu = this.mapper.toDomain(id, request);

        Assertions.assertEquals(id, menu.getId());
        Assertions.assertEquals("New title", menu.getTitle());
        Assertions.assertEquals("New description", menu.getDescription());
    }
}
