package demo.reactividad.infrastructure.adapter.in.web.validation;

import java.util.Set;

import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

// Los endpoints funcionales (RouterFunction) no aplican @Valid automáticamente como
// un @RestController — este componente dispara la validación a mano.
@Component
@RequiredArgsConstructor
public class RequestValidator {

    private final Validator validator;

    public <T> Mono<T> validate(T value) {
        Set<ConstraintViolation<T>> violations = this.validator.validate(value);
        if (violations.isEmpty()) {
            return Mono.just(value);
        }
        return Mono.error(new ConstraintViolationException(violations));
    }
}
