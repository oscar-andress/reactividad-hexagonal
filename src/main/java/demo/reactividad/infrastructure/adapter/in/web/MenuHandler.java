package demo.reactividad.infrastructure.adapter.in.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import demo.reactividad.application.port.in.MenuUseCases;
import demo.reactividad.infrastructure.adapter.in.web.dto.request.MenuCreateRequestDTO;
import demo.reactividad.infrastructure.adapter.in.web.dto.response.MenuResponseDTO;
import demo.reactividad.infrastructure.adapter.in.web.mapper.MenuWebMapper;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MenuHandler {

    private final MenuUseCases menuUseCases;
    private final MenuWebMapper menuWebMapper;

    public Mono<ServerResponse> getMenu(ServerRequest request) {
        UUID menuId = UUID.fromString(request.pathVariable("menuId"));
        return this.menuUseCases.getMenu(menuId)
                .map(this.menuWebMapper::toResponseDTO)
                .flatMap(dto -> ServerResponse.status(HttpStatus.OK).bodyValue(dto));
    }

    public Mono<ServerResponse> createMenu(ServerRequest request) {
        return request.bodyToMono(MenuCreateRequestDTO.class)
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
        return this.menuUseCases.deleteMenu(UUID.fromString(request.pathVariable("menuId")))
                .then(ServerResponse.noContent().build());
    }

    public Mono<ServerResponse> create1MillionMenu(ServerRequest request) {
        Flux<MenuResponseDTO> responseFlux = this.menuUseCases.createMenus(
                        request.bodyToFlux(MenuCreateRequestDTO.class).map(this.menuWebMapper::toDomain))
                .map(this.menuWebMapper::toResponseDTO);
        return ServerResponse.status(HttpStatus.ACCEPTED)
                .contentType(MediaType.APPLICATION_NDJSON)
                .body(responseFlux, MenuResponseDTO.class);
    }
}
