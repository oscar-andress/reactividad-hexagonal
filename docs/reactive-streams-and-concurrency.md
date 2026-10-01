# Streams reactivos, `Sinks` y el silent-failure de concurrencia (Gap E, ejercicio 3)

Este doc cubre el ejercicio 3 del Gap E del roadmap (`C:\Users\oscar.vega\.claude\plans\parsed-kindling-pixel.md`): arreglar el manejo ignorado de `EmitResult` en `ReactorMenuEventPublisher.java`. A diferencia de `docs/immutability.md` (ejercicios 1-2, sobre inmutabilidad), este ejercicio es sobre **no ignorar el resultado de una operación que puede fallar**, en un contexto de concurrencia real. Sigue la misma estructura que el resto de `docs/`.

## Conceptos base: ¿qué es un stream reactivo?

Si vienes de Angular/RxJS, esto ya lo conocés con otro nombre: un `Observable` de RxJS **es** un stream reactivo. Project Reactor (lo que usa Spring WebFlux) es conceptualmente "RxJS para Java".

Un stream no es una lista en memoria — es una **secuencia de valores que llegan en el tiempo**, empujados hacia quien esté escuchando, uno por uno:

| | Array / `List` | Stream (`Flux`/`Mono`) |
|---|---|---|
| ¿Cuándo existen los datos? | Ya existen, todos, ahora | Van a existir, de a uno, en el futuro |
| ¿Cómo los leés? | Vos los buscás (`list.get(i)`) — *pull* | Te los empujan cuando llegan (`.subscribe(valor -> ...)`) — *push* |

`Mono<Menu>` = "va a llegar como máximo 1 `Menu` (o un error, o nada)". `Flux<Menu>` = "van a llegar 0, 1 o muchos `Menu`, uno tras otro".

## Streams fríos vs. calientes — la pieza que explica por qué existe `Sinks`

Todos los `Mono`/`Flux` que se ven en el resto del código (`findById`, `save`, etc.) son **fríos**: no pasa nada hasta que alguien se suscribe, y cada suscriptor dispara su propia ejecución independiente. Si dos requests llaman `getMenu(id)` al mismo tiempo, se disparan **dos** queries SQL distintas — es como un video de YouTube: no se reproduce hasta que apretás play, y cada quien lo ve desde el principio sin importar cuándo apretó.

Un stream **caliente** es distinto: el productor empuja eventos por su cuenta, pasen o no pasen suscriptores, y todos los que estén escuchando en ese momento reciben el mismo evento al mismo tiempo. Es como un canal de TV en vivo: si prendés la tele a la mitad de un partido, ves lo que está pasando *ahora*, no el arranque.

`streamMenus()` (el SSE de "avisame cuando cambie un menú") **tiene que ser caliente**: el evento "se creó un menú" ocurre una sola vez — no puede "volver a ejecutarse" por cada cliente SSE conectado.

## ¿Qué es un `Sink`?

Es el puente entre código imperativo normal y un stream caliente: el mecanismo para decir, desde un `if`/`return` de siempre, *"esto acaba de pasar — avisale a quien esté escuchando"*. Si conocés RxJS, es exactamente un `Subject`:

| RxJS (Angular) | Reactor (este proyecto) |
|---|---|
| `new Subject<Menu>()` | `Sinks.many().replay().limit(1)` |
| `subject.next(menu)` | `sink.emitNext(menu, ...)` |
| `subject.asObservable()` | `sink.asFlux()` |

## El flujo completo, de punta a punta

**1.** Algo pasa — `POST /menu`, `PUT /menu/{id}`, o subida de imagen. [MenuUseCasesService.java](src/main/java/demo/reactividad/application/usecase/MenuUseCasesService.java) termina llamando `.doOnNext(this.menuEventPublisher::publish)`.

**2.** El publisher empuja al sink ([ReactorMenuEventPublisher.java](src/main/java/demo/reactividad/infrastructure/adapter/out/event/ReactorMenuEventPublisher.java)):
```java
public void publish(Menu menu) {
    this.sink.emitNext(menu, ...);
}
```
`sink` es un campo de instancia de un `@Component` — Spring crea los `@Component` como **singleton por defecto**: una sola instancia de `ReactorMenuEventPublisher` para toda la vida de la aplicación, inyectada en todos lados. Por eso el `sink` es **uno solo, compartido por todas las llamadas HTTP** — si dos requests llegan en threads distintos (A y B) y ambos llaman `publish()`, ambos llaman sobre la misma instancia y el mismo `sink`. Esto es necesario (un stream caliente tiene que ser el mismo canal para todos los que escuchan), pero es justo lo que abre la puerta al problema de concurrencia de más abajo.

