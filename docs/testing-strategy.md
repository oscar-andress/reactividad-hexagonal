# Estrategia de testing: el flujo de preguntas que decide todo

Este doc es transversal — no pertenece a un gap puntual. Junta las decisiones que aparecieron repetidas veces en el Gap D (Resiliencia Avanzada) y el Gap B (Seguridad): **¿es unitario o de integración?**, **¿necesito un contexto real de Spring?**, y **¿este colaborador hay que mockearlo o puedo usar el real?** Son tres preguntas distintas, que conviene hacer **en orden** — mezclarlas de una sola vez es lo que genera la confusión de "¿uso `Mock`, `MockBean`, o hago un test de integración?".

## El flujo completo, en orden

### Pregunta 1: ¿es un test unitario o de integración?

La convención de este proyecto ya lo responde por el **sufijo** (ver `CLAUDE.md`):

- **`*Test`** → unitario. Corre con `mvn test` (Surefire). Rápido, nunca toca Docker/red real.
- **`*IT`** → integración. Corre con `mvn verify` (Failsafe). Lento, necesita algo real corriendo (Postgres vía Testcontainers, etc.).

La pregunta concreta para decidir cuál es: **¿el objetivo es probar MI lógica, o probar que una integración real funciona?**

- `MenuUseCasesServiceTest` (`*Test`): quiero probar que mi código orquesta bien los pasos — no me importa si Postgres de verdad acepta el SQL.
- `MenuR2dbcRepositoryIT` (`*IT`): quiero probar que el mapeo objeto-relacional funciona contra un Postgres **real** — ahí sí importa, porque fue justo donde encontramos el bug de `menu_created_at NOT NULL` que un mock nunca hubiera detectado.

Si la respuesta es "quiero probar una integración real", la pregunta termina ahí: `*IT`, con lo real corriendo, **sin mockear** la pieza que querés integrar — mockearla sería anular el propósito del test.

### Pregunta 2 (solo si es `*Test`): ¿necesito `@SpringBootTest`, o alcanza con `new X(...)`?

La pregunta exacta: **¿la clase bajo test tiene alguna anotación que solo funciona a través de un proxy de Spring AOP?** — `@Retry`, `@Bulkhead`, `@TimeLimiter`, `@CircuitBreaker`, `@RateLimiter` (todas de resilience4j) son los casos reales de este proyecto. Son metadatos que un aspecto de Spring intercepta — si construís el objeto con `new` a mano, el aspecto nunca se activa, y la anotación queda sin ningún efecto (lo confirmamos empíricamente: ver `docs/resilience.md`, el hallazgo de que `@CircuitBreaker` llevaba tiempo sin funcionar, exactamente por este motivo).

```java
// Necesita @SpringBootTest — @Retry/@Bulkhead solo actúan a través del proxy real
@SpringBootTest
class ResilientMenuRepositoryAdapterResilienceTest { ... }

// NO necesita @SpringBootTest — AuthenticationWebFilter es lógica plana, sin ninguna
// anotación que dependa de un proxy
class AuthenticationWebFilterTest {
    private final JwtTokenService jwtTokenService = new JwtTokenService(SECRET, Duration.ofMinutes(15));
    private final AuthenticationWebFilter filter = new AuthenticationWebFilter(this.jwtTokenService);
}
```

Si no hay AOP de por medio, usar `@SpringBootTest` igual no sería *incorrecto* — solo sería pagar ~10 segundos de arranque de contexto por nada. La regla: **reservá el contexto real para cuando algo específicamente lo necesita, no por costumbre.**

### Pregunta 3: para cada colaborador, ¿es una frontera de infraestructura, o uso el objeto real?

Esta pregunta es independiente de la anterior — aplica tanto si hay contexto de Spring como si no. El criterio, de `CLAUDE.md`: *"Mocks y stubs solo en las fronteras de infraestructura (I/O, APIs externas, persistencia)"*.

> **¿Esta llamada sale del proceso de la JVM?** — ¿toca la red, un disco, otro servicio? Si sí, es frontera. Si todo pasa en memoria, en el mismo proceso, sin tocar nada externo, no es frontera — sin importar si la clase viene de una librería de terceros.

