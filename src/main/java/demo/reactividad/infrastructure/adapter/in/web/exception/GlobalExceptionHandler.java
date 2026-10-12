package demo.reactividad.infrastructure.adapter.in.web.exception;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import demo.reactividad.domain.exception.FeatureDisabledException;
import demo.reactividad.domain.exception.ImageStorageException;
import demo.reactividad.domain.exception.MenuException;
import demo.reactividad.domain.exception.MenuNotFoundException;
import demo.reactividad.domain.exception.MenuUnavailableException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.validation.ConstraintViolationException;
import reactor.core.publisher.Mono;

@Component
public class GlobalExceptionHandler {

    private static final String IMAGE_STORAGE_FAILED_CODE = "IMAGE_STORAGE_FAILED";
    private static final String VALIDATION_FAILED_CODE = "VALIDATION_FAILED";
    private static final String RATE_LIMIT_EXCEEDED_CODE = "RATE_LIMIT_EXCEEDED";
    private static final String INVALID_PARAMETER_CODE = "INVALID_PARAMETER";
    private static final String INVALID_PARAMETER_MESSAGE = "Invalid request parameter format";

    public Mono<ServerResponse> handleMenuUnavailableException(MenuUnavailableException ex, ServerRequest request) {
        return buildResponse(ex, request, HttpStatus.CONFLICT);
    }

    public Mono<ServerResponse> handleMenuNotFoundException(MenuNotFoundException ex, ServerRequest request) {
        return buildResponse(ex, request, HttpStatus.NOT_FOUND);
    }

    public Mono<ServerResponse> handleImageStorageException(ImageStorageException ex, ServerRequest request) {
        return buildResponse(ex.getMessage(), IMAGE_STORAGE_FAILED_CODE, request, HttpStatus.BAD_GATEWAY);
    }

    public Mono<ServerResponse> handleConstraintViolationException(
            ConstraintViolationException ex, ServerRequest request) {
        String message = ex.getConstraintViolations().stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        return buildResponse(message, VALIDATION_FAILED_CODE, request, HttpStatus.BAD_REQUEST);
    }

    public Mono<ServerResponse> handleRequestNotPermitted(RequestNotPermitted ex, ServerRequest request) {
        return buildResponse(ex.getMessage(), RATE_LIMIT_EXCEEDED_CODE, request, HttpStatus.TOO_MANY_REQUESTS);
    }

    // Hallazgo real de OWASP ZAP (ver docs/security/threat-model.md): un menuId con
    // formato inválido (ej. no un UUID) rompía UUID.fromString sin que nada lo capturara,
    // escapando hasta el manejador default de Spring con un 500 y el stack trace
    // completo en el body. Nunca se usa ex.getMessage() acá a propósito -- a diferencia
    // de las demás excepciones de este archivo (de dominio, con mensajes ya controlados),
    // IllegalArgumentException es genérica y puede venir con texto interno no pensado
    // para un cliente HTTP.
    public Mono<ServerResponse> handleIllegalArgumentException(IllegalArgumentException ex, ServerRequest request) {
        return buildResponse(INVALID_PARAMETER_MESSAGE, INVALID_PARAMETER_CODE, request, HttpStatus.BAD_REQUEST);
    }

    // 503, no 404/204 -- distingue a propósito "la feature está apagada" de "no hubo
    // sugerencia" (204, sigue existiendo) y de "la ruta no existe" (404). Ver
    // docs/data/feature-flag-reversibility.md.
    public Mono<ServerResponse> handleFeatureDisabledException(FeatureDisabledException ex, ServerRequest request) {
        return buildResponse(ex, request, HttpStatus.SERVICE_UNAVAILABLE);
    }

    private Mono<ServerResponse> buildResponse(MenuException ex, ServerRequest request, HttpStatus status) {
        return buildResponse(ex.getMessage(), ex.getErrorCode(), request, status);
    }

    private Mono<ServerResponse> buildResponse(String message, String errorCode, ServerRequest request, HttpStatus status) {
        ErrorResponse error = new ErrorResponse(
            LocalDateTime.now().toString(),
            status.value(),
            message,
            errorCode,
            request.path());
        return ServerResponse.status(status).bodyValue(error);
    }
}
