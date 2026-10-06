package demo.reactividad.domain.exception;

// Nunca llega al cliente HTTP como error — se captura siempre en la capa de
// aplicación y se traduce a "sin sugerencia" (ver MenuUseCasesService.suggestFoodType).
// Por eso no necesita la ceremonia de errorCode de MenuException: no hay ningún
// mapeo HTTP directo que lo necesite.
public class UnsafeAiResponseException extends RuntimeException {

    public UnsafeAiResponseException(String message) {
        super(message);
    }
}
