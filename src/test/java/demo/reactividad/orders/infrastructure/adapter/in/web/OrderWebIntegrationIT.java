package demo.reactividad.orders.infrastructure.adapter.in.web;

import static demo.reactividad.orders.testsupport.fixtures.OrderCreateRequestDTOTestDataBuilder.anOrderCreateRequestDTO;
import static demo.reactividad.testsupport.fixtures.MenuEntityTestDataBuilder.aMenuEntity;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import demo.reactividad.infrastructure.adapter.in.web.security.AuthenticationCategory;
import demo.reactividad.infrastructure.adapter.in.web.security.JwtTokenService;
import demo.reactividad.infrastructure.adapter.out.persistence.entity.MenuEntity;
import demo.reactividad.infrastructure.adapter.out.persistence.repository.MenuR2dbcRepository;
import demo.reactividad.orders.infrastructure.adapter.in.web.dto.request.OrderCreateRequestDTO;
import demo.reactividad.orders.infrastructure.adapter.in.web.dto.response.OrderResponseDTO;
import demo.reactividad.orders.infrastructure.adapter.out.persistence.repository.OrderR2dbcRepository;
import demo.reactividad.testsupport.containers.AbstractPostgresContainerTest;
import reactor.test.StepVerifier;

@AutoConfigureWebTestClient
@SpringBootTest
class OrderWebIntegrationIT extends AbstractPostgresContainerTest {

    private static final String ORDER_PATH = "/api/v1/order";
    private static final String AUTH_HEADER = "auth-token";
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(5);
    private static final Logger log = LoggerFactory.getLogger(OrderWebIntegrationIT.class);

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private MenuR2dbcRepository menuR2dbcRepository;

    @Autowired
    private OrderR2dbcRepository orderR2dbcRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private MenuEntity existingMenu;
    private String standardToken;
    private String primeToken;

    @BeforeEach
    void setUp() {
        this.standardToken = this.jwtTokenService.generate(AuthenticationCategory.STANDARD);
        this.primeToken = this.jwtTokenService.generate(AuthenticationCategory.PRIME);
        this.existingMenu = this.menuR2dbcRepository.deleteAll()
                .then(this.menuR2dbcRepository.save(aMenuEntity().build()))
                .block();
        this.orderR2dbcRepository.deleteAll().block();
    }

    @Test
    void postOrder_Success() {
        OrderCreateRequestDTO order = anOrderCreateRequestDTO().withMenuId(this.existingMenu.getId()).withQuantity(2).build();

        this.webTestClient.post()
                .uri(ORDER_PATH + "/")
                .header(AUTH_HEADER, this.primeToken)
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
        OrderCreateRequestDTO order = anOrderCreateRequestDTO().withMenuId(this.existingMenu.getId()).withQuantity(1).build();

        this.webTestClient.post()
                .uri(ORDER_PATH + "/")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(order)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // Antes era postOrder_Forbidden (esperaba 403) -- STANDARD solo podía hacer GET.
    // Ahora STANDARD puede crear su propio pedido a propósito (ver
    // docs/security/threat-model.md, cierre de Elevation of Privilege): crear un pedido
    // es la única escritura que un cliente final legítimamente necesita.
    @Test
    void postOrder_WithStandardToken_Succeeds() {
        OrderCreateRequestDTO order = anOrderCreateRequestDTO().withMenuId(this.existingMenu.getId()).withQuantity(1).build();

        this.webTestClient.post()
                .uri(ORDER_PATH + "/")
                .header(AUTH_HEADER, this.standardToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(order)
                .exchange()
                .expectStatus().is2xxSuccessful();
    }

    @Test
    void postOrder_WhenMenuDoesNotExist_ReturnsNotFound() {
        UUID unknownMenuId = UUID.randomUUID();
        OrderCreateRequestDTO order = anOrderCreateRequestDTO().withMenuId(unknownMenuId).withQuantity(1).build();

        this.webTestClient.post()
                .uri(ORDER_PATH + "/")
                .header(AUTH_HEADER, this.primeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(order)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("MENU_NOT_FOUND");
    }

    @Test
    void getOrderStream_Success() {
        OrderCreateRequestDTO order = anOrderCreateRequestDTO().withMenuId(this.existingMenu.getId()).withQuantity(5).build();

        this.webTestClient.post()
                .uri(ORDER_PATH + "/")
                .header(AUTH_HEADER, this.primeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(order)
                .exchange()
                .expectStatus().is2xxSuccessful();

        this.webTestClient
                .get()
                .uri(ORDER_PATH + "/stream")
                .header(AUTH_HEADER, this.standardToken)
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

    // Ver docs/adr/0002-order-idempotency-and-menu-lookup-resilience.md: simula un
    // cliente que reintenta la MISMA request (timeout de red, no sabe si la primera
    // llegó a procesarse) -- la segunda llamada con la misma Idempotency-Key no debe
    // crear un segundo pedido.
    @Test
    void postOrder_WithSameIdempotencyKeyTwiceSequentially_CreatesOnlyOneOrder() {
        OrderCreateRequestDTO order = anOrderCreateRequestDTO().withMenuId(this.existingMenu.getId()).withQuantity(3).build();
        String idempotencyKey = "sequential-retry-key";

        UUID firstOrderId = this.webTestClient.post()
                .uri(ORDER_PATH + "/")
                .header(AUTH_HEADER, this.primeToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(order)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponseDTO.class)
                .returnResult()
                .getResponseBody()
                .orderId();

        UUID secondOrderId = this.webTestClient.post()
                .uri(ORDER_PATH + "/")
                .header(AUTH_HEADER, this.primeToken)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(order)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponseDTO.class)
                .returnResult()
                .getResponseBody()
                .orderId();

        assertEquals(firstOrderId, secondOrderId);
        assertEquals(1, this.orderR2dbcRepository.count().block());
    }

    // El caso real que motiva esto: dos requests CONCURRENTES con la misma clave (no
    // secuenciales) -- un SELECT-antes-de-INSERT no alcanza por sí solo, la fuente de
    // verdad es la constraint única de Postgres (migración V5) + el catch de
    // DuplicateKeyException en OrderUseCasesService.
    @Test
    void postOrder_WithSameIdempotencyKeyConcurrently_CreatesOnlyOneOrder() throws InterruptedException {
        OrderCreateRequestDTO order = anOrderCreateRequestDTO().withMenuId(this.existingMenu.getId()).withQuantity(1).build();
        String idempotencyKey = "concurrent-retry-key";
        int concurrentRequests = 8;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);

        try {
            List<CompletableFuture<UUID>> requests = IntStream.range(0, concurrentRequests)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> this.webTestClient.post()
                            .uri(ORDER_PATH + "/")
                            .header(AUTH_HEADER, this.primeToken)
                            .header("Idempotency-Key", idempotencyKey)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(order)
                            .exchange()
                            .expectStatus().isCreated()
                            .expectBody(OrderResponseDTO.class)
                            .returnResult()
                            .getResponseBody()
                            .orderId(), executor))
                    .toList();

            List<UUID> orderIds = requests.stream().map(CompletableFuture::join).collect(Collectors.toSet()).stream().toList();

            assertEquals(1, orderIds.size(),
                    "Las " + concurrentRequests + " respuestas deberían apuntar todas al mismo pedido");
            assertEquals(1, this.orderR2dbcRepository.count().block(),
                    "Solo debería haberse insertado una fila, aunque " + concurrentRequests
                            + " requests concurrentes compartieran la misma Idempotency-Key");
        } finally {
            executor.shutdown();
        }
    }
}
