package demo.reactividad.infrastructure.adapter.out.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import demo.reactividad.application.port.out.FoodTypeClassifierPort;
import demo.reactividad.domain.model.FoodType;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

// Necesita un contexto real de Spring: @TimeLimiter/@RateLimiter solo actúan a través del
// proxy AOP (ver docs/resilience.md). Separado de LlmFoodTypeClassifierAdapterTest para no
// pagar el costo del contexto en los tests rápidos de gobernanza de entrada/salida.
@SpringBootTest
class LlmFoodTypeClassifierAdapterResilienceTest {

    private static final FoodType VEGANO = new FoodType(UUID.randomUUID(), "Vegano", true);
    private static final Set<FoodType> CANDIDATES = Set.of(VEGANO);

    @MockitoBean
    private ChatCompletionClient chatCompletionClient;

    @Autowired
    private FoodTypeClassifierPort foodTypeClassifierPort;

    @Test
    void suggestFoodType_WhenLlmNeverResponds_FailsWithTimeoutInsteadOfHangingForever() {
        when(this.chatCompletionClient.complete(any())).thenReturn(Mono.never());

        this.foodTypeClassifierPort.suggestFoodType("Título", "Descripción", CANDIDATES)
                .as(StepVerifier::create)
                .expectError(TimeoutException.class)
                // resilience4j.timelimiter.instances.ai-food-type-classifier.timeout-duration=3s
                .verify(Duration.ofSeconds(5));
    }

    // Ráfaga disparada CONCURRENTEMENTE, no en secuencia — un test secuencial es
    // inherentemente flaky bajo carga variable (ver docs/security/rate-limiting.md).
    @Test
    void suggestFoodType_WhenBurstExceedsLimit_RejectsExcessWithRequestNotPermitted() throws Exception {
        when(this.chatCompletionClient.complete(any())).thenReturn(Mono.just("Vegano"));
        int burstSize = 6; // resilience4j.ratelimiter...ai-food-type-classifier.limit-for-period=5
        ExecutorService executor = Executors.newFixedThreadPool(burstSize);
        try {
            List<CompletableFuture<Boolean>> futures = IntStream.range(0, burstSize)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                        try {
                            this.foodTypeClassifierPort.suggestFoodType("Título", "Descripción", CANDIDATES).block();
                            return false;
                        } catch (RequestNotPermitted ex) {
                            return true;
                        }
                    }, executor))
                    .toList();

            List<Boolean> rejected = futures.stream().map(CompletableFuture::join).toList();

            assertTrue(rejected.contains(true),
                    "Esperábamos que al menos una de las " + burstSize + " llamadas fuera rechazada");
        } finally {
            executor.shutdown();
        }
    }
}
