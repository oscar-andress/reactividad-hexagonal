# Rate limiting como control de seguridad (Gap B, ejercicio 4)

Este doc cubre el último ejercicio del Gap B del roadmap (`C:\Users\oscar.vega\.claude\plans\parsed-kindling-pixel.md`): cierra el hallazgo de **Denial of Service** del threat model (`docs/security/threat-model.md`) — nada impedía a un cliente disparar `POST /million` o `POST /{menuId}/image` sin parar, cada uno con trabajo real detrás (escrituras masivas a la base, subidas a S3).

## El fix

Se reutilizó exactamente la misma infraestructura de resilience4j que ya montamos en el Gap D (`resilience4j-spring-boot3` + `aspectjweaver`, ya en el `pom.xml` — no hizo falta agregar nada nuevo):

```java
// MenuHandler.java
@RateLimiter(name = "create-menus-bulk")
public Mono<ServerResponse> create1MillionMenu(ServerRequest request) { ... }

@RateLimiter(name = "menu-image-upload")
public Mono<ServerResponse> uploadMenuImage(ServerRequest request) { ... }
```

```properties
resilience4j.ratelimiter.instances.create-menus-bulk.limit-for-period=5
resilience4j.ratelimiter.instances.create-menus-bulk.limit-refresh-period=1s
resilience4j.ratelimiter.instances.create-menus-bulk.timeout-duration=0

resilience4j.ratelimiter.instances.menu-image-upload.limit-for-period=5
resilience4j.ratelimiter.instances.menu-image-upload.limit-refresh-period=1s
resilience4j.ratelimiter.instances.menu-image-upload.timeout-duration=0
```

Dos instancias separadas, no una compartida — son dos rutas con perfiles de costo distintos (una crea muchos menús de una vez vía streaming NDJSON, la otra sube un archivo); que una se agote no debería afectar a la otra. `timeout-duration=0` significa lo mismo que ya vimos con `@Bulkhead` en el Gap D: la llamada de más se rechaza al instante, sin esperar a que se libere un cupo.

El rechazo lanza `RequestNotPermitted` (de resilience4j), mapeado a **HTTP 429** en `GlobalExceptionHandler` y registrado en `MenuRouterConfig`, siguiendo el mismo patrón que ya existe para los demás `.onError(...)`.

## Un hallazgo real durante la verificación: el primer test era frágil

El primer intento de test disparaba 6 llamadas **secuenciales** (una tras otra) y esperaba que al menos una volviera con 429. Pasaba en aislamiento, pero **falló al correr junto al resto del suite** — no por un bug en el rate limiter, sino porque el test dependía de que las 6 llamadas terminaran dentro del mismo segundo de reloj. Bajo más carga en la máquina (corriendo decenas de otros tests), cada llamada tardó un poco más, y para cuando llegó la 6ta, la ventana de 1 segundo ya se había refrescado — las 6 pasaron con 200.

**El fix:** disparar las 6 llamadas **concurrentemente** (en paralelo, vía `ExecutorService` + `CompletableFuture`, mismo patrón ya usado en `ReactorMenuEventPublisherTest` del Gap E), no en secuencia. Así el tiempo total no depende de cuántas llamadas haya ni de qué tan ocupada esté la máquina — todas compiten por el mismo cupo casi al mismo instante, que es además una simulación más fiel de lo que sería un abuso/DoS real.

```java
List<CompletableFuture<HttpStatusCode>> futures = IntStream.range(0, BURST_SIZE)
        .mapToObj(i -> CompletableFuture.supplyAsync(request::get, executor))
        .toList();
List<HttpStatusCode> statuses = futures.stream().map(CompletableFuture::join).toList();
```

Confirmado: 3 corridas en aislamiento + 1 corrida junto al suite completo (87 tests), todas estables.

## Verificación

1. **`MenuRateLimiterTest`** (nuevo, `@SpringBootTest` + `@MockitoBean` sobre `MenuUseCases` — mismo motivo de siempre: `@RateLimiter` solo actúa vía proxy AOP): dos tests, uno por ruta, cada uno disparando una ráfaga de 6 llamadas concurrentes (más que el límite de 5) y confirmando que al menos una vuelve con 429.
2. **`MenuWebIntegrationIT`** (extendido, por la verificación literal del roadmap): mismo caso para `/million`, de punta a punta contra el stack real. No se pudo ejecutar en este sandbox (sin Docker) — queda pendiente confirmarlo en `mvn verify` local/CI.

**Verificado rompiendo a propósito:** se quitó momentáneamente `@RateLimiter` de `uploadMenuImage` y se corrió el test — las 6 llamadas volvieron con 200, ninguna rechazada. Restaurado el fix, vuelve a pasar.

**Resultado final:** 87 tests verdes, PIT sin cambios (100%, 44/44 — la capa web está fuera del alcance de PIT). **Con esto se cierran los 4 ejercicios del Gap B (Seguridad): threat model, Bean Validation, JWT, rate limiting.**