Nota sobre `final`: en `private final Sinks.Many<Menu> sink = ...`, el `final` solo impide **reasignar la referencia** después del constructor (nadie puede hacer `this.sink = otroSink` en otro método). No tiene nada que ver con que sea compartido ni con que sea thread-safe — eso viene de ser un singleton de Spring, no del `final`.

**3.** ¿Qué hace "escribir" exactamente? Dos cosas, en la misma llamada:
   - Actualiza el buffer interno del sink (el último valor emitido, ver `replay().limit(1)` abajo).
   - Recorre la lista de suscriptores **activos ahora mismo** y les llama `onNext(menu)` a cada uno, **sincrónicamente, en el mismo thread que llamó `publish()`** — no hay un thread propio del sink. Si hay 3 clientes SSE conectados, el thread que procesó el `POST /menu` ejecuta, en cadena, el `onNext` de los 3, antes de que `publish()` termine.

**4.** El lado de lectura ([MenuHandler.java](src/main/java/demo/reactividad/infrastructure/adapter/in/web/MenuHandler.java)):
```java
public Mono<ServerResponse> getMenuStream(ServerRequest request) {
    Flux<MenuResponseDTO> responseFlux = this.menuUseCases.streamMenus()   // sink.asFlux()
            .map(this.menuWebMapper::toResponseDTO);
    return ServerResponse.status(HttpStatus.OK)
            .contentType(MediaType.TEXT_EVENT_STREAM)   // Server-Sent Events
            .body(responseFlux, MenuResponseDTO.class);
}
```
Cada `GET /menu/stream` abre una conexión HTTP que nunca se cierra; cada valor que el sink empuja viaja por esa conexión como un chunk nuevo.

## El detalle de `replay().limit(1)`

`replay()` = "guardame un buffer de los últimos eventos, para que un suscriptor que llega tarde no se quede sin nada". `.limit(1)` = guardar solo el último. Efecto: un cliente que se conecta al SSE después de que ya se creó un menú recibe inmediatamente ese último menú (del buffer), y después sigue recibiendo todo en vivo.

## El problema real: el resultado de la escritura se ignoraba

```java
// Antes
public void publish(Menu menu) {
    this.sink.tryEmitNext(menu);   // el valor de retorno se descarta
}
```

`tryEmitNext(...)` no es un `void` — devuelve un `Sinks.EmitResult` que dice si la emisión falló y por qué. El fallo más probable acá es `FAIL_NON_SERIALIZED`: si el thread A está en medio del paso 3 (recorriendo suscriptores) y en ese instante el thread B también llama `tryEmitNext`, ambos tratan de tocar la misma estructura interna (buffer + lista de suscriptores) a la vez. El sink detecta el solapamiento y **rechaza** al segundo en vez de arriesgarse a corromper su estado (ej. un suscriptor recibiendo el evento dos veces, o el orden mezclado entre distintos suscriptores).

Al ignorar el resultado, ese rechazo era invisible: sin excepción, sin log, el evento desaparecía y ningún cliente SSE lo veía jamás.

## Verificamos la API real antes de escribir el fix

En vez de asumir cómo funciona `Sinks.EmitFailureHandler.busyLooping(...)`, se decompiló la clase real del jar (`javap -c` sobre `Sinks$OptimisticEmitFailureHandler`, la implementación interna detrás de `busyLooping`):

```java
public boolean onEmitFailure(SignalType signalType, EmitResult emitResult) {
    return emitResult.equals(EmitResult.FAIL_NON_SERIALIZED) && System.nanoTime() < this.deadline;
}
```

Confirmado: la función de decisión retorna `true` ("seguí reintentando") únicamente si el fallo es `FAIL_NON_SERIALIZED` **y** todavía no se agotó el presupuesto de tiempo. El bucle real (dentro de `emitNext`) es, conceptualmente:

```
mientras onEmitFailure(señal, resultado) devuelva true:
    volver a intentar tryEmitNext(menu)
    si tuvo éxito -> salir
```

