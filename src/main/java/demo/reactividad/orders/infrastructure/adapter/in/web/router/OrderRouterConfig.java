package demo.reactividad.orders.infrastructure.adapter.in.web.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import demo.reactividad.orders.domain.exception.InvalidOrderException;
import demo.reactividad.orders.domain.exception.MenuNotFoundForOrderException;
import demo.reactividad.orders.infrastructure.adapter.in.web.OrderHandler;
import demo.reactividad.orders.infrastructure.adapter.in.web.exception.OrderExceptionHandler;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class OrderRouterConfig {

    private final OrderHandler orderHandler;
    private final OrderExceptionHandler orderExceptionHandler;

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
