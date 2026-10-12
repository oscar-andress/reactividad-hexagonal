package demo.reactividad.infrastructure.adapter.in.web.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import demo.reactividad.domain.exception.FeatureDisabledException;
import demo.reactividad.domain.exception.ImageStorageException;
import demo.reactividad.domain.exception.MenuNotFoundException;
import demo.reactividad.domain.exception.MenuUnavailableException;
import demo.reactividad.infrastructure.adapter.in.web.MenuHandler;
import demo.reactividad.infrastructure.adapter.in.web.dto.request.MenuCreateRequestDTO;
import demo.reactividad.infrastructure.adapter.in.web.dto.request.MenuUpdateRequestDTO;
import demo.reactividad.infrastructure.adapter.in.web.dto.response.FoodTypeSuggestionResponseDTO;
import demo.reactividad.infrastructure.adapter.in.web.dto.response.MenuResponseDTO;
import demo.reactividad.infrastructure.adapter.in.web.exception.GlobalExceptionHandler;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;

// Estilo funcional de WebFlux (RouterFunction, no @RestController): springdoc-openapi
// no descubre estas rutas por reflexión sola, igual que @Valid tampoco se dispara solo
// (ver docs/security/bean-validation.md) -- necesita @RouterOperation apuntando al
// método real del handler para poblar /v3/api-docs (ver docs/openapi.md).
@Configuration
@RequiredArgsConstructor
public class MenuRouterConfig {

    private final MenuHandler menuHandler;
    private final GlobalExceptionHandler globalExceptionHandler;

    @RouterOperations({
        @RouterOperation(path = "/api/v1/menu/stream",
                method = RequestMethod.GET, beanClass = MenuHandler.class, beanMethod = "getMenuStream",
                operation = @Operation(operationId = "getMenuStream",
                        responses = @ApiResponse(responseCode = "200",
                                content = @Content(mediaType = "text/event-stream",
                                        schema = @Schema(implementation = MenuResponseDTO.class))))),
        @RouterOperation(path = "/api/v1/menu/{menuId}",
                method = RequestMethod.GET, beanClass = MenuHandler.class, beanMethod = "getMenu",
                operation = @Operation(operationId = "getMenu",
                        parameters = @Parameter(name = "menuId", in = ParameterIn.PATH, required = true,
                                schema = @Schema(type = "string", format = "uuid")),
                        responses = @ApiResponse(responseCode = "200",
                                content = @Content(schema = @Schema(implementation = MenuResponseDTO.class))))),
        @RouterOperation(path = "/api/v1/menu/{menuId}/suggest-food-type",
                method = RequestMethod.GET, beanClass = MenuHandler.class, beanMethod = "suggestFoodTypeForMenu",
                operation = @Operation(operationId = "suggestFoodTypeForMenu",
                        parameters = @Parameter(name = "menuId", in = ParameterIn.PATH, required = true,
                                schema = @Schema(type = "string", format = "uuid")),
                        responses = {
                            @ApiResponse(responseCode = "200",
                                    content = @Content(schema =
                                            @Schema(implementation = FoodTypeSuggestionResponseDTO.class))),
                            @ApiResponse(responseCode = "204", description = "No hay sugerencia disponible")
                        })),
        @RouterOperation(path = "/api/v1/menu/million",
                method = RequestMethod.POST, beanClass = MenuHandler.class, beanMethod = "create1MillionMenu",
                operation = @Operation(operationId = "create1MillionMenu",
                        requestBody = @RequestBody(description = "Stream NDJSON, un MenuCreateRequestDTO por línea",
                                content = @Content(mediaType = "application/x-ndjson",
                                        schema = @Schema(implementation = MenuCreateRequestDTO.class))),
                        responses = @ApiResponse(responseCode = "202",
                                content = @Content(mediaType = "application/x-ndjson",
                                        schema = @Schema(implementation = MenuResponseDTO.class))))),
        @RouterOperation(path = "/api/v1/menu/",
                method = RequestMethod.POST, beanClass = MenuHandler.class, beanMethod = "createMenu",
                operation = @Operation(operationId = "createMenu",
                        requestBody = @RequestBody(content =
                                @Content(schema = @Schema(implementation = MenuCreateRequestDTO.class))),
                        responses = @ApiResponse(responseCode = "201",
                                content = @Content(schema = @Schema(implementation = MenuResponseDTO.class))))),
        @RouterOperation(path = "/api/v1/menu/{menuId}",
                method = RequestMethod.DELETE, beanClass = MenuHandler.class, beanMethod = "deleteMenu",
                operation = @Operation(operationId = "deleteMenu",
                        parameters = @Parameter(name = "menuId", in = ParameterIn.PATH, required = true,
                                schema = @Schema(type = "string", format = "uuid")),
                        responses = @ApiResponse(responseCode = "204", description = "No Content"))),
        @RouterOperation(path = "/api/v1/menu/{menuId}",
                method = RequestMethod.PUT, beanClass = MenuHandler.class, beanMethod = "updateMenu",
                operation = @Operation(operationId = "updateMenu",
                        parameters = @Parameter(name = "menuId", in = ParameterIn.PATH, required = true,
                                schema = @Schema(type = "string", format = "uuid")),
                        requestBody = @RequestBody(content =
                                @Content(schema = @Schema(implementation = MenuUpdateRequestDTO.class))),
                        responses = @ApiResponse(responseCode = "200",
                                content = @Content(schema = @Schema(implementation = MenuResponseDTO.class))))),
        @RouterOperation(path = "/api/v1/menu/{menuId}/image",
                method = RequestMethod.POST, beanClass = MenuHandler.class, beanMethod = "uploadMenuImage",
                operation = @Operation(operationId = "uploadMenuImage",
                        parameters = @Parameter(name = "menuId", in = ParameterIn.PATH, required = true,
                                schema = @Schema(type = "string", format = "uuid")),
                        requestBody = @RequestBody(
                                description = "Multipart, part \"image\" con el archivo a subir",
                                content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)),
                        responses = @ApiResponse(responseCode = "200",
                                content = @Content(schema = @Schema(implementation = MenuResponseDTO.class)))))
    })
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
            .GET("/{menuId}/suggest-food-type", this.menuHandler::suggestFoodTypeForMenu)
            .POST("/million",
                  contentType(MediaType.APPLICATION_NDJSON)
                  .and(accept(MediaType.APPLICATION_NDJSON)),
                  this.menuHandler::create1MillionMenu)
            .POST("/", this.menuHandler::createMenu)
            .DELETE("/{menuId}", this.menuHandler::deleteMenu)
            .PUT("/{menuId}", this.menuHandler::updateMenu)
            .POST("/{menuId}/image",
                  contentType(MediaType.MULTIPART_FORM_DATA),
                  this.menuHandler::uploadMenuImage)
            .onError(MenuNotFoundException.class, this.globalExceptionHandler::handleMenuNotFoundException)
            .onError(MenuUnavailableException.class, this.globalExceptionHandler::handleMenuUnavailableException)
            .onError(ImageStorageException.class, this.globalExceptionHandler::handleImageStorageException)
            .onError(ConstraintViolationException.class, this.globalExceptionHandler::handleConstraintViolationException)
            .onError(RequestNotPermitted.class, this.globalExceptionHandler::handleRequestNotPermitted)
            .onError(IllegalArgumentException.class, this.globalExceptionHandler::handleIllegalArgumentException)
            .onError(FeatureDisabledException.class, this.globalExceptionHandler::handleFeatureDisabledException)
            .build();
    }
}
