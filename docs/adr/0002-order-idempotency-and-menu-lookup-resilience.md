# ADR 0002: idempotencia de `POST /api/v1/order` y resiliencia completa de `MenuLookupPortAdapter`

Ejercicio de la Fase 3 del learning path (E4: prever qué pasa cuando el otro lado no responde). Cubre la tabla de fallos completa que pide el ejercicio para dos puntos del flujo de creación de un pedido: el propio endpoint (¿qué pasa si el cliente reintenta la request completa?) y la llamada de `Orders` hacia `Menu` (¿qué pasa si `Menu` tarda o falla?).

## Decisión 1: clave de idempotencia como header, no como ID generado por el cliente

**Contexto.** Hoy `POST /api/v1/order` crea un pedido nuevo en cada llamada. Si el cliente reintenta (timeout de red, no sabe si la primera llamada llegó a procesarse), el segundo intento duplica el pedido — no hay ninguna forma de que el servidor reconozca "esta ya la procesé".

**Opción A — header `Idempotency-Key` (elegida).** El cliente genera un string opaco y lo manda en un header; el body sigue siendo solo datos de negocio (`menuId`, `quantity`). El `order_id` sigue generándolo el servidor, como hoy.
- Pros: separa la identidad del recurso (quién es este pedido) de la idempotencia de la operación (es la misma solicitud repetida); es el patrón estándar de la industria (Stripe y similares); no cambia el contrato de qué asigna el `orderId`.
- Contras: agrega un concepto nuevo (lectura de headers) al handler, que hoy no lee ninguno.

**Opción B — `orderId` generado por el cliente en el body.** El cliente elige el UUID del pedido; ese mismo UUID actúa como clave de idempotencia (conflicto de PK como mecanismo de dedup).
- Pros: no agrega columna nueva, reusa la PK existente.
- Contras: cambia el contrato público — hoy el servidor asigna el ID del recurso en una operación de creación (`POST`), delegarlo al cliente es un cambio semántico mayor, no solo una adición.

**Decisión:** Opción A. Consultada explícitamente con el usuario antes de implementar.

**Consecuencias aceptadas:**
- Nueva columna `order_idempotency_key` (nullable) + índice único parcial (`WHERE order_idempotency_key IS NOT NULL`) — permite múltiples pedidos sin clave (comportamiento actual, sin romper nada) y a la vez fuerza unicidad real cuando sí se manda.
- La deduplicación real tiene que sobrevivir a una carrera (dos requests concurrentes con la misma clave) — un `SELECT` antes del `INSERT` no alcanza por sí solo (ventana de carrera entre el `SELECT` y el `INSERT`); la fuente de verdad es la constraint única de la base, con un `catch` de `DuplicateKeyException` + re-lectura como camino de "ya existe" para el perdedor de la carrera.
- Se devuelve siempre `201 Created` (incluso en una repetición) — se decidió explícitamente no distinguir `201` (creado) de `200` (ya existía) para no agregar una rama de estado adicional que el ejercicio no pide; el contrato que importa (el cliente recibe el recurso canónico, nunca un duplicado) queda cumplido igual.

## Decisión 2: `MenuLookupPortAdapter` gana `@Retry` + `@TimeLimiter`, sobre el `@Bulkhead` que ya tenía

**Contexto.** `MenuLookupPortAdapter.findMenuSnapshot` solo tiene `@Bulkhead` (ver `docs/resilience.md`, ejercicio 2) — nada limita cuánto puede tardar una llamada lenta, y un fallo transitorio no se reintenta nunca.

**Opción A — agregar `@Retry` + `@TimeLimiter` (elegida), con traducción de fallos en la capa de aplicación.** Mismo patrón ya usado en `ResilientMenuRepositoryAdapter` (`@Retry`, con `Mono.defer(...)` obligatorio) y en `S3ImageStorageAdapter` (`@TimeLimiter`, sin `defer`). El error final (timeout agotado, reintentos agotados, o bulkhead lleno) se traduce en `OrderUseCasesService` a una excepción propia de `Orders` (`MenuLookupUnavailableException` → 503), nunca se deja pasar sin traducir.

**Opción B — agregar también `@CircuitBreaker`.** El ejercicio no lo pide explícitamente (a diferencia de `menu-service-reactivo`, que sí lo tiene) — agregarlo ahora sería alcance no solicitado (CLAUDE.md: evitar funcionalidad no solicitada). Se descarta por ahora; si se repite el patrón de "Menu viene fallando mucho, dejemos de llamarlo un rato" como problema real, es un ejercicio aparte.

