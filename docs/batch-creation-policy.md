# SOLID + concurrencia explícita en `createMenus` (Gap E, ejercicio 4)

Este doc cubre el ejercicio 4, el último del Gap E del roadmap (`C:\Users\oscar.vega\.claude\plans\parsed-kindling-pixel.md`): extraer la política de batching/manejo de errores de `createMenus` a un colaborador inyectado (DIP), y hacer explícito el límite de concurrencia. Sigue la misma estructura que el resto de `docs/`.

## Los 3 problemas reales en el código original

```java
// MenuUseCasesService.createMenus — antes
public Flux<Menu> createMenus(Flux<Menu> menus) {
    return menus
            .doOnNext(menu -> log.info("Recieved {}", menu))
            .buffer(500)
            .flatMap(batch -> this.menuRepositoryPort.saveAll(batch)
                    .onErrorResume(error -> {
                        log.error("Error saving menus list: {}", batch);
                        return Flux.empty();          // (1)
                    }))                                // (2)
            .delayElements(Duration.ofSeconds(2));
}
```

1. **Error swallowing ciego.** Si `saveAll(batch)` falla, se loguea y se devuelve `Flux.empty()` — los 500 menús de ese batch desaparecen del stream de respuesta NDJSON sin que el cliente de `POST /million` tenga ninguna forma de saber que fallaron. Mismo patrón de fallo silencioso que ya se vio y arregló en `ReactorMenuEventPublisher` (`docs/reactive-streams-and-concurrency.md`).
2. **Concurrencia implícita, no decidida.** `.flatMap(batch -> ...)` sin especificar concurrencia usa el default de Reactor: **256**. Es decir, hasta 256 llamadas `saveAll` podían estar corriendo al mismo tiempo contra la base, sin que nadie lo hubiera decidido a propósito — un número capaz de agotar el pool de conexiones de R2DBC sin que se note hasta que pasa en producción.
3. **Política mezclada con orquestación.** El tamaño del batch y qué hacer si falla un batch estaban escritos en línea, adentro del caso de uso — sin poder testear esa política por separado ni cambiarla sin tocar `createMenus`.

## Decisiones de alcance

Dos decisiones se tomaron antes de escribir código, para no sobrediseñar (CLAUDE.md: evitar abstracciones prematuras):

- **Qué se extrae por DIP**: solo la política de **qué hacer cuando un batch falla** (`MenuBatchFailurePolicy`). El tamaño del batch (500) y el límite de concurrencia quedan como constantes nombradas dentro del caso de uso — ya son explícitas, no hace falta volverlas plegables sin un caso de uso real que lo necesite.
- **Mecanismo de concurrencia explícita**: el argumento de concurrencia de `flatMap`, no un `Scheduler` dedicado. Un `Scheduler` controla *en qué pool de threads* corre el trabajo; lo que acá hace falta es limitar *cuántos batches están "en vuelo" a la vez* — eso es exactamente lo que resuelve el segundo argumento de `flatMap`, sin bloquear ningún thread (las llamadas a R2DBC ya son no bloqueantes).

## El nuevo puerto (DIP)

```java
// application/port/out/MenuBatchFailurePolicy.java
public interface MenuBatchFailurePolicy {
    Flux<Menu> onBatchFailure(List<Menu> batch, Throwable error);
}
```

Implementación por defecto, en infraestructura (mismo patrón que `ReactorMenuEventPublisher`: loguear + contar, nunca perder el fallo en silencio):

```java
// infrastructure/adapter/out/batch/LoggingMenuBatchFailurePolicy.java
@Slf4j
@Component
public class LoggingMenuBatchFailurePolicy implements MenuBatchFailurePolicy {

    private final AtomicLong failedBatchCount = new AtomicLong();

    @Override
    public Flux<Menu> onBatchFailure(List<Menu> batch, Throwable error) {
        this.failedBatchCount.incrementAndGet();
        log.error("Failed to save a batch of {} menus: {}", batch.size(), error.getMessage(), error);
        return Flux.empty();
    }

    long getFailedBatchCount() {
        return this.failedBatchCount.get();
    }
}
```

`failedBatchCount` es un `AtomicLong` (no un `long` normal) porque varios batches pueden fallar en threads distintos al mismo tiempo — mismo motivo que en `ReactorMenuEventPublisher.failedEmissionCount`. Queda con visibilidad de paquete para que el test lo pueda leer; en el Gap D (observabilidad) sería el candidato natural para un contador de Micrometer real.

## El caso de uso, después

```java
private static final int CREATE_MENUS_BATCH_SIZE = 500;
private static final int CREATE_MENUS_BATCH_SAVE_CONCURRENCY = 8;

private final MenuBatchFailurePolicy menuBatchFailurePolicy;   // inyectado, DIP

@Override
public Flux<Menu> createMenus(Flux<Menu> menus) {
    return menus
            .doOnNext(menu -> log.info("Recieved {}", menu))
            .buffer(CREATE_MENUS_BATCH_SIZE)
            .flatMap(batch -> this.menuRepositoryPort.saveAll(batch)
                    .onErrorResume(error -> this.menuBatchFailurePolicy.onBatchFailure(batch, error)),
                    CREATE_MENUS_BATCH_SAVE_CONCURRENCY)
            .delayElements(Duration.ofSeconds(2));
}
```

