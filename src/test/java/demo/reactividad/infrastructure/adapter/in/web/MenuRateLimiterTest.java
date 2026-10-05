package demo.reactividad.infrastructure.adapter.in.web;

import static demo.reactividad.testsupport.fixtures.MenuCreateRequestDTOTestDataBuilder.aMenuCreateRequestDTO;
import static demo.reactividad.testsupport.fixtures.MenuTestDataBuilder.aMenu;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

import demo.reactividad.application.port.in.MenuUseCases;
import demo.reactividad.domain.model.Menu;
import demo.reactividad.infrastructure.adapter.in.web.dto.request.MenuCreateRequestDTO;
import demo.reactividad.infrastructure.adapter.in.web.security.AuthenticationCategory;
import demo.reactividad.infrastructure.adapter.in.web.security.JwtTokenService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// Necesita un contexto real de Spring: @RateLimiter solo actúa a través del proxy AOP
// (ver docs/resilience.md, docs/testing-strategy.md).
//
// Las llamadas se disparan CONCURRENTEMENTE (no en secuencia) a propósito: con llamadas
// secuenciales, el test dependía de que las 6 terminaran dentro del mismo segundo de
// reloj — frágil bajo carga (falló al correr junto al resto del suite, aunque pasaba
// en aislamiento). Disparándolas en paralelo, el tiempo total no depende de cuántas
// llamadas haya ni de qué tan ocupada esté la máquina.
@AutoConfigureWebTestClient
@SpringBootTest
class MenuRateLimiterTest {

    private static final String AUTH_HEADER = "auth-token";
    private static final int BURST_SIZE = 6;

    @MockitoBean
    private MenuUseCases menuUseCases;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void create1MillionMenu_WhenBurstExceedsLimit_RejectsExcessWithTooManyRequests() throws Exception {
        when(this.menuUseCases.createMenus(any())).thenReturn(Flux.empty());
        String primeToken = this.jwtTokenService.generate(AuthenticationCategory.PRIME);
        MenuCreateRequestDTO body = aMenuCreateRequestDTO().build();

        // resilience4j.ratelimiter.instances.create-menus-bulk.limit-for-period=5 (ver
        // application.properties) — con 6 llamadas concurrentes, al menos una debe rechazarse.
        List<HttpStatusCode> statuses = fireConcurrently(() -> this.webTestClient.post()
                .uri("/api/v1/menu/million")
                .header(AUTH_HEADER, primeToken)
                .contentType(MediaType.APPLICATION_NDJSON)
                .accept(MediaType.APPLICATION_NDJSON)
                .bodyValue(body)
                .exchange()
                .returnResult(Void.class)
                .getStatus());

        org.junit.jupiter.api.Assertions.assertTrue(
                statuses.contains(HttpStatus.TOO_MANY_REQUESTS),
                "Esperábamos que al menos una de las " + BURST_SIZE + " llamadas fuera rechazada con 429, status reales: "
                        + statuses);
    }

    @Test
    void uploadMenuImage_WhenBurstExceedsLimit_RejectsExcessWithTooManyRequests() throws Exception {
        Menu menu = aMenu().build();
        when(this.menuUseCases.uploadMenuImage(any(), any(), any())).thenReturn(Mono.just(menu));
        String primeToken = this.jwtTokenService.generate(AuthenticationCategory.PRIME);
        UUID menuId = UUID.randomUUID();

        // resilience4j.ratelimiter.instances.menu-image-upload.limit-for-period=5 — con
        // 6 llamadas concurrentes, al menos una debe rechazarse.
        List<HttpStatusCode> statuses = fireConcurrently(() -> this.webTestClient.post()
                .uri("/api/v1/menu/{menuId}/image", menuId)
                .header(AUTH_HEADER, primeToken)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipartImageBody().build()))
                .exchange()
                .returnResult(Void.class)
                .getStatus());

        org.junit.jupiter.api.Assertions.assertTrue(
                statuses.contains(HttpStatus.TOO_MANY_REQUESTS),
                "Esperábamos que al menos una de las " + BURST_SIZE + " llamadas fuera rechazada con 429, status reales: "
                        + statuses);
    }

    private List<HttpStatusCode> fireConcurrently(java.util.function.Supplier<HttpStatusCode> request)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(BURST_SIZE);
        try {
            List<CompletableFuture<HttpStatusCode>> futures = IntStream.range(0, BURST_SIZE)
                    .mapToObj(i -> CompletableFuture.supplyAsync(request::get, executor))
                    .toList();
            List<HttpStatusCode> statuses = new java.util.ArrayList<>();
            for (CompletableFuture<HttpStatusCode> future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        } finally {
            executor.shutdown();
        }
    }

    private MultipartBodyBuilder multipartImageBody() {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("image", new ByteArrayResource("fake-image-bytes".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "menu.png";
            }
        }).contentType(MediaType.IMAGE_PNG);
        return builder;
    }
}
