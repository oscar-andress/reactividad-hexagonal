package demo.reactividad.infrastructure.adapter.in.web.exception;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import demo.reactividad.domain.exception.MenuException;
import demo.reactividad.domain.exception.MenuNotFoundException;
import demo.reactividad.domain.exception.MenuUnavailableException;
import reactor.core.publisher.Mono;

@Component
public class GlobalExceptionHandler {

    public Mono<ServerResponse> handleMenuUnavailableException(MenuUnavailableException ex, ServerRequest request) {
        return buildResponse(ex, request, HttpStatus.CONFLICT);
    }

    public Mono<ServerResponse> handleMenuNotFoundException(MenuNotFoundException ex, ServerRequest request) {
        return buildResponse(ex, request, HttpStatus.NOT_FOUND);
    }

    private Mono<ServerResponse> buildResponse(MenuException ex, ServerRequest request, HttpStatus status) {
        ErrorResponse error = new ErrorResponse(
            LocalDateTime.now().toString(),
            status.value(),
            ex.getMessage(),
            ex.getErrorCode(),
            request.path());
        return ServerResponse.status(status).bodyValue(error);
    }
}
