package demo.reactividad.infrastructure.adapter.out.persistence.mapper;

import java.util.Set;

import org.springframework.stereotype.Component;

import demo.reactividad.domain.model.Menu;
import demo.reactividad.infrastructure.adapter.out.persistence.entity.MenuEntity;

@Component
public class MenuPersistenceMapper {

    public Menu toDomain(MenuEntity entity) {
        return new Menu(entity.getId(), entity.getTitle(), entity.getDescription(), entity.getCreatedAt(),
                entity.getVersion(), entity.getImageKey(), null, Set.of());
    }

    public MenuEntity toEntity(Menu menu) {
        MenuEntity entity = new MenuEntity(menu.getId(), menu.getTitle(), menu.getDescription(), menu.getCreatedAt());
        entity.setVersion(menu.getVersion());
        entity.setImageKey(menu.getImageKey());
        return entity;
    }
}
