package demo.reactividad.infrastructure.adapter.in.web.exception;

import static demo.reactividad.testsupport.golden.GoldenFileAssertions.assertMatchesGoldenFile;

import org.junit.jupiter.api.Test;

/**
 * Golden-file de contrato: pinea la forma JSON exacta que ya consumen clientes externos.
 * ErrorResponse se reutiliza en TODOS los endpoints de Menu y Orders cuando fallan — un
 * rename/removal de campo acá rompería el manejo de errores de toda la API. Ver Gap F,
 * ejercicio 3, en docs/quality-gates.md.
 */
class ErrorResponseTest {

    @Test
    void errorResponse_SerializesToTheExactContractedJsonShape() {
        ErrorResponse errorResponse = new ErrorResponse(
                "2026-01-15T10:30:00",
                404,
                "Menu with id 11111111-1111-1111-1111-111111111111 not found",
                "MENU_NOT_FOUND",
                "/api/v1/menu/11111111-1111-1111-1111-111111111111");

        assertMatchesGoldenFile(errorResponse, "/golden/error-response.json");
    }
}
