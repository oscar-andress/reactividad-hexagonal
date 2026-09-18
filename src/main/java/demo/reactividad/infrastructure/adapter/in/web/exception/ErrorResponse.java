package demo.reactividad.infrastructure.adapter.in.web.exception;

public record ErrorResponse(
    String timestamp,
    int status,
    String message,
    String errorCode,
    String path
) {

}
