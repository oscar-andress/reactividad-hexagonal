package demo.reactividad.orders.infrastructure.adapter.in.web.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import demo.reactividad.orders.domain.exception.InvalidOrderException;
import demo.reactividad.orders.domain.exception.MenuNotFoundForOrderException;
import demo.reactividad.orders.infrastructure.adapter.in.web.OrderHandler;
import demo.reactividad.orders.infrastructure.adapter.in.web.dto.request.OrderCreateRequestDTO;
import demo.reactividad.orders.infrastructure.adapter.in.web.dto.response.OrderResponseDTO;
import demo.reactividad.orders.infrastructure.adapter.in.web.exception.OrderExceptionHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;

// Ver MenuRouterConfig: estilo funcional, springdoc-openapi necesita @RouterOperation
// apuntando al handler real para poblar /v3/api-docs (ver docs/openapi.md).
@Configuration
@RequiredArgsConstructor
public class OrderRouterConfig {

    private final OrderHandler orderHandler;
    private final OrderExceptionHandler orderExceptionHandler;

    @RouterOperations({
        @RouterOperation(path = "/api/v1/order/stream",
                method = RequestMethod.GET, beanClass = OrderHandler.class, beanMethod = "getOrderStream",
                operation = @Operation(operationId = "getOrderStream",
                        responses = @ApiResponse(responseCode = "200",
                                content = @Content(mediaType = "text/event-stream",
                                        schema = @Schema(implementation = OrderResponseDTO.class))))),
        @RouterOperation(path = "/api/v1/order/",
                method = RequestMethod.POST, beanClass = OrderHandler.class, beanMethod = "createOrder",
                operation = @Operation(operationId = "createOrder",
                        requestBody = @RequestBody(content =
                                @Content(schema = @Schema(implementation = OrderCreateRequestDTO.class))),
                        responses = @ApiResponse(responseCode = "201",
                                content = @Content(schema = @Schema(implementation = OrderResponseDTO.class)))))
    })
    @Bean
    public RouterFunction<ServerResponse> orderRoute() {
        return RouterFunctions.route()
            .path("/api/v1/order", this::orderRoutes)
            .build();
    }

    public RouterFunction<ServerResponse> orderRoutes() {
        return RouterFunctions.route()
            .GET("/stream",
                  accept(MediaType.TEXT_EVENT_STREAM),
                  this.orderHandler::getOrderStream)
            .POST("/", this.orderHandler::createOrder)
            .onError(InvalidOrderException.class, this.orderExceptionHandler::handleInvalidOrderException)
            .onError(MenuNotFoundForOrderException.class,
                     this.orderExceptionHandler::handleMenuNotFoundForOrderException)
            .build();
    }
}
