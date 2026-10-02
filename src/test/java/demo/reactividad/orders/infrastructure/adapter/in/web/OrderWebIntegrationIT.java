package demo.reactividad.orders.infrastructure.adapter.in.web;

import static demo.reactividad.testsupport.fixtures.MenuEntityTestDataBuilder.aMenuEntity;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import demo.reactividad.infrastructure.adapter.out.persistence.entity.MenuEntity;
import demo.reactividad.infrastructure.adapter.out.persistence.repository.MenuR2dbcRepository;
import demo.reactividad.orders.infrastructure.adapter.in.web.dto.request.OrderCreateRequestDTO;
import demo.reactividad.orders.infrastructure.adapter.in.web.dto.response.OrderResponseDTO;
import demo.reactividad.testsupport.containers.AbstractPostgresContainerTest;
import reactor.test.StepVerifier;

@AutoConfigureWebTestClient
@SpringBootTest
class OrderWebIntegrationIT extends AbstractPostgresContainerTest {

    private static final String ORDER_PATH = "/api/v1/order";
    private static final String AUTH_HEADER = "auth-token";
    private static final String STANDARD_TOKEN = "secret123";
    private static final String PRIME_TOKEN = "secret456";
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(5);
    private static final Logger log = LoggerFactory.getLogger(OrderWebIntegrationIT.class);

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private MenuR2dbcRepository menuR2dbcRepository;

    private MenuEntity existingMenu;

    @BeforeEach
    void setUp() {
        this.existingMenu = this.menuR2dbcRepository.deleteAll()
                .then(this.menuR2dbcRepository.save(aMenuEntity().build()))
                .block();
    }

    @Test
    void postOrder_Success() {
        OrderCreateRequestDTO order = new OrderCreateRequestDTO(this.existingMenu.getId(), 2);

        this.webTestClient.post()
                .uri(ORDER_PATH + "/")
                .header(AUTH_HEADER, PRIME_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(order)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .consumeWith(r -> log.info("{}", new String(r.getResponseBody(), StandardCharsets.UTF_8)))
                .jsonPath("$.menuTitle").isEqualTo(this.existingMenu.getTitle())
                .jsonPath("$.quantity").isEqualTo(2);
    }

    @Test
    void postOrder_Unauthorized() {
        OrderCreateRequestDTO order = new OrderCreateRequestDTO(this.existingMenu.getId(), 1);

        this.webTestClient.post()
                .uri(ORDER_PATH + "/")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(order)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void postOrder_Forbidden() {
        OrderCreateRequestDTO order = new OrderCreateRequestDTO(this.existingMenu.getId(), 1);

        this.webTestClient.post()
                .uri(ORDER_PATH + "/")
                .header(AUTH_HEADER, STANDARD_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(order)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void postOrder_WhenMenuDoesNotExist_ReturnsNotFound() {
        UUID unknownMenuId = UUID.randomUUID();
        OrderCreateRequestDTO order = new OrderCreateRequestDTO(unknownMenuId, 1);

        this.webTestClient.post()
                .uri(ORDER_PATH + "/")
                .header(AUTH_HEADER, PRIME_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(order)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("MENU_NOT_FOUND");
    }

    @Test
    void getOrderStream_Success() {
        OrderCreateRequestDTO order = new OrderCreateRequestDTO(this.existingMenu.getId(), 5);

        this.webTestClient.post()
                .uri(ORDER_PATH + "/")
                .header(AUTH_HEADER, PRIME_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(order)
                .exchange()
                .expectStatus().is2xxSuccessful();

        this.webTestClient
                .get()
                .uri(ORDER_PATH + "/stream")
                .header(AUTH_HEADER, STANDARD_TOKEN)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .returnResult(OrderResponseDTO.class)
                .getResponseBody()
                .as(StepVerifier::create)
                .expectNextMatches(received -> received.quantity() == 5)
                .thenCancel()
                .verify(STREAM_TIMEOUT);
    }
}
