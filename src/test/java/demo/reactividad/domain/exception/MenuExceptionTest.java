package demo.reactividad.domain.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MenuExceptionTest {

    @Test
    void getErrorCode_ReturnsTheCodeGivenAtConstruction() {
        MenuException exception = new MenuNotFoundException("mensaje", "NOT_FOUND");

        assertEquals("NOT_FOUND", exception.getErrorCode());
    }
}
