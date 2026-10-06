package demo.reactividad.infrastructure.adapter.out.ai;

import reactor.core.publisher.Mono;

// Representa "el cliente del LLM" — el colaborador inmediato que LlmFoodTypeClassifierAdapter
// mockea en sus tests (frontera de infraestructura real: en producción, esto haría una
// llamada de red a un proveedor externo). Este proyecto usa una implementación simulada
// (StubChatCompletionClient) a propósito — ver docs/ai-governance.md — pero el puerto en sí
// es el mismo que usaría un cliente real (ej. el SDK de Anthropic), así que reemplazarlo
// más adelante no toca ni el dominio ni la aplicación (DIP).
public interface ChatCompletionClient {
    Mono<String> complete(String prompt);
}
