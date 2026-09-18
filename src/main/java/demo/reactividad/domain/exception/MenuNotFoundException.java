package demo.reactividad.domain.exception;

public class MenuNotFoundException extends MenuException {

    public MenuNotFoundException(String message, String errorCode) {
        super(message, errorCode);
    }
}
