package demo.reactividad.infrastructure.adapter.in.web;

import static demo.reactividad.testsupport.fixtures.MenuCreateRequestDTOTestDataBuilder.aMenuCreateRequestDTO;
import static demo.reactividad.testsupport.fixtures.MenuEntityTestDataBuilder.aMenuEntity;
import static demo.reactividad.testsupport.fixtures.MenuUpdateRequestDTOTestDataBuilder.aMenuUpdateRequestDTO;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import demo.reactividad.infrastructure.adapter.in.web.dto.request.MenuCreateRequestDTO;
import demo.reactividad.infrastructure.adapter.in.web.dto.request.MenuUpdateRequestDTO;
import demo.reactividad.infrastructure.adapter.in.web.dto.response.MenuResponseDTO;
import demo.reactividad.infrastructure.adapter.in.web.security.AuthenticationCategory;
import demo.reactividad.infrastructure.adapter.in.web.security.JwtTokenService;
import demo.reactividad.infrastructure.adapter.out.persistence.entity.FoodTypeEntity;
import demo.reactividad.infrastructure.adapter.out.persistence.entity.MenuEntity;
import demo.reactividad.infrastructure.adapter.out.persistence.repository.FoodTypeR2dbcRepository;
import demo.reactividad.infrastructure.adapter.out.persistence.repository.MenuFoodTypeR2dbcRepository;
import demo.reactividad.infrastructure.adapter.out.persistence.repository.MenuR2dbcRepository;
import demo.reactividad.testsupport.containers.AbstractPostgresContainerTest;
import reactor.test.StepVerifier;

@AutoConfigureWebTestClient
@SpringBootTest
class MenuWebIntegrationIT extends AbstractPostgresContainerTest {

    private static final String MENU_PATH = "/api/v1/menu";
    private static final String AUTH_HEADER = "auth-token";
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(5);
    private static final Logger log = LoggerFactory.getLogger(MenuWebIntegrationIT.class);

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private MenuR2dbcRepository menuR2dbcRepository;

    @Autowired
    private FoodTypeR2dbcRepository foodTypeR2dbcRepository;

    @Autowired
    private MenuFoodTypeR2dbcRepository menuFoodTypeR2dbcRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    private MenuEntity existingMenu;
    private FoodTypeEntity existingFoodType;
    private String standardToken;
    private String primeToken;

    @BeforeEach
    void setUp() {
        this.standardToken = this.jwtTokenService.generate(AuthenticationCategory.STANDARD);
        this.primeToken = this.jwtTokenService.generate(AuthenticationCategory.PRIME);
        // tbl_menu_food_type tiene FK hacia ambas tablas — hay que vaciarla antes de
        // poder vaciar tbl_menu/tbl_food_type sin violar la constraint.
        this.menuFoodTypeR2dbcRepository.deleteAllAssignments().block();
        this.existingMenu = this.menuR2dbcRepository.deleteAll()
                .then(this.menuR2dbcRepository.save(aMenuEntity().build())
                          .doOnNext(l -> log.info("{}", l)))
                .block();
        this.existingFoodType = this.foodTypeR2dbcRepository.deleteAll()
                .then(this.foodTypeR2dbcRepository.save(new FoodTypeEntity(null, "Vegano", true)))
                .block();
    }

    @Test
    void getMenu_Success() {
        this.webTestClient.get()
                .uri(MENU_PATH + "/{menuId}", this.existingMenu.getId())
                .header(AUTH_HEADER, this.standardToken)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .consumeWith(r -> log.info("{}", new String(r.getResponseBody(), StandardCharsets.UTF_8)))
                .jsonPath("$.menuId").isEqualTo(this.existingMenu.getId().toString());
    }

