# Threat model (Gap B, ejercicio 1): pasada STRIDE

Este doc cubre el ejercicio 1 del Gap B del roadmap (`C:\Users\oscar.vega\.claude\plans\parsed-kindling-pixel.md`): un documento de scoping que identifica los riesgos de seguridad reales del código actual, y los mapea 1:1 a los ejercicios 2-4 que los cierran. Sigue la misma estructura que el resto de `docs/`.

## ¿Qué es STRIDE?

Un mnemónico (de Microsoft) para pensar sistemáticamente "¿qué puede salir mal?" en un sistema, cubriendo 6 categorías de amenaza:

| Letra | Amenaza | Pregunta que responde |
|---|---|---|
| **S** | Spoofing | ¿Alguien puede hacerse pasar por quien no es? |
| **T** | Tampering | ¿Alguien puede modificar datos que no debería poder tocar? |
| **R** | Repudiation | ¿Alguien puede negar haber hecho algo que sí hizo? |
| **I** | Information Disclosure | ¿Se expone información que no debería verse? |
| **D** | Denial of Service | ¿Alguien puede tirar abajo o saturar el sistema? |
| **E** | Elevation of Privilege | ¿Alguien puede conseguir más permisos de los que le dieron? |

No es una auditoría exhaustiva — es un checklist para no dejar un ángulo entero sin mirar, aplicado puntualmente a la superficie real de este proyecto (no hay `spring-boot-starter-security` ni ningún framework de seguridad — toda la "capa de seguridad" hoy son dos `WebFilter` a medida).

## Alcance revisado

Los dos filtros de seguridad (`AuthenticationWebFilter`, `AuthorizationWebFilter`), el path de upload (`MultipartFilePartExtractor`), los DTOs de request de `Menu` y `Order`, los routers y manejadores de excepciones de ambos contextos, y `pom.xml`/`application.properties` para confirmar qué hay (y qué no hay) configurado. Confirmado por `grep`: cero referencias a `SecurityWebFilterChain`, `@PreAuthorize`, CORS, CSP, CSRF o headers de seguridad en todo `src/main/java`.

## Hallazgos, por categoría STRIDE

### S — Spoofing: tokens estáticos y hardcodeados

```java
// AuthenticationWebFilter.java
private static final Map<String, AuthenticationCategory> AUTH_CATEGORY_MAP = Map.of(
    "secret123", AuthenticationCategory.STANDARD,
    "secret456", AuthenticationCategory.PRIME
);
```

Los "tokens" son dos strings fijos, escritos en el código fuente, que nunca expiran ni rotan. Cualquiera que vea el código (o el repo, o un dump de memoria, o los logs si algún día se loguean headers) puede hacerse pasar por un cliente `PRIME` para siempre — no hay forma de revocar un token sin recompilar y redesplegar la aplicación entera.

**→ Cierra con el ejercicio 3** (JWT firmado, de corta duración).

### T — Tampering: cero validación de entrada

```java
// MenuCreateRequestDTO.java / OrderCreateRequestDTO.java
public record MenuCreateRequestDTO(String menuTitle, String menuDescription) {}
```

Ningún campo de ningún DTO de request tiene restricciones — título vacío, descripción de longitud arbitraria, cantidad de pedido negativa (esto último sí lo frena `Order` en el dominio, con su propia validación — pero `Menu` no tiene ninguna, así que un título vacío llega sin filtro hasta la base, y falla con un error SQL crudo en vez de una respuesta 400 clara y controlada).

**→ Cierra con el ejercicio 2** (Bean Validation en los DTOs).

### D — Denial of Service: sin límites en upload ni en frecuencia de requests

```java
// MultipartFilePartExtractor.java
private Mono<UploadedFile> toUploadedFile(FilePart filePart) {
    return DataBufferUtils.join(filePart.content())
            .map(dataBuffer -> new UploadedFile(readAndReleaseBytes(dataBuffer), contentTypeOf(filePart)));
}
```

Dos hallazgos distintos, relacionados pero no iguales:

1. **Sin límite de tamaño de archivo**: `DataBufferUtils.join(...)` vuelca el archivo completo a un `byte[]` en memoria, sin ningún tope. Un solo archivo lo bastante grande puede saturar memoria.
2. **Sin límite de frecuencia de requests**: nada impide a un cliente disparar `POST /million` o `POST /{menuId}/image` sin parar — cada uno dispara trabajo real (escrituras a la base, subidas a S3).

**→ El hallazgo 2 cierra con el ejercicio 4** (`@RateLimiter` sobre esas dos rutas, explícitamente enmarcado como prevención de abuso/DoS).

**El hallazgo 1 queda anotado, no cerrado todavía** — no tiene un lugar natural en los ejercicios 2-4 tal como están definidos hoy (el ejercicio 2 es validación de *campos de DTO*, no del cuerpo multipart). El rate limiter del ejercicio 4 lo mitiga *parcialmente* (menos requests por ventana de tiempo = menos presión agregada), pero un solo archivo gigante dentro del límite de frecuencia sigue pudiendo causar un pico de memoria. Candidato real para un ejercicio futuro (ej. `maxInMemorySize` en la configuración de multipart de WebFlux, o un chequeo de `Content-Length` antes de leer el body).

