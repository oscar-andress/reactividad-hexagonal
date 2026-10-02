# Resiliencia avanzada (Gap D, ejercicios 1-3)

Este doc cubre los ejercicios 1, 2 y 3 del Gap D del roadmap (`C:\Users\oscar.vega\.claude\plans\parsed-kindling-pixel.md`): decorar `save`/`deleteById`/`saveAll` de `ResilientMenuRepositoryAdapter` (ejercicio 1), aislar las llamadas de `Orders` hacia `Menu` en `MenuLookupPortAdapter` (ejercicio 2) y poner un límite de tiempo a `S3ImageStorageAdapter` (ejercicio 3). En el camino del ejercicio 1 se encontró y arregló un bug real preexistente: **`@CircuitBreaker` nunca había funcionado**. Sigue la misma estructura que el resto de `docs/`.

## El concepto: las anotaciones de resilience4j necesitan un proxy de Spring AOP

`@Retry`, `@Bulkhead`, `@CircuitBreaker` (las de `io.github.resilience4j.*.annotation`) no son código que se ejecuta directamente — son **metadatos que un aspecto de Spring AOP intercepta**, envolviendo la llamada real con la lógica correspondiente. Eso solo pasa si el objeto es un **bean gestionado por Spring**, accedido a través de su proxy — nunca si se construye a mano (`new ResilientMenuRepositoryAdapter(mock)`, que es exactamente lo que hace `ResilientMenuRepositoryAdapterTest.java`). Por eso ese test, aunque siempre pasó, nunca probó que la resiliencia real funcionara — solo probaba la delegación y el método de fallback como función aislada.

## ¿Qué es un Bulkhead?

El nombre viene de los barcos: un **bulkhead** es un mamparo — una pared estanca que divide el casco en compartimentos. Si uno se inunda, el agua no pasa a los demás y el barco no se hunde entero. El patrón de resiliencia toma ese nombre prestado: **limita cuántas llamadas concurrentes puede haber hacia un recurso**, para que si ese recurso se atasca, no arrastre con él a todo lo demás.

### El problema que resuelve

Sin bulkhead, si `save()` empieza a tardar (la base de datos está lenta, hay un problema de red), nada le impide a cientos de llamadas concurrentes quedarse todas esperando a la vez — cada una consumiendo un hilo, una conexión del pool, memoria. Eso no solo no ayuda: empeora el problema, porque ahora la base de datos lenta tiene que lidiar con aún más carga encima, y el resto de la aplicación se queda sin recursos para hacer cualquier otra cosa. Es el efecto cascada clásico: un problema chico en un lugar tira abajo todo el sistema.

El bulkhead pone un tope: **"como máximo, dejo que N llamadas a `save()` estén en vuelo al mismo tiempo — la N+1 se rechaza de inmediato, en vez de hacerla esperar."**

### Cómo lo configuramos

```properties
resilience4j.bulkhead.instances.menu-service-reactivo.max-concurrent-calls=4
resilience4j.bulkhead.instances.menu-service-reactivo.max-wait-duration=0
```

- `max-concurrent-calls=4`: como máximo 4 llamadas a la vez usando ese "carril".
- `max-wait-duration=0`: la 5ta llamada **no espera nada** a que se libere un lugar — se rechaza instantáneamente con `BulkheadFullException`. (Si pusiéramos, por ejemplo, `500ms`, la 5ta llamada esperaría hasta medio segundo a que se libere un permiso antes de rendirse — una variante más tolerante, pero que sigue dejando esperar, solo que con un límite.)

### Lo que confirma el test real

```java
// Ocupamos los 4 permisos a propósito, con llamadas que nunca terminan
for (int i = 0; i < 4; i++) {
    this.menuRepositoryPort.save(menu).subscribe();
}

// La 5ta se rechaza de inmediato, sin siquiera intentar llegar a la base de datos
this.menuRepositoryPort.save(menu)
        .as(StepVerifier::create)
        .expectError(BulkheadFullException.class)
        .verify(Duration.ofSeconds(5));
```

