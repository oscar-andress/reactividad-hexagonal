package demo.reactividad.orders.infrastructure.adapter.in.web.dto.response;

import static demo.reactividad.testsupport.golden.GoldenFileAssertions.assertMatchesGoldenFile;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Golden-file de contrato: pinea la forma JSON exacta que ya consumen clientes externos.
 * Un rename/removal de campo cambia el JSON serializado y rompe este test — ver Gap F,
 * ejercicio 3, en docs/quality-gates.md.
 */
class OrderResponseDTOTest {

    @Test
    void orderResponseDTO_SerializesToTheExactContractedJsonShape() {
        OrderResponseDTO orderResponseDTO = new OrderResponseDTO(
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Menu de prueba",
                2,
                LocalDateTime.of(2026, 1, 15, 10, 30, 0));

        assertMatchesGoldenFile(orderResponseDTO, "/golden/order-response.json");
    }
}
