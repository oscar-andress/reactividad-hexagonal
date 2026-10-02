package demo.reactividad.orders.infrastructure.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import demo.reactividad.orders.application.port.in.OrderUseCases;
import demo.reactividad.orders.domain.model.MenuId;
import demo.reactividad.orders.infrastructure.adapter.in.web.dto.request.OrderCreateRequestDTO;
import demo.reactividad.orders.infrastructure.adapter.in.web.dto.response.OrderResponseDTO;
import demo.reactividad.orders.infrastructure.adapter.in.web.mapper.OrderWebMapper;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class OrderHandler {

    private final OrderUseCases orderUseCases;
    private final OrderWebMapper orderWebMapper;

    public Mono<ServerResponse> createOrder(ServerRequest request) {
        return request.bodyToMono(OrderCreateRequestDTO.class)
                .flatMap(dto -> this.orderUseCases.createOrder(new MenuId(dto.menuId()), dto.quantity()))
                .map(this.orderWebMapper::toResponseDTO)
                .flatMap(dto -> ServerResponse.status(HttpStatus.CREATED).bodyValue(dto));
    }

    public Mono<ServerResponse> getOrderStream(ServerRequest request) {
        Flux<OrderResponseDTO> responseFlux = this.orderUseCases.streamOrders()
                .map(this.orderWebMapper::toResponseDTO);
        return ServerResponse.status(HttpStatus.OK)
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(responseFlux, OrderResponseDTO.class);
    }
}
