# Bean Validation en los DTOs (Gap B, ejercicio 2)

Este doc cubre el ejercicio 2 del Gap B del roadmap (`C:\Users\oscar.vega\.claude\plans\parsed-kindling-pixel.md`): cerrar el hallazgo de **Tampering** del threat model (`docs/security/threat-model.md`) — los DTOs de request no tenían ninguna validación de campos.

## El problema concreto

```java
// MenuCreateRequestDTO.java — antes
public record MenuCreateRequestDTO(String menuTitle, String menuDescription) {}
```

Un título vacío llegaba sin ningún filtro hasta la base de datos (`tbl_menu.menu_title varchar(50) not null`), y fallaba ahí con un error SQL crudo — una respuesta 500 genérica, no un 400 claro que le diga al cliente qué campo estuvo mal.

## El detalle real de WebFlux funcional: `@Valid` no se aplica solo

Spring sí sabe aplicar validación automáticamente — pero solo en controladores anotados (`@RestController` + `@RequestBody @Valid Dto dto`). Este proyecto usa el estilo **funcional** (`RouterFunction` + `ServerRequest`/`ServerResponse`), donde `request.bodyToMono(Dto.class)` solo deserializa el JSON — nadie dispara la validación por vos. Hay que hacerlo a mano.

Se creó un componente chico y reutilizable para eso:

```java
@Component
@RequiredArgsConstructor
public class RequestValidator {

    private final Validator validator;   // jakarta.validation.Validator

    public <T> Mono<T> validate(T value) {
        Set<ConstraintViolation<T>> violations = this.validator.validate(value);
        if (violations.isEmpty()) {
            return Mono.just(value);
        }
        return Mono.error(new ConstraintViolationException(violations));
    }
}
```

Y se conectó en el único punto donde hace falta — después de deserializar el body, antes de mapear a dominio:

```java
// MenuHandler.createMenu
return request.bodyToMono(MenuCreateRequestDTO.class)
        .flatMap(this.requestValidator::validate)   // <- dispara @NotBlank/@Size a mano
        .map(this.menuWebMapper::toDomain)
        ...
```

## Las anotaciones, con el límite tomado del esquema real

```java
public record MenuCreateRequestDTO(
    @NotBlank(message = "menuTitle must not be blank")
    @Size(max = MenuCreateRequestDTO.MAX_FIELD_LENGTH, message = "menuTitle must be at most 50 characters")
    String menuTitle,

    @NotBlank(message = "menuDescription must not be blank")
    @Size(max = MenuCreateRequestDTO.MAX_FIELD_LENGTH, message = "menuDescription must be at most 50 characters")
    String menuDescription
) {
    static final int MAX_FIELD_LENGTH = 50;   // mismo límite que tbl_menu.menu_title/menu_description
}
```

`MenuUpdateRequestDTO` recibió el mismo tratamiento. `50` no es un número mágico sin fuente — es exactamente el límite que ya existe en `schema.sql` (`varchar(50)`); así, un título que *pasaría* la validación nunca puede fallar después por exceder la columna.

## El manejo de errores: un `ConstraintViolationException` → 400 estructurado

```java
// GlobalExceptionHandler.java
public Mono<ServerResponse> handleConstraintViolationException(
        ConstraintViolationException ex, ServerRequest request) {
    String message = ex.getConstraintViolations().stream()
            .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .collect(Collectors.joining(", "));
    return buildResponse(message, "VALIDATION_FAILED", request, HttpStatus.BAD_REQUEST);
}
```

Registrado en `MenuRouterConfig` igual que los demás `.onError(...)` ya existentes — mismo patrón, mismo `ErrorResponse` genérico que ya usan `MenuNotFoundException`/`MenuUnavailableException`.

## Decisión de alcance: no se tocó `OrderCreateRequestDTO`

El roadmap nombra explícitamente `MenuCreateRequestDTO`/`MenuUpdateRequestDTO` como archivos clave de este ejercicio — no `OrderCreateRequestDTO`. Se encontró, de paso, que `Orders` tiene un caso similar (`menuId` nulo en el request no está validado), pero a diferencia de `Menu`, el dominio de `Order` **ya** valida su propio invariante de negocio (`quantity < 1` lanza `InvalidOrderException`, mapeado a un 400 limpio por `OrderExceptionHandler`) — agregar Bean Validation ahí sería duplicar una garantía que ya existe para `quantity`. El caso de `menuId` nulo (que hoy probablemente cae en un 500 sin mapear, vía `IllegalArgumentException` de `MenuId`) queda anotado como hallazgo real pero fuera del alcance explícito de este ejercicio — se puede cerrar aparte si se decide.

## Verificación

1. **`MenuRouterConfigTest`** (extendido): un `Validator` real (`Validation.buildDefaultValidatorFactory().getValidator()`, no mockeado — mismo criterio que `SimpleMeterRegistry` en el Gap D: no es una frontera de infraestructura) confirma que postear un título en blanco devuelve 400 con `errorCode=VALIDATION_FAILED`, y que el caso de uso **nunca se llega a invocar**.
2. **`MenuWebIntegrationIT`** (extendido, por la verificación literal del roadmap): mismo caso, de punta a punta contra el stack real. No se pudo ejecutar en este sandbox (sin Docker) — queda pendiente confirmarlo en `mvn verify` local/CI.

**Verificado rompiendo a propósito:** se quitó momentáneamente la llamada a `requestValidator.validate(...)` en `createMenu` y se corrió el test — el título en blanco llegó hasta el mapper mockeado (sin stub), produciendo un **500** en vez del 400 esperado. Restaurado el fix, vuelve a pasar.

**Resultado final:** 75 tests verdes, PIT sin cambios (100%, 44/44 — los DTOs y el manejo de errores HTTP están fuera del alcance de PIT).
