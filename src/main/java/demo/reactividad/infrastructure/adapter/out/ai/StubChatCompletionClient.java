package demo.reactividad.infrastructure.adapter.out.ai;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

// Implementación simulada de "el LLM" — elegida a propósito para este ejercicio en vez de
// un proveedor real (ver docs/ai-governance.md): practica exactamente los mismos patrones
// de gobernanza (prompt injection, validación de salida, timeout, rate limit) sin
// credenciales ni costo real. Reemplazarla por un cliente real más adelante no toca ni
// LlmFoodTypeClassifierAdapter ni nada por encima — cumple el mismo contrato (DIP).
//
// Heurística deliberadamente simple: busca, dentro de las líneas de Título/Descripción
// del prompt (nunca dentro de la lista de categorías permitidas, para no "encontrarse a
// sí misma"), si aparece el nombre de alguna categoría permitida.
@Component
public class StubChatCompletionClient implements ChatCompletionClient {

    private static final Pattern TITLE_LINE = Pattern.compile("(?i)^Título: (.*)$", Pattern.MULTILINE);
    private static final Pattern DESCRIPTION_LINE = Pattern.compile("(?i)^Descripción: (.*)$", Pattern.MULTILINE);
    private static final Pattern CATEGORIES_LINE = Pattern.compile("(?i)^Categorías permitidas: (.*)$", Pattern.MULTILINE);
    private static final String NO_MATCH_RESPONSE = "NONE";

    @Override
    public Mono<String> complete(String prompt) {
        return Mono.fromCallable(() -> pickBestCandidate(prompt));
    }

    private String pickBestCandidate(String prompt) {
        String menuText = (firstGroup(TITLE_LINE, prompt) + " " + firstGroup(DESCRIPTION_LINE, prompt))
                .toLowerCase(Locale.ROOT);
        String categoriesLine = firstGroup(CATEGORIES_LINE, prompt);

        return List.of(categoriesLine.split(","))
                .stream()
                .map(String::trim)
                .filter(candidate -> !candidate.isEmpty() && menuText.contains(candidate.toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse(NO_MATCH_RESPONSE);
    }

    private String firstGroup(Pattern pattern, String input) {
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher.group(1) : "";
    }
}
