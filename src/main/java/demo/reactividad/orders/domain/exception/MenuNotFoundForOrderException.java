package demo.reactividad.orders.domain.exception;

public class MenuNotFoundForOrderException extends RuntimeException {

    public MenuNotFoundForOrderException(String message) {
        super(message);
    }
}
