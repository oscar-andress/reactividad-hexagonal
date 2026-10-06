package demo.reactividad.infrastructure.adapter.in.web.router;

import static demo.reactividad.testsupport.fixtures.MenuCreateRequestDTOTestDataBuilder.aMenuCreateRequestDTO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import demo.reactividad.application.port.in.MenuUseCases;
import demo.reactividad.domain.model.FoodType;
import demo.reactividad.domain.model.FoodTypeSuggestion;
import demo.reactividad.domain.model.Menu;
import demo.reactividad.infrastructure.adapter.in.web.MenuHandler;
import demo.reactividad.infrastructure.adapter.in.web.dto.response.FoodTypeSuggestionResponseDTO;
import demo.reactividad.infrastructure.adapter.in.web.exception.GlobalExceptionHandler;
import demo.reactividad.infrastructure.adapter.in.web.mapper.MenuWebMapper;
import demo.reactividad.infrastructure.adapter.in.web.multipart.MultipartFilePartExtractor;
import demo.reactividad.infrastructure.adapter.in.web.validation.RequestValidator;
import jakarta.validation.Validation;
import reactor.core.publisher.Mono;

class MenuRouterConfigTest {

    // Validator real (no mockeado) — es un objeto de reglas, no una frontera de
    // infraestructura, igual que SimpleMeterRegistry en los tests del Gap D.
    private final RequestValidator requestValidator =
            new RequestValidator(Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void deleteRoute_ResolvesMenuIdFromPathAndDelegatesToUseCase() {
        UUID menuId = UUID.randomUUID();
        MenuUseCases menuUseCases = mock(MenuUseCases.class);
        when(menuUseCases.deleteMenu(eq(menuId))).thenReturn(Mono.empty());
        MenuHandler menuHandler = new MenuHandler(
                menuUseCases, mock(MenuWebMapper.class), mock(MultipartFilePartExtractor.class),
                this.requestValidator);
        MenuRouterConfig router = new MenuRouterConfig(menuHandler, new GlobalExceptionHandler());
        WebTestClient client = WebTestClient.bindToRouterFunction(router.menuRoutes()).build();

        client.delete().uri("/{menuId}", menuId)
                .exchange()
                .expectStatus().isNoContent();

        verify(menuUseCases).deleteMenu(menuId);
    }

    @Test
    void postRoute_WhenTitleIsBlank_RejectsWithBadRequestBeforeReachingUseCase() {
        MenuUseCases menuUseCases = mock(MenuUseCases.class);
        MenuWebMapper menuWebMapper = mock(MenuWebMapper.class);
        MenuHandler menuHandler = new MenuHandler(
                menuUseCases, menuWebMapper, mock(MultipartFilePartExtractor.class), this.requestValidator);
        MenuRouterConfig router = new MenuRouterConfig(menuHandler, new GlobalExceptionHandler());
        WebTestClient client = WebTestClient.bindToRouterFunction(router.menuRoutes()).build();

        client.post().uri("/")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(aMenuCreateRequestDTO().withTitle("  ").withDescription("Alguna descripción").build())
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("VALIDATION_FAILED")
                .consumeWith(response -> org.junit.jupiter.api.Assertions.assertTrue(
                        new String(response.getResponseBody(), java.nio.charset.StandardCharsets.UTF_8)
                                .contains("menuTitle")));

        verify(menuUseCases, never()).createMenu(any(Menu.class));
    }

    @Test
    void getSuggestFoodTypeRoute_ResolvesMenuIdFromPathAndReturnsSuggestion() {
        UUID menuId = UUID.randomUUID();
        MenuUseCases menuUseCases = mock(MenuUseCases.class);
        MenuWebMapper menuWebMapper = mock(MenuWebMapper.class);
        FoodType vegano = new FoodType(UUID.randomUUID(), "Vegano", true);
        FoodTypeSuggestion suggestion = new FoodTypeSuggestion(vegano, 0.75);
        FoodTypeSuggestionResponseDTO responseDTO = new FoodTypeSuggestionResponseDTO(vegano.getId(), "Vegano", 0.75);
        when(menuUseCases.suggestFoodType(menuId)).thenReturn(Mono.just(suggestion));
        when(menuWebMapper.toResponseDTO(suggestion)).thenReturn(responseDTO);
        MenuHandler menuHandler = new MenuHandler(
                menuUseCases, menuWebMapper, mock(MultipartFilePartExtractor.class), this.requestValidator);
        MenuRouterConfig router = new MenuRouterConfig(menuHandler, new GlobalExceptionHandler());
        WebTestClient client = WebTestClient.bindToRouterFunction(router.menuRoutes()).build();

        client.get().uri("/{menuId}/suggest-food-type", menuId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.foodTypeName").isEqualTo("Vegano");

        verify(menuUseCases).suggestFoodType(menuId);
    }

    @Test
    void getSuggestFoodTypeRoute_WhenNoSafeSuggestion_ReturnsNoContent() {
        UUID menuId = UUID.randomUUID();
        MenuUseCases menuUseCases = mock(MenuUseCases.class);
        when(menuUseCases.suggestFoodType(menuId)).thenReturn(Mono.empty());
        MenuHandler menuHandler = new MenuHandler(
                menuUseCases, mock(MenuWebMapper.class), mock(MultipartFilePartExtractor.class), this.requestValidator);
        MenuRouterConfig router = new MenuRouterConfig(menuHandler, new GlobalExceptionHandler());
        WebTestClient client = WebTestClient.bindToRouterFunction(router.menuRoutes()).build();

        client.get().uri("/{menuId}/suggest-food-type", menuId)
                .exchange()
                .expectStatus().isNoContent();
    }
}
