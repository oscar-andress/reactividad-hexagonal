package demo.reactividad.infrastructure.adapter.out.batch;

import static demo.reactividad.testsupport.fixtures.MenuTestDataBuilder.aMenu;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

import demo.reactividad.domain.model.Menu;

// No necesita Docker/Testcontainers (solo ejercita un bean real + el endpoint de actuator),
// así que es un *Test, no un *IT, aunque el roadmap lo describa como "un *IT".
@AutoConfigureWebTestClient
@SpringBootTest
class LoggingMenuBatchFailurePolicyMetricsEndpointTest {

    private static final String AUTH_HEADER = "auth-token";
    private static final String PRIME_TOKEN = "secret456";

    @Autowired
    private LoggingMenuBatchFailurePolicy loggingMenuBatchFailurePolicy;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void afterABatchFailure_ActuatorMetricsReflectsTheRealCounter() {
        Menu menu = aMenu().build();

        this.loggingMenuBatchFailurePolicy.onBatchFailure(List.of(menu), new RuntimeException("db down"))
                .blockLast();

        this.webTestClient.get()
                .uri("/actuator/metrics/menu.batch_save.failures")
                .header(AUTH_HEADER, PRIME_TOKEN)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath("$.name").isEqualTo("menu.batch_save.failures")
                .jsonPath("$.measurements[0].value").isEqualTo(1.0);
    }
}