**Decisión:** Opción A.

**Por qué "no existe" nunca se reintenta, sin configuración especial de `@Retry`:** `findMenuSnapshot` ya traduce `MenuNotFoundException` a `Mono.empty()` **dentro** del propio método, antes de que la llamada llegue a los aspectos de resilience4j. Para cuando `@Retry` evalúa si hay un error que reintentar, no hay ningún error — la llamada ya terminó con éxito (vacío). No hace falta decirle a `@Retry` qué excepciones ignorar: el caso ya se resolvió una capa más adentro.

**Tabla de fallos (criterio de "hecho" del ejercicio):**

| Escenario | Mecanismo | Config | Qué recibe el usuario | Test |
|---|---|---|---|---|
| `Menu` tarda más de lo razonable | `@TimeLimiter` | `timeout-duration=2s`, `cancel-running-future=true` | Si el timeout se agota en todos los reintentos: `503` (`MenuLookupUnavailableException`) | `MenuLookupPortAdapterResilienceTest` con `Mono.never()` |
| `Menu` falla de forma transitoria | `@Retry` | `max-attempts=3`, backoff exponencial desde 50ms | Si se recupera dentro de los 3 intentos: éxito normal (`201`). Si no: `503` | `MenuLookupPortAdapterResilienceTest` con el mock fallando 2 veces y respondiendo bien a la 3ra |
| `Menu` dice que el menú no existe | Traducción ya existente (`Mono.empty()`), **sin** reintento | — | `404` (`MenuNotFoundForOrderException`), igual que hoy | Extensión de `OrderUseCasesServiceTest`: el mock se llama exactamente 1 vez aunque `@Retry` esté activo |
| El bulkhead de `orders-menu-lookup` está lleno | `@Bulkhead` (ya existía) | `max-concurrent-calls=3` | Antes: error crudo sin traducir. Ahora: `503` (`MenuLookupUnavailableException`), mismo código que los otros fallos de disponibilidad | Extensión de `MenuLookupPortAdapterResilienceTest` |
| El cliente reintiene la request completa (duplicado) | `Idempotency-Key` (Decisión 1) | índice único parcial + `catch` de `DuplicateKeyException` | `201` con el **mismo** pedido, nunca un segundo registro | `OrderUseCasesServiceTest` (secuencial) + `OrderWebIntegrationIT` (concurrente, contra Postgres real) |

Verificación de ambas decisiones: rompiendo a propósito cada mecanismo nuevo (quitar `@Retry`/`@TimeLimiter`, quitar el `catch` de `DuplicateKeyException`) y confirmando que el test correspondiente efectivamente falla, antes de restaurar — misma disciplina que el resto del proyecto (`CLAUDE.md`, Sección 2).

## Verificación final contra Postgres real

El `catch` de `DuplicateKeyException` se escribió asumiendo que el driver R2DBC de Postgres traduce una violación del índice único a ese tipo concreto de excepción de Spring Data — una suposición razonable (es el tipo estándar para esto), pero una suposición igual, solo confirmada con un mock en `OrderUseCasesServiceTest`. Corriendo `mvn verify` (con Docker real vía WSL, `DOCKER_HOST=tcp://localhost:2375`, dato que también quedó documentado como hallazgo de entorno), se confirmó contra la base real:

- `OrderR2dbcRepositoryIT.save_WhenIdempotencyKeyAlreadyExistsOnAnotherRow_FailsWithDuplicateKeyException`: un segundo `INSERT` con la misma `Idempotency-Key` sí falla con `DuplicateKeyException` real, no un tipo distinto — la suposición era correcta.
- `OrderWebIntegrationIT.postOrder_WithSameIdempotencyKeyConcurrently_CreatesOnlyOneOrder`: 8 requests HTTP concurrentes con la misma `Idempotency-Key` contra el stack completo (router, caso de uso, Postgres real) terminan en exactamente **una** fila, y las 8 respuestas apuntan al mismo `orderId`.
- `OrderWebIntegrationIT.postOrder_WithSameIdempotencyKeyTwiceSequentially_CreatesOnlyOneOrder`: el caso más simple (reintento secuencial del cliente) también confirmado de punta a punta.

Resultado: 127 tests unitarios (`mvn test`) + 30 de integración (`mvn verify`), todos en verde.
