package demo.reactividad.infrastructure.adapter.in.web;

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

import demo.reactividad.infrastructure.adapter.in.web.dto.request.MenuCreateRequestDTO;
import demo.reactividad.infrastructure.adapter.in.web.dto.request.MenuUpdateRequestDTO;
import demo.reactividad.infrastructure.adapter.in.web.dto.response.MenuResponseDTO;
import demo.reactividad.infrastructure.adapter.out.persistence.entity.MenuEntity;
import demo.reactividad.infrastructure.adapter.out.persistence.repository.MenuR2dbcRepository;
import demo.reactividad.testsupport.containers.AbstractPostgresContainerTest;
import reactor.test.StepVerifier;

@AutoConfigureWebTestClient
@SpringBootTest
class MenuWebIntegrationIT extends AbstractPostgresContainerTest {

    private static final String MENU_PATH = "/api/v1/menu";
    private static final String AUTH_HEADER = "auth-token";
    private static final String STANDARD_TOKEN = "secret123";
    private static final String PRIME_TOKEN = "secret456";
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(5);
    private static final Logger log = LoggerFactory.getLogger(MenuWebIntegrationIT.class);

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private MenuR2dbcRepository menuR2dbcRepository;

    private MenuEntity existingMenu;

    @BeforeEach
    void setUp() {
        this.existingMenu = this.menuR2dbcRepository.deleteAll()
                .then(this.menuR2dbcRepository.save(aMenuEntity().build())
                          .doOnNext(l -> log.info("{}", l)))
                .block();
    }

    @Test
    void getMenu_Success() {
        this.webTestClient.get()
                .uri(MENU_PATH + "/{menuId}", this.existingMenu.getId())
                .header(AUTH_HEADER, STANDARD_TOKEN)
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
                .header(AUTH_HEADER, STANDARD_TOKEN)
                .exchange()
                .expectStatus().is4xxClientError()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .consumeWith(r -> log.info("{}", new String(r.getResponseBody(), StandardCharsets.UTF_8)))
                .jsonPath("$.message").isEqualTo("Menu with id " + unknownMenuId + " not found");
    }

    @Test
    void postMenu_Success() {
        MenuCreateRequestDTO menu = new MenuCreateRequestDTO("Test", "Test description");

        this.webTestClient.post()
                .uri(MENU_PATH + "/")
                .header(AUTH_HEADER, PRIME_TOKEN)
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
    void putMenu_Success() {
        MenuUpdateRequestDTO menu = new MenuUpdateRequestDTO("Updated title", "Updated description");

        this.webTestClient.put()
                .uri(MENU_PATH + "/{menuId}", this.existingMenu.getId())
                .header(AUTH_HEADER, PRIME_TOKEN)
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
        MenuCreateRequestDTO menu = new MenuCreateRequestDTO("Test", "Test description");

        this.webTestClient.post()
                .uri(MENU_PATH + "/")
                .header(AUTH_HEADER, STANDARD_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(menu)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void getMenuStream_Success() {
        String streamMenuTitle = "Stream menu";
        MenuCreateRequestDTO menu = new MenuCreateRequestDTO(streamMenuTitle, "Streamed via SSE");

        this.webTestClient.post()
                .uri(MENU_PATH + "/")
                .header(AUTH_HEADER, PRIME_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(menu)
                .exchange()
                .expectStatus().is2xxSuccessful();

        this.webTestClient
                .get()
                .uri(MENU_PATH + "/stream")
                .header(AUTH_HEADER, STANDARD_TOKEN)
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
}
