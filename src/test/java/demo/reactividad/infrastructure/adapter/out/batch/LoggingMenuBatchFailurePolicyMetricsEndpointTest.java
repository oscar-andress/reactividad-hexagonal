package demo.reactividad.infrastructure.adapter.out.batch;

import static demo.reactividad.testsupport.fixtures.MenuTestDataBuilder.aMenu;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

import demo.reactividad.domain.model.Menu;
import demo.reactividad.infrastructure.adapter.in.web.security.AuthenticationCategory;
import demo.reactividad.infrastructure.adapter.in.web.security.JwtTokenService;

// No necesita Docker/Testcontainers (solo ejercita un bean real + el endpoint de actuator),
// así que es un *Test, no un *IT, aunque el roadmap lo describa como "un *IT".
@AutoConfigureWebTestClient
@SpringBootTest
class LoggingMenuBatchFailurePolicyMetricsEndpointTest {

    private static final String AUTH_HEADER = "auth-token";

    @Autowired
    private LoggingMenuBatchFailurePolicy loggingMenuBatchFailurePolicy;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void afterABatchFailure_ActuatorMetricsReflectsTheRealCounter() {
        Menu menu = aMenu().build();

        this.loggingMenuBatchFailurePolicy.onBatchFailure(List.of(menu), new RuntimeException("db down"))
                .blockLast();

        this.webTestClient.get()
                .uri("/actuator/metrics/menu.batch_save.failures")
                .header(AUTH_HEADER, this.jwtTokenService.generate(AuthenticationCategory.PRIME))
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .jsonPath("$.name").isEqualTo("menu.batch_save.failures")
                .jsonPath("$.measurements[0].value").isEqualTo(1.0);
    }
}