`createMenus` ya no decide *qué hacer* ante un batch fallido — solo orquesta y delega esa decisión al colaborador inyectado. Y el límite de concurrencia ahora es un número nombrado y buscable (`CREATE_MENUS_BATCH_SAVE_CONCURRENCY = 8`), no un default escondido de la librería.

## Verificación: dos tests, cada uno probado contra su propio bug real

Siguiendo la disciplina de este proyecto (romper a propósito y confirmar, no asumir), cada test se corrió primero contra una versión deliberadamente revertida al bug que debía detectar.

### Test 1 — la delegación reemplaza el swallow ciego

```java
@Test
void createMenus_WhenBatchSaveFails_DelegatesToFailurePolicyInsteadOfSwallowingSilently() {
    Menu menu = aMenu().build();
    RuntimeException saveError = new RuntimeException("db down");
    when(this.menuRepositoryPort.saveAll(List.of(menu))).thenReturn(Flux.error(saveError));
    when(this.menuBatchFailurePolicy.onBatchFailure(List.of(menu), saveError)).thenReturn(Flux.empty());

    StepVerifier.withVirtualTime(() -> this.menuUseCasesService.createMenus(Flux.just(menu)))
            .thenAwait(Duration.ofSeconds(2))
            .expectComplete()
            .verify(Duration.ofSeconds(5));

    verify(this.menuRepositoryPort, times(1)).saveAll(any());
    verify(this.menuBatchFailurePolicy, times(1)).onBatchFailure(List.of(menu), saveError);
    verify(this.menuEventPublisher, never()).publish(any());
}
```

La aserción clave es `verify(this.menuBatchFailurePolicy, times(1)).onBatchFailure(...)`: por construcción, si `createMenus` volviera a manejar el error en línea (como antes) en vez de delegar, esta verificación fallaría con cero invocaciones — el mock nunca sería llamado.

### Test 2 — el límite de concurrencia se respeta

```java
@Test
void createMenus_LimitsHowManyBatchesAreSavedConcurrently() {
    int batchSize = 500;
    int batchCount = 9;   // > 8 (el límite configurado), para forzar que la saturación sea observable
    List<Menu> menus = IntStream.range(0, batchSize * batchCount)
            .mapToObj(i -> aMenu().build())
            .toList();

    AtomicInteger currentConcurrency = new AtomicInteger();
    AtomicInteger maxObservedConcurrency = new AtomicInteger();
    when(this.menuRepositoryPort.saveAll(any())).thenAnswer(invocation -> {
        List<Menu> batch = invocation.getArgument(0);
        int current = currentConcurrency.incrementAndGet();
        maxObservedConcurrency.accumulateAndGet(current, Math::max);
        return Mono.delay(Duration.ofMillis(100))
                .thenMany(Flux.fromIterable(batch))
                .doFinally(signalType -> currentConcurrency.decrementAndGet());
    });

    StepVerifier.withVirtualTime(() -> this.menuUseCasesService.createMenus(Flux.fromIterable(menus)))
            .thenAwait(Duration.ofSeconds(1))
            .thenCancel()
            .verify(Duration.ofSeconds(5));

    assertEquals(8, maxObservedConcurrency.get());
}
```

La idea: se envían **9 batches** (más que el límite de 8) y el mock de `saveAll` usa un contador (`currentConcurrency`) que se incrementa justo cuando `flatMap` decide arrancar un batch, y se decrementa cuando ese batch termina (100ms de delay virtual después). `maxObservedConcurrency` registra el pico más alto visto. Con el límite funcionando, nunca debería pasar de 8, aunque haya 9 batches esperando.

Se usa `StepVerifier.withVirtualTime` (no tiempo real) porque el `.delayElements(Duration.ofSeconds(2))` final de `createMenus` se aplica a cada uno de los 4500 menús resultantes — en tiempo real sería carísimo de esperar; con tiempo virtual, avanzar el reloj es instantáneo en wall-clock.

**Prueba de que el test detecta el bug real:** se quitó momentáneamente el segundo argumento de `flatMap` (volviendo al default oculto de Reactor) y se corrió el test:

```
expected: <8> but was: <9>
```

Con los 9 batches sin límite, los 9 corrieron concurrentemente — el test lo detectó de inmediato. Se restauró el fix y se confirmó que vuelve a pasar.

## Resultado final

- 45 tests, todos verdes (`mvn test` + gates de JaCoCo).
- PIT: **100%** (33/33 mutantes muertos) — el nuevo colaborador y el cambio de concurrencia no abrieron ningún hueco nuevo en `domain`/`application`.
- Con esto se cierran los 4 ejercicios del Gap E (SOLID / Trade-offs / Concurrencia e Inmutabilidad): `Menu` inmutable, `FoodType` inmutable, el silent-failure de `ReactorMenuEventPublisher`, y esta política de batching explícita.
