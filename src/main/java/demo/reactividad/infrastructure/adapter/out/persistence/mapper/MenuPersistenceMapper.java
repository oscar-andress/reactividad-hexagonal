package demo.reactividad.infrastructure.adapter.out.persistence.mapper;

import org.springframework.stereotype.Component;

import demo.reactividad.domain.model.Menu;
import demo.reactividad.infrastructure.adapter.out.persistence.entity.MenuEntity;

@Component
public class MenuPersistenceMapper {

    public Menu toDomain(MenuEntity entity) {
        return new Menu(entity.getId(), entity.getTitle(), entity.getDescription(), entity.getCreatedAt());
    }

    public MenuEntity toEntity(Menu menu) {
        return new MenuEntity(menu.getId(), menu.getTitle(), menu.getDescription(), menu.getCreatedAt());
    }
}