Esto confirma la propiedad clave: la 5ta llamada **ni siquiera llega a tocar la base de datos** — se rechaza en el borde, antes de agregar más presión a un recurso que ya está saturado.

### Cómo se relaciona con `@Retry` y `@CircuitBreaker`

Los tres atacan problemas distintos, y por eso frecuentemente se usan juntos:

| Patrón | Pregunta que responde |
|---|---|
| `@Retry` | "¿Este fallo fue pasajero? Intentemos de nuevo." |
| `@CircuitBreaker` | "¿Este dependiente viene fallando mucho? Dejemos de llamarlo un rato." |
| `@Bulkhead` | "¿Cuántas llamadas simultáneas le permito a este recurso, sin importar si fallan o no?" |

`Bulkhead` no le importa si las llamadas tienen éxito o fallan — solo cuenta cuántas están abiertas *al mismo tiempo*, en cualquier momento. Es la única de las tres que protege contra saturación por volumen, no contra fallos.

## Hallazgo 1: faltaba la dependencia que de verdad procesa las anotaciones

El proyecto ya tenía `spring-cloud-starter-circuitbreaker-reactor-resilience4j` y `@CircuitBreaker` parecía "andar" (compilaba, el test de la función de fallback pasaba). Se verificó empíricamente si de verdad interceptaba algo, autowireando el bean real (`MenuRepositoryPort`, que resuelve al `@Primary` `ResilientMenuRepositoryAdapter`) con el delegate mockeado para fallar:

```java
@SpringBootTest
class CircuitBreakerDiagnosticTest {
    @MockitoBean private MenuRepositoryAdapter delegate;
    @Autowired private MenuRepositoryPort menuRepositoryPort;

    @Test
    void findById_WhenDelegateFails_SeesWhatReallyHappens() {
        when(this.delegate.findById(id)).thenReturn(Mono.error(new RuntimeException("db down")));
        this.menuRepositoryPort.findById(id)
                .as(StepVerifier::create)
                .expectErrorSatisfies(error -> System.out.println("CLASE REAL: " + error.getClass()))
                .verify();
    }
}
```

Resultado: `CLASE REAL: class java.lang.RuntimeException` — el error crudo, sin traducir. `fallbackFindById` (que debería convertirlo en `MenuUnavailableException`) **nunca se disparaba**. El circuit breaker llevaba tiempo siendo, literalmente, código muerto.

**Causa raíz, confirmada leyendo el POM real de la dependencia** (no asumida):

```
spring-cloud-starter-circuitbreaker-reactor-resilience4j
  └─ spring-cloud-circuitbreaker-resilience4j   (solo el CircuitBreakerFactory funcional de Spring Cloud)
  └─ resilience4j-circuitbreaker, resilience4j-timelimiter, resilience4j-reactor
```

Ese starter trae la integración de **Spring Cloud** (su propio `ReactiveCircuitBreakerFactory`, un estilo funcional: `factory.create("x").run(mono, fallback)`), **no** el módulo que registra los aspectos de Spring AOP para las anotaciones nativas de resilience4j (`@CircuitBreaker`, `@Retry`, `@Bulkhead`...). Ese módulo es **`resilience4j-spring-boot3`**, y no estaba en el `pom.xml` ni como dependencia directa ni transitiva.

Se agregó explícitamente:

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.3.0</version>
</dependency>
```

## Hallazgo 2: con eso solo, seguía sin funcionar — faltaba AspectJ

Tras agregar `resilience4j-spring-boot3`, el diagnóstico seguía igual. Las clases `@Aspect` de ese módulo (`RetryAspect`, `BulkheadAspect`, `CircuitBreakerAspect`) usan la anotación `org.aspectj.lang.annotation.Aspect` — sin esa clase en el classpath, Spring ni siquiera puede reconocerlas como aspectos. El proyecto no tenía `spring-boot-starter-aop` ni `aspectjweaver` en ninguna parte. Se agregó:

```xml
<dependency>
    <groupId>org.aspectj</groupId>
    <artifactId>aspectjweaver</artifactId>
    <version>1.9.25.1</version>
