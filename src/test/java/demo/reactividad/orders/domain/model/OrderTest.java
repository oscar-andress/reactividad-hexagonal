package demo.reactividad.orders.domain.model;

import static demo.reactividad.orders.testsupport.fixtures.OrderTestDataBuilder.anOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import demo.reactividad.orders.domain.exception.InvalidOrderException;

class OrderTest {

    @Test
    void constructor_WhenQuantityIsZeroOrNegative_ThrowsInvalidOrderException() {
        assertThrows(InvalidOrderException.class, () -> anOrder().withQuantity(0).build());
        assertThrows(InvalidOrderException.class, () -> anOrder().withQuantity(-1).build());
    }

    @Test
    void constructor_WhenMenuIdIsNull_ThrowsInvalidOrderException() {
        assertThrows(InvalidOrderException.class, () -> anOrder().withMenuId(null).build());
    }

    @Test
    void constructor_WithValidData_CreatesOrder() {
        MenuId menuId = new MenuId(UUID.randomUUID());

        Order order = anOrder().withMenuId(menuId).withQuantity(3).build();

        assertEquals(menuId, order.getMenuId());
        assertEquals(3, order.getQuantity());
    }
}
