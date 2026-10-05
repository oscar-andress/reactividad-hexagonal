package demo.reactividad.testsupport.fixtures;

import demo.reactividad.infrastructure.adapter.in.web.dto.request.MenuCreateRequestDTO;

public final class MenuCreateRequestDTOTestDataBuilder {

    private String menuTitle = "DEVOS";
    private String menuDescription = "Menu de prueba";

    private MenuCreateRequestDTOTestDataBuilder() {
    }

    public static MenuCreateRequestDTOTestDataBuilder aMenuCreateRequestDTO() {
        return new MenuCreateRequestDTOTestDataBuilder();
    }

    public MenuCreateRequestDTOTestDataBuilder withTitle(String menuTitle) {
        this.menuTitle = menuTitle;
        return this;
    }

    public MenuCreateRequestDTOTestDataBuilder withDescription(String menuDescription) {
        this.menuDescription = menuDescription;
        return this;
    }

    public MenuCreateRequestDTO build() {
        return new MenuCreateRequestDTO(this.menuTitle, this.menuDescription);
    }
}
