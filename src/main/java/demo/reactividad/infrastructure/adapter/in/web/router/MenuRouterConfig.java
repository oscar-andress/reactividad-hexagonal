package demo.reactividad.infrastructure.adapter.in.web.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import demo.reactividad.domain.exception.MenuNotFoundException;
import demo.reactividad.domain.exception.MenuUnavailableException;
import demo.reactividad.infrastructure.adapter.in.web.MenuHandler;
import demo.reactividad.infrastructure.adapter.in.web.exception.GlobalExceptionHandler;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class MenuRouterConfig {

    private final MenuHandler menuHandler;
    private final GlobalExceptionHandler globalExceptionHandler;

    @Bean
    public RouterFunction<ServerResponse> menuRoute() {
        return RouterFunctions.route()
            .path("/api/v1/menu", this::menuRoutes)
            .build();
    }

    public RouterFunction<ServerResponse> menuRoutes() {
        return RouterFunctions.route()
            .GET("/stream",
                  accept(MediaType.TEXT_EVENT_STREAM),
                  this.menuHandler::getMenuStream)
            .GET("/{menuId}", this.menuHandler::getMenu)
            .POST("/million",
                  contentType(MediaType.APPLICATION_NDJSON)
                  .and(accept(MediaType.APPLICATION_NDJSON)),
                  this.menuHandler::create1MillionMenu)
            .POST("/", this.menuHandler::createMenu)
            .DELETE("/", this.menuHandler::deleteMenu)
            .onError(MenuNotFoundException.class, this.globalExceptionHandler::handleMenuNotFoundException)
            .onError(MenuUnavailableException.class, this.globalExceptionHandler::handleMenuUnavailableException)
            .build();
    }
}
