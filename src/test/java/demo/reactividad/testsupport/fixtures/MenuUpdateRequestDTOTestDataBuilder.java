package demo.reactividad.testsupport.fixtures;

import demo.reactividad.infrastructure.adapter.in.web.dto.request.MenuUpdateRequestDTO;

public final class MenuUpdateRequestDTOTestDataBuilder {

    private String menuTitle = "Updated title";
    private String menuDescription = "Updated description";

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

    public MenuUpdateRequestDTO build() {
        return new MenuUpdateRequestDTO(this.menuTitle, this.menuDescription);
    }
}
