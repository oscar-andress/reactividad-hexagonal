package demo.reactividad.infrastructure.adapter.in.web.security;

public class InvalidAuthTokenException extends RuntimeException {

    public InvalidAuthTokenException(String message) {
        super(message);
    }

    public InvalidAuthTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
