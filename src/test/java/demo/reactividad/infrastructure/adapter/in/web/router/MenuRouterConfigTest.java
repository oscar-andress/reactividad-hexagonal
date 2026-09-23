package demo.reactividad.infrastructure.adapter.in.web.router;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import demo.reactividad.application.port.in.MenuUseCases;
import demo.reactividad.infrastructure.adapter.in.web.MenuHandler;
import demo.reactividad.infrastructure.adapter.in.web.exception.GlobalExceptionHandler;
import demo.reactividad.infrastructure.adapter.in.web.mapper.MenuWebMapper;
import demo.reactividad.infrastructure.adapter.in.web.multipart.MultipartFilePartExtractor;
import reactor.core.publisher.Mono;

class MenuRouterConfigTest {

    @Test
    void deleteRoute_ResolvesMenuIdFromPathAndDelegatesToUseCase() {
        UUID menuId = UUID.randomUUID();
        MenuUseCases menuUseCases = mock(MenuUseCases.class);
        when(menuUseCases.deleteMenu(eq(menuId))).thenReturn(Mono.empty());
        MenuHandler menuHandler = new MenuHandler(
                menuUseCases, mock(MenuWebMapper.class), mock(MultipartFilePartExtractor.class));
        MenuRouterConfig router = new MenuRouterConfig(menuHandler, new GlobalExceptionHandler());
        WebTestClient client = WebTestClient.bindToRouterFunction(router.menuRoutes()).build();

        client.delete().uri("/{menuId}", menuId)
                .exchange()
                .expectStatus().isNoContent();

        verify(menuUseCases).deleteMenu(menuId);
    }
}