### E — Elevation of Privilege: `PRIME` no tiene ningún límite

```java
// AuthorizationWebFilter.java
private Mono<Void> prime(ServerWebExchange exchange, WebFilterChain chain) {
    return chain.filter(exchange);   // sin ninguna restricción
}
```

El modelo de autorización es binario y grueso: `STANDARD` solo puede hacer `GET`, `PRIME` puede hacer *cualquier cosa*, sin granularidad por recurso ni por acción. Combinado con el hallazgo de Spoofing (el token de `PRIME` es `secret456`, estático, nunca revocable), quien obtenga ese token tiene control total y permanente sobre la API.

**→ Cierra, igual que Spoofing, con el ejercicio 3** — un JWT de corta duración no agrega granularidad por sí solo, pero acota el daño: un token comprometido deja de ser válido en minutos, no para siempre.

## Hallazgos identificados pero fuera de alcance de este gap

Por transparencia — encontrados durante la misma revisión, pero sin ningún ejercicio de este gap que los cierre, y fuera del alcance que se pidió para este gap puntual:

- **Repudiation**: no hay ID de correlación ni auditoría de "quién hizo qué" — no hay forma de probar o refutar que un cliente específico envió una request dada.
- **Information Disclosure (menor)**: `GlobalExceptionHandler`/`OrderExceptionHandler` exponen `ex.getMessage()` directo en la respuesta HTTP. Para las excepciones de dominio actuales el mensaje es controlado y seguro ("Menu with id X not found"), pero el patrón en sí es frágil — si alguna vez una excepción no mapeada con un mensaje interno llegara a propagarse, se filtraría tal cual al cliente.
- ~~**Credenciales en `application.properties`**~~ — cerrado, ver "Cierre posterior" al final del documento.

## Mapa de trazabilidad (hallazgo → ejercicio → verificación)

| Hallazgo | Categoría STRIDE | Ejercicio que lo cierra | Cómo se va a verificar |
|---|---|---|---|
| Tokens hardcodeados, no expiran | Spoofing | 3 | Test: token expirado/alterado/con firma incorrecta → 401 |
| `PRIME` sin ningún límite de autorización | Elevation of Privilege | 3 | Mismo test — el token válido debe seguir mapeando a la categoría correcta |
| DTOs sin validación de campos | Tampering | 2 | `MenuWebIntegrationIT`: postear título en blanco → 400 estructurado |
| Sin límite de frecuencia en `/million` y `/{menuId}/image` | Denial of Service | 4 | `MenuWebIntegrationIT`: ráfaga de requests → 429 pasado el umbral |
| Sin límite de tamaño en upload multipart | Denial of Service | *(sin cerrar — anotado arriba)* | — |
| Sin auditoría/correlación de requests | Repudiation | *(fuera de alcance de este gap)* | — |
| Mensajes de excepción expuestos directo en la respuesta | Information Disclosure | *(fuera de alcance de este gap)* | — |
| Credenciales en texto plano en `application.properties` | Information Disclosure | Cierre posterior (ver abajo) | Valores reemplazados por `${VAR:default-dev}`; se verifica leyendo el archivo — cero literales sensibles |

## Cierre posterior: credenciales fuera del repo

Cerrado fuera del Gap B original, como parte de una ruta de aprendizaje de seguridad.

**Decisión** (antes de tocar código): variables de entorno con default de solo-desarrollo (`${VAR:valor-dev}`), no un perfil separado ni un vault — resuelve el problema real (nada sensible en git) sin agregar infraestructura nueva. Se descartó un vault (ej. Spring Cloud Vault) por sobreingeniería para el alcance de este proyecto; se descartó un `application-local.properties` ignorado por git por ser un archivo más para mantener sin ganar nada que el placeholder no dé ya.

**Qué cambió:** `spring.r2dbc.username`/`password`, `aws.s3.access-key`/`secret-key` y `security.jwt.secret` pasaron de literales a `${NOMBRE_VARIABLE:default-de-desarrollo}`. El default después de `:` nunca es un secreto real — son los valores ya conocidos de Postgres/LocalStack en local. En cualquier entorno real, esas variables de entorno deben estar seteadas; si no lo están, la app sigue arrancando con el default de desarrollo, que es intencional para no romper el flujo local.

**Verificación:** `mvn test` sigue en verde sin cambios (los `*Test`/`*IT` no dependen de estos valores — Testcontainers sobreescribe la conexión real vía `@ServiceConnection`, y los tests de S3 mockean `S3AsyncClient`). No se verificó empíricamente en esta sesión que una variable de entorno real sobreescriba el placeholder — es comportamiento estándar de Spring Boot (`${VAR:default}`), no un mecanismo propio de este proyecto como sí lo son los proxies de resilience4j, así que no se consideró necesario reproducirlo aquí.