</dependency>
```

Con las dos dependencias, el stack trace de un test que fuerza un fallo ya muestra el proxy real actuando:

```
...CglibAopProxy$DynamicAdvisedInterceptor.intercept
demo.reactividad.infrastructure.adapter.out.persistence.ResilientMenuRepositoryAdapter$$SpringCGLIB$$0.save(<generated>)
io.github.resilience4j.spring6.retry.configure.RetryAspect.retryAroundAdvice
io.github.resilience4j.spring6.retry.configure.ReactorRetryAspectExt.handle
```

## Hallazgo 3: con los aspectos ya activos, `@Bulkhead` funcionaba — `@Retry` todavía no

Con ambas dependencias, el test de `@Bulkhead` pasó a la primera. El de `@Retry` seguía fallando, siempre en el primer intento, sin reintentar nunca — pero ahora el aspecto sí corría (aparecía en el stack trace). La causa:

```java
// ANTES — no retentable de verdad
public Mono<Menu> save(Menu menu) {
    return this.delegate.save(menu);
}
```

`this.delegate.save(menu)` se **evalúa una sola vez**, en el momento en que el aspecto llama a `proceed()` — produce un único `Mono` ya resuelto (en este caso, un `Mono.error(...)` fijo). El aspecto de retry sí intenta reintentar, pero como no tiene forma de "volver a ejecutar" la llamada original, solo puede volver a suscribirse al **mismo** `Mono.error` ya construido — que emite el mismo error de inmediato, sin que `delegate.save(...)` se vuelva a invocar nunca. Se verificó esto de forma aislada, sin AOP de por medio, usando el `RetryOperator` de resilience4j manualmente:

```java
// Con Mono.defer: SÍ reintenta de verdad (confirmado: "INTENTO 1", "INTENTO 2", "INTENTO 3")
Mono<String> decorated = Mono.defer(() -> { attempts.incrementAndGet(); ... })
        .transformDeferred(RetryOperator.of(retry));

// Sin Mono.defer (un Mono.error ya fijo): el retry se agota reintentando el mismo resultado,
// la llamada real nunca se repite.
```

**El fix**: envolver la llamada al delegate en `Mono.defer(...)`/`Flux.defer(...)`, para que cada intento de verdad vuelva a invocar la operación real:

```java
@Retry(name = RESILIENCE_INSTANCE_NAME)
@Bulkhead(name = RESILIENCE_INSTANCE_NAME)
public Mono<Menu> save(Menu menu) {
    return Mono.defer(() -> this.delegate.save(menu));
}
```

Nota: `findById` (con solo `@CircuitBreaker`, sin `@Retry`) **no** necesitó este cambio — el fallback no necesita re-invocar nada, solo observar una vez que hubo error y sustituir la respuesta. El `defer` es específicamente necesario cuando la anotación necesita **repetir** la operación (`@Retry`); no para traducir un fallo (`@CircuitBreaker`) ni para bloquear antes de empezar (`@Bulkhead`, que solo decide si deja pasar la suscripción).

## Configuración agregada

```properties
# menu-service-reactivo: protege los write paths de MenuRepositoryAdapter
resilience4j.retry.instances.menu-service-reactivo.max-attempts=3
resilience4j.retry.instances.menu-service-reactivo.wait-duration=50ms
resilience4j.retry.instances.menu-service-reactivo.enable-exponential-backoff=true
resilience4j.retry.instances.menu-service-reactivo.exponential-backoff-multiplier=2

