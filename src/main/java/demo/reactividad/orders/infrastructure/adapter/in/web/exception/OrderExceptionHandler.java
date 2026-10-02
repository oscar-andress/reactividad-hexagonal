package demo.reactividad.orders.infrastructure.adapter.in.web.exception;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import demo.reactividad.infrastructure.adapter.in.web.exception.ErrorResponse;
import demo.reactividad.orders.domain.exception.InvalidOrderException;
import demo.reactividad.orders.domain.exception.MenuNotFoundForOrderException;
import reactor.core.publisher.Mono;

@Component
public class OrderExceptionHandler {

    private static final String INVALID_ORDER_CODE = "INVALID_ORDER";
    private static final String MENU_NOT_FOUND_CODE = "MENU_NOT_FOUND";

    public Mono<ServerResponse> handleInvalidOrderException(InvalidOrderException ex, ServerRequest request) {
        return buildResponse(ex.getMessage(), INVALID_ORDER_CODE, request, HttpStatus.BAD_REQUEST);
    }

    public Mono<ServerResponse> handleMenuNotFoundForOrderException(
            MenuNotFoundForOrderException ex, ServerRequest request) {
        return buildResponse(ex.getMessage(), MENU_NOT_FOUND_CODE, request, HttpStatus.NOT_FOUND);
    }

    private Mono<ServerResponse> buildResponse(String message, String errorCode, ServerRequest request,
            HttpStatus status) {
        ErrorResponse error = new ErrorResponse(
            LocalDateTime.now().toString(),
            status.value(),
            message,
            errorCode,
            request.path());
        return ServerResponse.status(status).bodyValue(error);
    }
}