    @Test
    void getMenu_Unauthorized() {
        this.webTestClient.get()
                .uri(MENU_PATH + "/{menuId}", this.existingMenu.getId())
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void getMenu_NotFound() {
        UUID unknownMenuId = UUID.randomUUID();

        this.webTestClient.get()
                .uri(MENU_PATH + "/{menuId}", unknownMenuId)
                .header(AUTH_HEADER, this.standardToken)
                .exchange()
                .expectStatus().is4xxClientError()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .consumeWith(r -> log.info("{}", new String(r.getResponseBody(), StandardCharsets.UTF_8)))
                .jsonPath("$.message").isEqualTo("Menu with id " + unknownMenuId + " not found");
    }

    @Test
    void postMenu_Success() {
        MenuCreateRequestDTO menu = aMenuCreateRequestDTO().build();

        this.webTestClient.post()
                .uri(MENU_PATH + "/")
                .header(AUTH_HEADER, this.primeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(menu)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .consumeWith(r -> log.info("{}", new String(r.getResponseBody(), StandardCharsets.UTF_8)))
                .jsonPath("$.menuTitle").isEqualTo(menu.menuTitle());
    }

    @Test
    void postMenu_WhenTitleIsBlank_ReturnsBadRequestWithStructuredError() {
        MenuCreateRequestDTO menu = aMenuCreateRequestDTO().withTitle("  ").build();

        this.webTestClient.post()
                .uri(MENU_PATH + "/")
                .header(AUTH_HEADER, this.primeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(menu)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .consumeWith(r -> log.info("{}", new String(r.getResponseBody(), StandardCharsets.UTF_8)))
                .jsonPath("$.errorCode").isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void putMenu_Success() {
        MenuUpdateRequestDTO menu = aMenuUpdateRequestDTO().build();

        this.webTestClient.put()
                .uri(MENU_PATH + "/{menuId}", this.existingMenu.getId())
                .header(AUTH_HEADER, this.primeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(menu)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .expectBody()
                .consumeWith(r -> log.info("{}", new String(r.getResponseBody(), StandardCharsets.UTF_8)))
                .jsonPath("$.menuTitle").isEqualTo(menu.menuTitle());
    }

    @Test
    void postMenu_Forbidden() {
        MenuCreateRequestDTO menu = aMenuCreateRequestDTO().build();

        this.webTestClient.post()
                .uri(MENU_PATH + "/")
                .header(AUTH_HEADER, this.standardToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(menu)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void getMenuStream_Success() {
        String streamMenuTitle = "Stream menu";
        MenuCreateRequestDTO menu = aMenuCreateRequestDTO().withTitle(streamMenuTitle).withDescription("Streamed via SSE").build();

        this.webTestClient.post()
                .uri(MENU_PATH + "/")
                .header(AUTH_HEADER, this.primeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(menu)
                .exchange()
                .expectStatus().is2xxSuccessful();

        this.webTestClient
                .get()
                .uri(MENU_PATH + "/stream")
                .header(AUTH_HEADER, this.standardToken)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().is2xxSuccessful()
                .returnResult(MenuResponseDTO.class)
                .getResponseBody()
                .as(StepVerifier::create)
                .expectNextMatches(received -> received.menuTitle().equals(streamMenuTitle))
                .thenCancel()
                .verify(STREAM_TIMEOUT);
    }

    @Test
    void create1MillionMenu_WhenBurstExceedsLimit_RejectsExcessWithTooManyRequests() throws Exception {
        // resilience4j.ratelimiter.instances.create-menus-bulk.limit-for-period=5 — con
        // 6 llamadas concurrentes, al menos una debe rechazarse. Disparadas en paralelo
        // (no en secuencia) para no depender de que la máquina sea lo bastante rápida
        // como para que las 6 entren en la misma ventana de 1 segundo (ver docs/security/).
        int burstSize = 6;
        MenuCreateRequestDTO menu = aMenuCreateRequestDTO().withTitle("Burst test").withDescription("Rate limiter IT").build();
        ExecutorService executor = Executors.newFixedThreadPool(burstSize);
        try {
            List<CompletableFuture<HttpStatusCode>> futures = IntStream.range(0, burstSize)
                    .mapToObj(i -> CompletableFuture.supplyAsync(() -> this.webTestClient.post()
                            .uri(MENU_PATH + "/million")
                            .header(AUTH_HEADER, this.primeToken)
                            .contentType(MediaType.APPLICATION_NDJSON)
                            .accept(MediaType.APPLICATION_NDJSON)
                            .bodyValue(menu)
                            .exchange()
                            .returnResult(Void.class)
                            .getStatus(), executor))
                    .toList();

            List<HttpStatusCode> statuses = futures.stream().map(CompletableFuture::join).toList();

            org.junit.jupiter.api.Assertions.assertTrue(
                    statuses.contains(HttpStatus.TOO_MANY_REQUESTS),
                    "Esperábamos que al menos una de las " + burstSize + " llamadas fuera rechazada con 429, status reales: "
                            + statuses);
        } finally {
            executor.shutdown();
        }
    }

    // Gap C, ejercicio 4 (human-in-the-loop): la sugerencia nunca escribe en la BD, por
    // más veces que se llame — solo un PUT explícito y posterior aplica el cambio.
    @Test
    void suggestFoodTypeForMenu_NeverWritesUntilAnExplicitPutApplies() {
        this.webTestClient.get()
                .uri(MENU_PATH + "/{menuId}/suggest-food-type", this.existingMenu.getId())
                .header(AUTH_HEADER, this.standardToken)
                .exchange()
                .expectStatus().is2xxSuccessful();

        List<FoodTypeEntity> foodTypesAfterSuggestion = this.foodTypeR2dbcRepository
                .findFoodTypeByMenuId(this.existingMenu.getId())
                .collectList()
                .block();
        org.junit.jupiter.api.Assertions.assertTrue(foodTypesAfterSuggestion.isEmpty(),
                "La sugerencia no debe asignar ningún food type por sí sola");

        MenuUpdateRequestDTO update = aMenuUpdateRequestDTO()
                .withFoodTypeIds(java.util.Set.of(this.existingFoodType.getFoodTypeId()))
                .build();

        this.webTestClient.put()
                .uri(MENU_PATH + "/{menuId}", this.existingMenu.getId())
                .header(AUTH_HEADER, this.primeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(update)
                .exchange()
                .expectStatus().is2xxSuccessful();

        List<FoodTypeEntity> foodTypesAfterPut = this.foodTypeR2dbcRepository
                .findFoodTypeByMenuId(this.existingMenu.getId())
                .collectList()
                .block();
        org.junit.jupiter.api.Assertions.assertEquals(1, foodTypesAfterPut.size(),
                "El PUT explícito sí debe aplicar la asignación de food types");
        org.junit.jupiter.api.Assertions.assertEquals(
                this.existingFoodType.getFoodTypeId(), foodTypesAfterPut.get(0).getFoodTypeId());
    }
}
