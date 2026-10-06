package demo.reactividad.testsupport.fixtures;

import java.util.Set;
import java.util.UUID;

import demo.reactividad.infrastructure.adapter.in.web.dto.request.MenuUpdateRequestDTO;

public final class MenuUpdateRequestDTOTestDataBuilder {

    private String menuTitle = "Updated title";
    private String menuDescription = "Updated description";
    private Set<UUID> foodTypeIds = null;

    private MenuUpdateRequestDTOTestDataBuilder() {
    }

    public static MenuUpdateRequestDTOTestDataBuilder aMenuUpdateRequestDTO() {
        return new MenuUpdateRequestDTOTestDataBuilder();
    }

    public MenuUpdateRequestDTOTestDataBuilder withTitle(String menuTitle) {
        this.menuTitle = menuTitle;
        return this;
    }

    public MenuUpdateRequestDTOTestDataBuilder withDescription(String menuDescription) {
        this.menuDescription = menuDescription;
        return this;
    }

    public MenuUpdateRequestDTOTestDataBuilder withFoodTypeIds(Set<UUID> foodTypeIds) {
        this.foodTypeIds = foodTypeIds;
        return this;
    }

    public MenuUpdateRequestDTO build() {
        return new MenuUpdateRequestDTO(this.menuTitle, this.menuDescription, this.foodTypeIds);
    }
}