| Colaborador | ¿Qué hace al llamarlo? | ¿Frontera? | Qué usar en el test |
|---|---|---|---|
| `MenuR2dbcRepository.save(...)` | Manda bytes por un socket a Postgres | **Sí** | mockear (unitario) o Testcontainers real (`*IT`) |
| `S3AsyncClient.putObject(...)` | Request HTTP a S3/LocalStack | **Sí** | mockear |
| `MenuUseCases.createOrder(...)` | Eventualmente toca DB/S3 (aunque el método en sí no lo haga directo) | **Sí** | mockear |
| `JwtTokenService.parseCategory(...)` | HMAC-SHA256 sobre bytes ya en memoria | **No** | instancia real |
| `SimpleMeterRegistry.counter(...).increment()` | Suma 1 en un `Map` en memoria | **No** | instancia real |
| `Validator.validate(dto)` | Recorre campos y chequea anotaciones en memoria | **No** | instancia real |

Prueba mental rápida: **"si llamo a este método 10.000 veces en un loop, ¿necesito algo externo corriendo (Postgres, S3, internet) para que no explote o se cuelgue?"** Si no, no es frontera — usá el objeto real, te da una prueba más fuerte (comportamiento auténtico) que un mock, que solo verificaría "¿llamé al método correcto?".

#### Si decidís mockear: `@Mock` vs `@MockitoBean` — depende de la Pregunta 2

Esta es la distinción que genera más confusión, y la resuelve directamente la respuesta de la Pregunta 2 (¿hay `@SpringBootTest` o no?):

| | Sin `@SpringBootTest` | Con `@SpringBootTest` |
|---|---|---|
| **Frontera que hay que mockear** | `@Mock` de Mockito + `new X(mock)` a mano | `@MockitoBean` |
| **No es frontera, uso el real** | `new SimpleMeterRegistry()`, `new JwtTokenService(...)` | `@Autowired` |

`@Mock` y `@MockitoBean` no son intercambiables: **`@Mock` le pide a *Mockito* que cree un objeto falso — Mockito no sabe nada de Spring.** Si estás dentro de un `@SpringBootTest` y usás `@Mock`, Mockito crea el mock, pero el contenedor de Spring nunca se entera de que existe — Spring sigue construyendo el bean real (con sus propias dependencias reales) para inyectarlo donde corresponda, y tu mock queda sin usarse.

**`@MockitoBean` le pide a *Spring*** que, al armar el contexto, reemplace la definición del bean real por un mock de Mockito — así el resto del contexto se arma normal (incluyendo el proxy AOP que sí necesitás real en algún otro bean), pero esa pieza puntual queda controlada por vos. Se usa exactamente cuando necesitás que un bean **real y gestionado por Spring** (por ejemplo, uno con proxy AOP activo) dependa de algo **falso**.

## Ejemplo completo, con las tres preguntas respondidas

```java
@SpringBootTest                                        // P2: @RateLimiter necesita el proxy AOP → sí
class MenuRateLimiterTest {

    @MockitoBean                                        // P3: MenuUseCases es una frontera (toca DB/S3) →
    private MenuUseCases menuUseCases;                  //     mockear; hay Spring → @MockitoBean, no @Mock

    @Autowired
    private WebTestClient webTestClient;

    @Autowired                                          // P3: JwtTokenService NO es frontera (todo en memoria) →
    private JwtTokenService jwtTokenService;             //     uso el real; hay Spring → @Autowired, no new
}
```

(P1 ya está resuelta por el nombre del archivo: `MenuRateLimiterTest`, no `...IT` — no necesita Docker, todo lo que haría falta de infraestructura real quedó mockeado.)

## Las preguntas combinadas: todas las combinaciones reales de este proyecto

| | Tiene colaborador-frontera que mockear | Sin colaborador-frontera (todo en memoria) |
|---|---|---|
| **Necesita proxy AOP** (P2 = sí) | `ResilientMenuRepositoryAdapterResilienceTest`, `MenuRateLimiterTest`: `@SpringBootTest` + `@MockitoBean` sobre la frontera | *(infrecuente — si no hay frontera que mockear, usualmente tampoco hace falta `@SpringBootTest` salvo el AOP en sí)* |
| **No necesita proxy AOP** (P2 = no) | `MenuUseCasesServiceTest`: Mockito plano, `@Mock` sobre `MenuRepositoryPort`/`ImageStoragePort` | `JwtTokenServiceTest`, `AuthenticationWebFilterTest`, `LoggingMenuBatchFailurePolicyTest`: `new X(...)` directo, sin mocks |

Cada celda responde una pregunta distinta — por eso conviene decidirlas en el orden de arriba, en vez de elegir "el patrón de test que usamos la última vez" sin volver a pensar cuál pregunta aplica en este caso puntual.
