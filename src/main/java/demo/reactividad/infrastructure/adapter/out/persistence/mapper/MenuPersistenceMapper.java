package demo.reactividad.infrastructure.adapter.out.persistence.mapper;

import org.springframework.stereotype.Component;

import demo.reactividad.domain.model.Menu;
import demo.reactividad.infrastructure.adapter.out.persistence.entity.MenuEntity;

@Component
public class MenuPersistenceMapper {

    public Menu toDomain(MenuEntity entity) {
        Menu menu = new Menu(entity.getId(), entity.getTitle(), entity.getDescription(), entity.getCreatedAt());
        menu.setVersion(entity.getVersion());
        menu.setImageKey(entity.getImageKey());
        return menu;
    }

    public MenuEntity toEntity(Menu menu) {
        MenuEntity entity = new MenuEntity(menu.getId(), menu.getTitle(), menu.getDescription(), menu.getCreatedAt());
        entity.setVersion(menu.getVersion());
        entity.setImageKey(menu.getImageKey());
        return entity;
    }
}
