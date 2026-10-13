package demo.reactividad.orders.domain.exception;

public class MenuLookupUnavailableException extends RuntimeException {

    public MenuLookupUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
