package demo.reactividad.domain.exception;

public class FeatureDisabledException extends MenuException {

    public FeatureDisabledException(String message, String errorCode) {
        super(message, errorCode);
    }
}
