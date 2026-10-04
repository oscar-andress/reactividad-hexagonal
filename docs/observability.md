# Observabilidad orientada a negocio (Gap D, ejercicio 4)

Este doc cubre el último ejercicio del Gap D del roadmap (`C:\Users\oscar.vega\.claude\plans\parsed-kindling-pixel.md`): agregar contadores de negocio reales con Micrometer, en dos eventos que ya existían en el código pero que hasta ahora solo se podían ver leyendo logs. Sigue la misma estructura que el resto de `docs/`.

## El concepto: contadores de negocio, no solo métricas técnicas

Hasta ahora, lo único que teníamos para saber "¿cuántas veces pasó X?" era buscar en los logs. Eso funciona para debuggear un caso puntual, pero no sirve para responder preguntas operativas en tiempo real como *"¿cuántas imágenes huérfanas se generaron esta hora?"* o *"¿está empeorando la tasa de fallos de batch?"* sin tener que grepear logs manualmente.

Un **contador de Micrometer** es, ante todo, un número con nombre que vive en memoria, que cualquiera puede incrementar desde el código, y que queda disponible para consultarse — vía `/actuator/metrics/<nombre>` hoy, o exportado a un sistema como Prometheus más adelante (no hicimos ese paso: `spring-boot-starter-actuator`, ya presente en el proyecto, trae Micrometer y un registro en memoria por defecto, suficiente para lo que pide este ejercicio).

La diferencia clave con un log: un log es un evento puntual, pensado para leer un caso específico. Un contador es un **acumulado**, pensado para preguntarte "¿cuánto pasó esto en total?" sin tener que contar líneas de log a mano.

## Dónde se agregaron los dos contadores

### 1. Limpieza de imagen huérfana (`MenuUseCasesService.compensateImageUpload`)

Este método ya existía (es la compensación que corre cuando subís una imagen a S3, pero después falla el `save` del menú en la base — hay que borrar la imagen que quedó "huérfana"). Antes, lo único que pasaba si la limpieza en sí fallaba era un `log.error`. Ahora:

```java
private Mono<Void> compensateImageUpload(String imageKey) {
    this.meterRegistry.counter("menu.image.orphan_cleanup.attempts").increment();
    return this.imageStoragePort.delete(imageKey)
            .onErrorResume(deleteError -> {
                this.meterRegistry.counter("menu.image.orphan_cleanup.failures").increment();
                log.error("Failed to delete orphaned image {} after a failed save", imageKey, deleteError);
                return Mono.empty();
            });
}
```

Dos contadores, no uno, porque son dos preguntas de negocio distintas:
- **`attempts`**: ¿cuántas veces un `save` falló lo suficientemente mal como para necesitar limpiar una imagen? (señal de salud de la base de datos / del flujo de subida).
- **`failures`**: de esas veces, ¿cuántas la limpieza en sí también falló? — el peor caso: una imagen que quedó huérfana de verdad, sin que nadie la borre, y que necesita intervención manual. Esta es la señal más urgente de las dos.

### 2. Fallo de batch al crear menús (`LoggingMenuBatchFailurePolicy`)

Esta clase ya la habíamos señalado como candidata perfecta desde el Gap E (ver `docs/batch-creation-policy.md`) — tenía un `AtomicLong failedBatchCount` con un getter de visibilidad de paquete que **ningún test usaba nunca** (lo confirmamos con `grep` antes de tocar nada). Se reemplazó directamente por un contador real:

```java
@Override
public Flux<Menu> onBatchFailure(List<Menu> batch, Throwable error) {
    this.meterRegistry.counter("menu.batch_save.failures").increment();
    log.error("Failed to save a batch of {} menus: {}", batch.size(), error.getMessage(), error);
    return Flux.empty();
}
```

## Exponer el endpoint

Por defecto, Spring Boot Actuator solo expone `/actuator/health` sobre HTTP — `/actuator/metrics` existe internamente pero no es alcanzable desde afuera hasta que se declare explícitamente:

```properties
management.endpoints.web.exposure.include=health,metrics
```

## Un detalle real de Micrometer que encontramos al escribir el test

Un contador **no existe hasta que algo lo incrementa por primera vez** — `meterRegistry.counter(nombre)` lo registra en ese momento, no antes. El primer intento de test escribió:

```java
// Esto rompe si "failures" nunca se incrementó en este test puntual:
assertEquals(0, this.meterRegistry.get("menu.image.orphan_cleanup.failures").counter().count());
```

Y efectivamente rompió, con `MeterNotFoundException` — en el test donde la limpieza de la imagen *sí* tiene éxito, el contador `failures` nunca se toca, así que **no existe todavía**, ni siquiera en cero. El fix correcto no es un valor "cero" sino comprobar que el contador *no se haya creado*:

```java
assertEquals(0, this.meterRegistry.find("menu.image.orphan_cleanup.failures").counters().size());
```

`.get(nombre)` exige que el meter ya exista (lanza si no); `.find(nombre)` es la búsqueda tolerante — devuelve una colección vacía si nadie lo tocó todavía.

## Verificación

1. **`MenuUseCasesServiceTest`** (extendido): en el test donde la limpieza tiene éxito, confirma `attempts=1` y que `failures` ni se registró. En el test donde la limpieza también falla, confirma `attempts=1` y `failures=1`.
2. **`LoggingMenuBatchFailurePolicyTest`** (nuevo — esta clase nunca tuvo test propio): confirma que el contador se incrementa en cada llamada, y que acumula correctamente en llamadas sucesivas.
3. **El camino completo, de punta a punta** (`LoggingMenuBatchFailurePolicyMetricsEndpointTest`): llama al bean real una vez, y confirma que **el mismo número aparece reflejado en `GET /actuator/metrics/menu.batch_save.failures`** — la prueba de que el contador no es solo un objeto en un test aislado, sino que está conectado al registro real que expone la aplicación.

   ```java
   @AutoConfigureWebTestClient
   @SpringBootTest
   class LoggingMenuBatchFailurePolicyMetricsEndpointTest {
       @Autowired private LoggingMenuBatchFailurePolicy loggingMenuBatchFailurePolicy;
       @Autowired private WebTestClient webTestClient;

       @Test
       void afterABatchFailure_ActuatorMetricsReflectsTheRealCounter() {
           this.loggingMenuBatchFailurePolicy.onBatchFailure(List.of(aMenu().build()), new RuntimeException("db down"))
                   .blockLast();

           this.webTestClient.get()
                   .uri("/actuator/metrics/menu.batch_save.failures")
                   .header(AUTH_HEADER, PRIME_TOKEN)   // los filtros de seguridad son globales, aplican también a /actuator
                   .exchange()
                   .expectStatus().is2xxSuccessful()
                   .expectBody()
                   .jsonPath("$.measurements[0].value").isEqualTo(1.0);
       }
   }
   ```

   Nota de clasificación: el roadmap describe este test como "un `*IT`", pero no necesita Docker/Testcontainers (solo un bean real de Spring + el endpoint de actuator) — por la convención real de `CLAUDE.md` (el sufijo depende de si hace falta Docker, no de si hace falta un contexto de Spring), se dejó como `*Test`.

**Verificado rompiendo a propósito:** se quitó momentáneamente el `.increment()` de ambos contadores y se corrieron los tests — los 4 que dependían de esos contadores fallaron con `MeterNotFoundException`, confirmando que realmente están probando la métrica real y no un valor cacheado. Restaurado el fix, vuelven a pasar.

**Resultado final:** 74 tests verdes, PIT en 100% (44/44). Con esto se cierran los 4 ejercicios del Gap D (Resiliencia Avanzada).
