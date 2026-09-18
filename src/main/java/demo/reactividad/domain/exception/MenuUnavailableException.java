package demo.reactividad.domain.exception;

public class MenuUnavailableException extends MenuException {

    public MenuUnavailableException(String message, String errorCode) {
        super(message, errorCode);
    }
}
