package demo.reactividad.infrastructure.adapter.in.web.dto.response;

import static demo.reactividad.testsupport.golden.GoldenFileAssertions.assertMatchesGoldenFile;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Golden-file de contrato: pinea la forma JSON exacta que ya consumen clientes externos.
 * Un rename/removal de campo cambia el JSON serializado y rompe este test — ver Gap F,
 * ejercicio 3, en docs/quality-gates.md.
 */
class MenuResponseDTOTest {

    @Test
    void menuResponseDTO_SerializesToTheExactContractedJsonShape() {
        MenuResponseDTO menuResponseDTO = new MenuResponseDTO(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                "Menu de prueba",
                "Descripción de prueba",
                LocalDateTime.of(2026, 1, 15, 10, 30, 0),
                "https://bucket.s3.amazonaws.com/menu.png",
                Set.of(new FoodTypeResponseDTO(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"), "Vegano", true)));

        assertMatchesGoldenFile(menuResponseDTO, "/golden/menu-response.json");
    }
}