resilience4j.bulkhead.instances.menu-service-reactivo.max-concurrent-calls=4
resilience4j.bulkhead.instances.menu-service-reactivo.max-wait-duration=0
```

Antes de este cambio, `@CircuitBreaker` corría con los defaults de la librería, sin que nadie los hubiera decidido — el mismo patrón de "default oculto" que ya vimos con la concurrencia de `flatMap` en el Gap E.

## Verificación: por qué estos tests necesitan un `@SpringBootTest`, no un mock simple

`ResilientMenuRepositoryAdapterTest.java` (el `*Test` original, rápido, sin contexto) se dejó intacto — sigue probando la delegación y el fallback como función aislada. Se agregó un archivo nuevo, `ResilientMenuRepositoryAdapterResilienceTest.java`, con `@SpringBootTest` + `@MockitoBean` (la anotación nueva de Spring 7/Boot 4, reemplaza a `@MockBean`) para reemplazar el delegate real sin tocar Postgres/Docker — el proxy AOP necesita un contexto real, pero no necesita una base de datos real:

```java
@SpringBootTest
class ResilientMenuRepositoryAdapterResilienceTest {
    @MockitoBean private MenuRepositoryAdapter delegate;
    @Autowired private MenuRepositoryPort menuRepositoryPort;   // el bean @Primary, con el proxy AOP real
    ...
}
```

Se mantiene como `*Test` (no `*IT`) porque no necesita Testcontainers — el único costo es el arranque del contexto de Spring (~10s), igual que `ReactividadApplicationTests`.

Tres tests, cada uno **probado contra el bug real que debía detectar** (revirtiendo el fix a propósito y confirmando que el test vuelve a fallar, restaurando después):

1. `save_WhenDelegateFailsTransiently_RetriesUntilSuccess` — el delegate falla 2 veces y responde bien a la 3ra; se confirma que se llamó exactamente 3 veces. Revertido el `Mono.defer`, falla de inmediato en el intento 1 — confirmado.
2. `save_WhenConcurrentCallsExceedBulkheadLimit_RejectsExcessWithBulkheadFullException` — se ocupan los 4 permisos del bulkhead con llamadas que nunca terminan (`Mono.never()`), la 5ta se rechaza con `BulkheadFullException`.
3. `findById_WhenDelegateFails_TranslatesToMenuUnavailableExceptionViaFallback` — confirma que el circuit breaker (que llevaba tiempo roto) ya traduce el error real a `MenuUnavailableException` vía `fallbackFindById`.

**Resultado final:** 68 tests verdes, PIT en 100% (42/42 — sin cambios, porque `ResilientMenuRepositoryAdapter` es infraestructura, fuera del alcance de PIT).

## Ejercicio 2: aislar las llamadas de `Orders` hacia `Menu` (`MenuLookupPortAdapter`)

En el ADR del Gap A (`docs/adr/0001-orders-menu-boundary.md`) ya habíamos dejado anotada esta brecha como consecuencia conocida: `MenuLookupPortAdapter` no tenía ninguna decoración de resiliencia propia. Si `Menu` se pone lento, hoy nada le impide a `Orders` acumular un número ilimitado de llamadas `findMenuSnapshot` esperando — el mismo problema de saturación que ya vimos en el ejercicio 1, pero esta vez **entre dos bounded contexts**, no dentro de uno solo.

### El fix

```java
@Bulkhead(name = "orders-menu-lookup")
public Mono<MenuSnapshot> findMenuSnapshot(MenuId menuId) {
    return this.menuUseCases.getMenu(menuId.value())
            .map(menu -> new MenuSnapshot(menuId, menu.getTitle()))
            .onErrorResume(MenuNotFoundException.class, error -> Mono.empty());
}
```

```properties
# orders-menu-lookup: aísla las llamadas de Orders hacia Menu. Carril propio y
# separado de menu-service-reactivo — si Menu se pone lento, esto limita cuánto
# puede "contagiarle" a Orders, sin afectar al resto de la aplicación.
resilience4j.bulkhead.instances.orders-menu-lookup.max-concurrent-calls=3
resilience4j.bulkhead.instances.orders-menu-lookup.max-wait-duration=0
```

Solo `@Bulkhead`, sin `@Retry`: si `Menu` dice "ese menú no existe", no es un fallo transitorio que valga la pena reintentar — es una respuesta de negocio legítima. Tampoco necesitó `Mono.defer(...)` (la lección del ejercicio 1): `@Bulkhead` solo decide si deja pasar la suscripción, nunca necesita volver a invocar nada.

Nombre de instancia **deliberadamente distinto** de `menu-service-reactivo` (el de los write paths de `Menu`): son dos recursos protegidos independientes — si el carril de escritura de `Menu` se llena, eso no debería afectar en nada al carril de `Orders` leyendo snapshots, y viceversa.

### Verificación: dos propiedades distintas, probadas por separado

```java
@SpringBootTest
class MenuLookupPortAdapterResilienceTest {
    @MockitoBean private MenuUseCases menuUseCases;
    @MockitoBean private OrderR2dbcRepository orderR2dbcRepository;
    @Autowired private MenuLookupPort menuLookupPort;
    @Autowired private OrderRepositoryPort orderRepositoryPort;
    ...
}
```

1. **El bulkhead rechaza el exceso, no lo deja colgado.** Se ocupan los 3 permisos con un `Menu` que nunca responde (`Mono.never()`); la 4ta llamada se rechaza al instante con `BulkheadFullException`, en vez de quedarse esperando para siempre.
2. **El resto de `Orders` sigue andando mientras eso pasa.** Con el bulkhead de `orders-menu-lookup` completamente saturado, `orderRepositoryPort.save(order)` (que no toca ese carril para nada) sigue completando de inmediato — la prueba concreta de que el aislamiento es real: una saturación en un recurso no se filtra a otro que no depende de él.

**Verificado rompiendo a propósito:** se quitó momentáneamente `@Bulkhead` y se corrió el primer test — la 4ta llamada se quedó colgada indefinidamente (`VerifySubscriber timed out`), confirmando que sin la anotación el problema que describe el ejercicio es real, no hipotético. Restaurado el fix, vuelve a pasar.

**Resultado final:** 70 tests verdes, suite completa sin regresiones.

## Ejercicio 3: límite de tiempo en `S3ImageStorageAdapter` (`@TimeLimiter`)

`upload`/`generatePresignedUrl` no tenían ningún control de tiempo — si S3 (o LocalStack en desarrollo) se cuelga, la llamada podía esperar indefinidamente, sin ningún mecanismo que la corte.

### ¿Qué es un `TimeLimiter`?

Una llamada asíncrona (como `s3AsyncClient.putObject(...)`) devuelve un `Mono`/`CompletableFuture` que, en teoría, *algún día* se completa — con éxito o con error. Pero "algún día" no es una garantía: si S3 (o la red hacia S3) se cuelga, ese `Mono` puede quedarse **esperando para siempre**, sin emitir nada. Nada en el código le pone un límite a cuánto tiempo estás dispuesto a esperar una respuesta.

`@TimeLimiter` responde exactamente esa pregunta: **"¿cuánto tiempo, como máximo, espero antes de darme por vencido?"** Si la operación no respondió dentro de ese plazo, `@TimeLimiter` cancela la espera y la convierte en un error (`TimeoutException`) — en vez de dejar que el código (y quien esté esperando la respuesta) cuelgue indefinidamente.

#### Por qué importa, concretamente

Sin esto, un S3 colgado en medio de un `uploadMenuImage` significa que:
- el usuario que subió la imagen nunca recibe respuesta (ni éxito ni error — el request HTTP simplemente nunca termina),
- y si pasa muchas veces, se van acumulando llamadas "zombie" esperando, cada una reteniendo recursos (conexiones, memoria) sin que nunca se liberen.

Con `@TimeLimiter`, en cambio, a los 2 segundos (lo que configuramos) el usuario recibe un error concreto — malo, pero **accionable** (puede reintentar, mostrar un mensaje) — en vez de una espera eterna.

#### La configuración, explicada

```properties
resilience4j.timelimiter.instances.s3-image-storage.timeout-duration=2s
resilience4j.timelimiter.instances.s3-image-storage.cancel-running-future=true
```

- `timeout-duration=2s`: el plazo máximo de espera.
- `cancel-running-future=true`: cuando se agota el plazo, además de devolver el error, **intenta cancelar** la operación que seguía en curso (el `CompletableFuture` subyacente de S3) — en vez de solo "dejar de esperarla" mientras sigue corriendo en segundo plano consumiendo recursos igual. Con `false`, el error llegaría igual, pero la llamada real a S3 seguiría viva de fondo hasta que termine por su cuenta (o nunca).

#### Por qué no usar `.timeout(Duration)` de Reactor directamente

Reactor ya tiene su propio operador `.timeout(Duration)` — ¿por qué no usar ese en vez de una anotación de resilience4j? La diferencia es la misma que vimos con `@Retry`/`@Bulkhead`: usar `@TimeLimiter` mantiene la configuración **centralizada y nombrada** en `application.properties` (junto con el resto de la configuración de resiliencia de ese mismo recurso), en vez de un número mágico (`Duration.ofSeconds(2)`) escrito a mano en el medio del código. También se integra con el mismo ecosistema de métricas de Micrometer — resilience4j expone automáticamente contadores de "cuántas veces se agotó el timeout" para esa instancia, algo que un `.timeout()` suelto no da gratis.

#### Dónde queda en la familia de patrones

| Patrón | Pregunta que responde |
|---|---|
| `@Retry` | "¿Este fallo fue pasajero? Intentemos de nuevo." |
| `@CircuitBreaker` | "¿Este dependiente viene fallando mucho? Dejemos de llamarlo un rato." |
| `@Bulkhead` | "¿Cuántas llamadas simultáneas le permito a este recurso?" |
| `@TimeLimiter` | "¿Cuánto tiempo, como máximo, estoy dispuesto a esperar una respuesta?" |

Con `@TimeLimiter` se completan los 4 patrones más comunes de resilience4j — cada uno protegiendo contra un tipo de falla distinto.

### El fix

```java
@TimeLimiter(name = "s3-image-storage")
public Mono<Void> upload(String key, byte[] content, String contentType) { ... }

