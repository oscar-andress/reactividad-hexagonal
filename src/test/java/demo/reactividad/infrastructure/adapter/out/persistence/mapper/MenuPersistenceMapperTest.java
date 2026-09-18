package demo.reactividad.infrastructure.adapter.out.persistence.mapper;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import demo.reactividad.domain.model.Menu;
import demo.reactividad.infrastructure.adapter.out.persistence.entity.MenuEntity;

class MenuPersistenceMapperTest {

    private final MenuPersistenceMapper mapper = new MenuPersistenceMapper();

    @Test
    void toDomain_MapsAllFields() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        MenuEntity entity = new MenuEntity(id, "DEVOS", "Menu de prueba", now);

        Menu menu = this.mapper.toDomain(entity);

        Assertions.assertEquals(id, menu.getId());
        Assertions.assertEquals("DEVOS", menu.getTitle());
        Assertions.assertEquals("Menu de prueba", menu.getDescription());
        Assertions.assertEquals(now, menu.getCreatedAt());
    }

    @Test
    void toEntity_MapsAllFields() {
        UUID id = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Menu menu = new Menu(id, "DEVOS", "Menu de prueba", now);

        MenuEntity entity = this.mapper.toEntity(menu);

        Assertions.assertEquals(id, entity.getId());
        Assertions.assertEquals("DEVOS", entity.getTitle());
        Assertions.assertEquals("Menu de prueba", entity.getDescription());
        Assertions.assertEquals(now, entity.getCreatedAt());
    }
}
