package demo.reactividad.testsupport.golden;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Compara la forma JSON serializada de un objeto contra un golden file real en
 * src/test/resources/golden/ — un rename/removal de campo cambia el JSON y rompe
 * la comparación. Usa el mismo motor (Jackson 3, tools.jackson.databind) que la
 * aplicación real al serializar respuestas HTTP. Ver docs/quality-gates.md.
 */
public final class GoldenFileAssertions {

    private static final ObjectMapper OBJECT_MAPPER = new JsonMapper();

    private GoldenFileAssertions() {
    }

    public static void assertMatchesGoldenFile(Object actualValue, String classpathResource) {
        JsonNode actual = OBJECT_MAPPER.valueToTree(actualValue);
        JsonNode expected = readGoldenFile(classpathResource);
        assertEquals(expected, actual,
                () -> "La forma JSON de " + actualValue.getClass().getSimpleName()
                        + " ya no coincide con el contrato fijado en " + classpathResource);
    }

    private static JsonNode readGoldenFile(String classpathResource) {
        try (InputStream inputStream = GoldenFileAssertions.class.getResourceAsStream(classpathResource)) {
            if (inputStream == null) {
                throw new IllegalStateException("Golden file no encontrado en el classpath: " + classpathResource);
            }
            return OBJECT_MAPPER.readTree(inputStream);
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo leer el golden file " + classpathResource, exception);
        }
    }
}