@TimeLimiter(name = "s3-image-storage")
public Mono<String> generatePresignedUrl(String key, Duration expiration) { ... }
```

```properties
resilience4j.timelimiter.instances.s3-image-storage.timeout-duration=2s
resilience4j.timelimiter.instances.s3-image-storage.cancel-running-future=true
```

`delete` se dejó **sin** `@TimeLimiter` a propósito — el plan de este ejercicio pide explícitamente "upload/presign", y agregarlo a `delete` sin que nadie lo pidiera habría sido alcance no solicitado.

### A diferencia de `@Retry`, acá no hizo falta `Mono.defer`

En el ejercicio 1 aprendimos que `@Retry` necesita que la llamada se pueda **reinvocar** en cada intento (de ahí `Mono.defer(...)`). `@TimeLimiter` es distinto: no necesita reinvocar nada — solo necesita poder **cancelar la suscripción** si no llegó una señal a tiempo. Eso funciona igual de bien sobre un `Mono` ya construido sin `defer`, porque cancelar una suscripción no depende de si el `Mono` es diferido o no. Se verificó esto empíricamente (consistente con la disciplina de este proyecto): el test pasó a la primera, sin necesitar ningún `defer` adicional.

### Verificación

```java
@SpringBootTest
class S3ImageStorageAdapterResilienceTest {
    @MockitoBean private S3AsyncClient s3AsyncClient;
    @Autowired private ImageStoragePort imageStoragePort;

    @Test
    void upload_WhenS3NeverResponds_FailsWithTimeoutInsteadOfHangingForever() {
        when(this.s3AsyncClient.putObject(any(), any())).thenReturn(new CompletableFuture<>());  // nunca se completa

        this.imageStoragePort.upload("menu.png", "image-bytes".getBytes(), "image/png")
                .as(StepVerifier::create)
                .expectError(TimeoutException.class)
                .verify(Duration.ofSeconds(4));
    }
}
```

**Verificado rompiendo a propósito:** se quitó momentáneamente `@TimeLimiter` de `upload` y se corrió el test — se colgó indefinidamente (`VerifySubscriber timed out`), confirmando el problema real que el ejercicio describe. Restaurado el fix, vuelve a pasar.

**Resultado final:** 71 tests verdes, suite completa sin regresiones.