Es un **busy loop**: reintenta tan rápido como la CPU lo permite, **sin dormir** entre intentos. Tiene sentido porque la ventana de colisión esperada es minúscula (el tiempo que tarda *otro* thread en terminar su propio `tryEmitNext`, del orden de nanosegundos) — dormir aunque sea 1ms sería desproporcionado. Para cualquier otro tipo de fallo (`FAIL_TERMINATED`, `FAIL_CANCELLED`) no reintenta, se rinde de inmediato. Si se agota el tiempo o el fallo no es reintentable, `emitNext(...)` lanza `Sinks.EmissionException` (una `IllegalStateException` con el `EmitResult` real adentro, vía `getReason()`).

## El fix

```java
private static final Duration CONCURRENT_EMIT_RETRY_BUDGET = Duration.ofMillis(200);

private final AtomicLong failedEmissionCount = new AtomicLong();

@Override
public void publish(Menu menu) {
    try {
        this.sink.emitNext(menu, Sinks.EmitFailureHandler.busyLooping(CONCURRENT_EMIT_RETRY_BUDGET));
    } catch (Sinks.EmissionException exception) {
        this.failedEmissionCount.incrementAndGet();
        log.error("Failed to publish menu event for menu {}: {}", menu.getId(), exception.getReason());
    }
}
```

`CONCURRENT_EMIT_RETRY_BUDGET` es solo el nombre elegido para una constante `Duration` de 200ms — no es sintaxis especial de Java, el nombre documenta para qué escenario existe (colisiones entre productores concurrentes). Los 200ms son el presupuesto de **peor caso**; en la práctica, dos threads que chocan se resuelven en el intento #2 o #3, microsegundos después.

`failedEmissionCount` es un `AtomicLong` (no un `long` normal) porque varios threads pueden incrementarlo a la vez — un `long` con `count++` tiene una condición de carrera real (dos threads leyendo el mismo valor viejo antes de que el otro escriba, perdiendo un incremento). `AtomicLong.incrementAndGet()` garantiza que cada incremento se aplique de forma atómica. Queda con visibilidad de paquete para que el test lo pueda verificar; en el Gap D (observabilidad) sería el candidato natural para convertirse en un contador de Micrometer real.

Esto resuelve la colisión más común (dos threads chocando, reintentos automáticos durante 200ms), y si de verdad falla de forma irrecuperable, ahora se loguea y se cuenta en vez de perderse en silencio.

## Verificación: el test detecta el bug real, no solo "pasa"

Se escribió `ReactorMenuEventPublisherTest.publish_FromManyThreadsConcurrently_NoEventIsSilentlyLost`: 200 menús publicados desde 16 threads en paralelo contra un único suscriptor, verificando que **los 200** lleguen y que `failedEmissionCount` quede en 0.

Siguiendo la disciplina de este proyecto (probar el fallo real, no asumir), se revirtió momentáneamente `publish(...)` a la versión vieja (`tryEmitNext` sin manejo) y se corrió el mismo test **5 veces**:

- Con el código viejo: **5/5 corridas fallaron** — el test detectó eventos perdidos de forma consistente (la colisión de 16 threads contra un sink no serializado no es un caso raro, pasa casi siempre bajo esa carga).
- Con el fix restaurado: **5/5 corridas pasaron**, sin flakiness.

Esta es la prueba de que el test no es un "test que compila" — realmente distingue el código correcto del bug real. `ReactorMenuEventPublisher` es infraestructura (fuera del alcance de PIT, que solo mutea `domain.*`/`application.*`), así que la verificación de este ejercicio se apoya en este test de concurrencia + el gate de JaCoCo (60% overall), no en mutation testing.

## Glosario corto

- **Stream frío**: no hace nada hasta que alguien se suscribe; cada suscriptor dispara su propia ejecución independiente (ej. `findById`).
- **Stream caliente**: el productor empuja eventos por su cuenta; todos los suscriptores activos reciben el mismo evento al mismo tiempo (ej. `streamMenus()`).
- **`Sink`**: puente entre código imperativo y un stream caliente — permite "empujar" un valor manualmente (`emitNext`) desde fuera del mundo reactivo.
- **`EmitResult`**: el resultado real de un intento de escritura a un sink — puede ser éxito (`OK`) o distintos tipos de fallo (`FAIL_NON_SERIALIZED`, `FAIL_OVERFLOW`, `FAIL_TERMINATED`, `FAIL_CANCELLED`).
- **`FAIL_NON_SERIALIZED`**: el sink detectó que dos productores intentaron escribirle al mismo tiempo, y rechazó a uno para no corromper su estado interno.
- **Busy loop**: un bucle de reintento que no duerme entre intentos — vuelve a intentar inmediatamente, apropiado cuando se espera que la condición que causó el fallo se resuelva casi instantáneamente.
