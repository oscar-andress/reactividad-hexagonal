package demo.reactividad.infrastructure.adapter.in.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import demo.reactividad.application.port.in.MenuUseCases;
import demo.reactividad.infrastructure.adapter.in.web.dto.request.MenuCreateRequestDTO;
import demo.reactividad.infrastructure.adapter.in.web.dto.request.MenuUpdateRequestDTO;
import demo.reactividad.infrastructure.adapter.in.web.dto.response.MenuResponseDTO;
import demo.reactividad.infrastructure.adapter.in.web.mapper.MenuWebMapper;
import demo.reactividad.infrastructure.adapter.in.web.multipart.MultipartFilePartExtractor;
import demo.reactividad.infrastructure.adapter.in.web.validation.RequestValidator;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MenuHandler {

    private static final String IMAGE_PART_NAME = "image";
    private static final String CREATE_MENUS_BULK_RATE_LIMITER = "create-menus-bulk";
    private static final String MENU_IMAGE_UPLOAD_RATE_LIMITER = "menu-image-upload";

    private final MenuUseCases menuUseCases;
    private final MenuWebMapper menuWebMapper;
    private final MultipartFilePartExtractor multipartFilePartExtractor;
    private final RequestValidator requestValidator;

    // menuId se parsea dentro de Mono.fromCallable, no antes del Mono -- un
    // IllegalArgumentException lanzado afuera de la cadena reactiva nunca llega al
    // .onError(...) del router (ver docs/security/threat-model.md, hallazgo real de
    // OWASP ZAP: un menuId inválido escapaba como 500 con el stack trace completo).
    public Mono<ServerResponse> getMenu(ServerRequest request) {
        return Mono.fromCallable(() -> UUID.fromString(request.pathVariable("menuId")))
                .flatMap(this.menuUseCases::getMenu)
                .map(this.menuWebMapper::toResponseDTO)
                .flatMap(dto -> ServerResponse.status(HttpStatus.OK).bodyValue(dto));
    }

    public Mono<ServerResponse> createMenu(ServerRequest request) {
        return request.bodyToMono(MenuCreateRequestDTO.class)
                .flatMap(this.requestValidator::validate)
                .map(this.menuWebMapper::toDomain)
                .flatMap(this.menuUseCases::createMenu)
                .map(this.menuWebMapper::toResponseDTO)
                .flatMap(dto -> ServerResponse.status(HttpStatus.CREATED).bodyValue(dto));
    }

    public Mono<ServerResponse> getMenuStream(ServerRequest request) {
        Flux<MenuResponseDTO> responseFlux = this.menuUseCases.streamMenus()
                .map(this.menuWebMapper::toResponseDTO);
        return ServerResponse.status(HttpStatus.OK)
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(responseFlux, MenuResponseDTO.class);
    }

    public Mono<ServerResponse> deleteMenu(ServerRequest request) {
        return Mono.fromCallable(() -> UUID.fromString(request.pathVariable("menuId")))
                .flatMap(this.menuUseCases::deleteMenu)
                .then(ServerResponse.noContent().build());
    }

    @RateLimiter(name = CREATE_MENUS_BULK_RATE_LIMITER)
    public Mono<ServerResponse> create1MillionMenu(ServerRequest request) {
        Flux<MenuResponseDTO> responseFlux = this.menuUseCases.createMenus(
                        request.bodyToFlux(MenuCreateRequestDTO.class).map(this.menuWebMapper::toDomain))
                .map(this.menuWebMapper::toResponseDTO);
        return ServerResponse.status(HttpStatus.ACCEPTED)
                .contentType(MediaType.APPLICATION_NDJSON)
                .body(responseFlux, MenuResponseDTO.class);
    }

    public Mono<ServerResponse> updateMenu(ServerRequest request) {
        return Mono.fromCallable(() -> UUID.fromString(request.pathVariable("menuId")))
                .flatMap(menuId -> request.bodyToMono(MenuUpdateRequestDTO.class)
                        .flatMap(this.requestValidator::validate)
                        .flatMap(dto -> this.menuUseCases.updateMenu(
                                this.menuWebMapper.toDomain(menuId, dto), dto.foodTypeIds())))
                .map(this.menuWebMapper::toResponseDTO)
                .flatMap(dto -> ServerResponse.ok().bodyValue(dto));
    }

    // Gap C, ejercicio 4 (human-in-the-loop): solo lee y sugiere, nunca escribe. Aplicar
    // la sugerencia requiere un PUT explícito y separado (updateMenu, con foodTypeIds).
    public Mono<ServerResponse> suggestFoodTypeForMenu(ServerRequest request) {
        return Mono.fromCallable(() -> UUID.fromString(request.pathVariable("menuId")))
                .flatMap(this.menuUseCases::suggestFoodType)
                .map(this.menuWebMapper::toResponseDTO)
                .flatMap(dto -> ServerResponse.ok().bodyValue(dto))
                .switchIfEmpty(ServerResponse.noContent().build());
    }

    @RateLimiter(name = MENU_IMAGE_UPLOAD_RATE_LIMITER)
    public Mono<ServerResponse> uploadMenuImage(ServerRequest request) {
        return Mono.fromCallable(() -> UUID.fromString(request.pathVariable("menuId")))
                .flatMap(menuId -> this.multipartFilePartExtractor.extract(request, IMAGE_PART_NAME)
                        .flatMap(file -> this.menuUseCases.uploadMenuImage(menuId, file.content(), file.contentType())))
                .map(this.menuWebMapper::toResponseDTO)
                .flatMap(dto -> ServerResponse.ok().bodyValue(dto));
    }
}
