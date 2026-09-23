package demo.reactividad.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class MenuTest {

    @Test
    void withUpdatedDetails_PreservesIdentityAndAuditFields() {
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 10, 0);
        Menu original = new Menu(id, "Original title", "Original description", createdAt);
        original.setVersion(3L);
        original.setImageKey("some-image-key");

        Menu updated = original.withUpdatedDetails("New title", "New description");

        assertEquals(id, updated.getId());
        assertEquals("New title", updated.getTitle());
        assertEquals("New description", updated.getDescription());
        assertEquals(3L, updated.getVersion());
        assertEquals("some-image-key", updated.getImageKey());
        assertEquals(createdAt, updated.getCreatedAt());
    }
}
