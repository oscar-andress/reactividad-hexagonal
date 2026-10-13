package demo.reactividad.orders.infrastructure.adapter.in.web.router;

import static demo.reactividad.orders.testsupport.fixtures.OrderCreateRequestDTOTestDataBuilder.anOrderCreateRequestDTO;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import demo.reactividad.orders.application.port.in.OrderUseCases;
import demo.reactividad.orders.domain.exception.MenuLookupUnavailableException;
import demo.reactividad.orders.domain.exception.MenuNotFoundForOrderException;
import demo.reactividad.orders.domain.model.MenuId;
import demo.reactividad.orders.domain.model.Order;
import demo.reactividad.orders.infrastructure.adapter.in.web.OrderHandler;
import demo.reactividad.orders.infrastructure.adapter.in.web.exception.OrderExceptionHandler;
import demo.reactividad.orders.infrastructure.adapter.in.web.mapper.OrderWebMapper;
import reactor.core.publisher.Mono;

class OrderRouterConfigTest {

    @Test
    void postRoute_DelegatesToUseCaseWithMenuIdAndQuantityFromBody() {
        UUID menuId = UUID.randomUUID();
        OrderUseCases orderUseCases = mock(OrderUseCases.class);
        Order savedOrder = new Order(new MenuId(menuId), "Menu del dia", 2, null);
        when(orderUseCases.createOrder(eq(new MenuId(menuId)), eq(2), eq(null))).thenReturn(Mono.just(savedOrder));
        OrderHandler orderHandler = new OrderHandler(orderUseCases, new OrderWebMapper());
        OrderRouterConfig router = new OrderRouterConfig(orderHandler, new OrderExceptionHandler());
        WebTestClient client = WebTestClient.bindToRouterFunction(router.orderRoutes()).build();

        client.post().uri("/")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(anOrderCreateRequestDTO().withMenuId(menuId).withQuantity(2).build())
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.menuTitle").isEqualTo("Menu del dia");

        verify(orderUseCases).createOrder(new MenuId(menuId), 2, null);
    }

    @Test
    void postRoute_WithIdempotencyKeyHeader_PassesItToTheUseCase() {
        UUID menuId = UUID.randomUUID();
        OrderUseCases orderUseCases = mock(OrderUseCases.class);
        Order savedOrder = new Order(new MenuId(menuId), "Menu del dia", 2, "client-key-1");
        when(orderUseCases.createOrder(eq(new MenuId(menuId)), eq(2), eq("client-key-1")))
                .thenReturn(Mono.just(savedOrder));
        OrderHandler orderHandler = new OrderHandler(orderUseCases, new OrderWebMapper());
        OrderRouterConfig router = new OrderRouterConfig(orderHandler, new OrderExceptionHandler());
        WebTestClient client = WebTestClient.bindToRouterFunction(router.orderRoutes()).build();

        client.post().uri("/")
                .header("Idempotency-Key", "client-key-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(anOrderCreateRequestDTO().withMenuId(menuId).withQuantity(2).build())
                .exchange()
                .expectStatus().isCreated();

        verify(orderUseCases).createOrder(new MenuId(menuId), 2, "client-key-1");
    }

    @Test
    void postRoute_WhenMenuDoesNotExist_ReturnsNotFoundWithErrorBody() {
        UUID menuId = UUID.randomUUID();
        OrderUseCases orderUseCases = mock(OrderUseCases.class);
        when(orderUseCases.createOrder(any(MenuId.class), eq(1), eq(null))).thenReturn(
                Mono.error(new MenuNotFoundForOrderException("Cannot order menu " + menuId + ": it does not exist")));
        OrderHandler orderHandler = new OrderHandler(orderUseCases, new OrderWebMapper());
        OrderRouterConfig router = new OrderRouterConfig(orderHandler, new OrderExceptionHandler());
        WebTestClient client = WebTestClient.bindToRouterFunction(router.orderRoutes()).build();

        client.post().uri("/")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(anOrderCreateRequestDTO().withMenuId(menuId).withQuantity(1).build())
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("MENU_NOT_FOUND");
    }

    @Test
    void postRoute_WhenMenuLookupIsUnavailable_ReturnsServiceUnavailableWithErrorBody() {
        UUID menuId = UUID.randomUUID();
        OrderUseCases orderUseCases = mock(OrderUseCases.class);
        when(orderUseCases.createOrder(any(MenuId.class), eq(1), eq(null))).thenReturn(
                Mono.error(new MenuLookupUnavailableException("Menu lookup for " + menuId + " is currently unavailable",
                        new RuntimeException("timeout"))));
        OrderHandler orderHandler = new OrderHandler(orderUseCases, new OrderWebMapper());
        OrderRouterConfig router = new OrderRouterConfig(orderHandler, new OrderExceptionHandler());
        WebTestClient client = WebTestClient.bindToRouterFunction(router.orderRoutes()).build();

        client.post().uri("/")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(anOrderCreateRequestDTO().withMenuId(menuId).withQuantity(1).build())
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.errorCode").isEqualTo("MENU_LOOKUP_UNAVAILABLE");
    }
}
