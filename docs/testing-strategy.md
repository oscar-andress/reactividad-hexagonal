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

## ¿Qué es un "fixture"?

Antes de la cuarta preocupación, vale la pena definir un término que ya aparece en el código (`testsupport/fixtures/`) y que se usa sin explicar: un **fixture** es el estado conocido y repetible que un test necesita como punto de partida — los datos o el contexto que preparás *antes* de ejercitar el código que realmente querés probar. El nombre viene de "fijar" un estado conocido: un test no debería depender de "lo que haya quedado dando vueltas" — necesita arrancar siempre desde el mismo punto, para ser repetible y confiable.

Dos sentidos del término, relacionados entre sí:

**1. Fixture como datos reutilizables** (el sentido que usa la carpeta `testsupport/fixtures/` de este proyecto):

```java
// MenuTestDataBuilder.java — vive en testsupport/fixtures/
public static MenuTestDataBuilder aMenu() {
    return new MenuTestDataBuilder();   // "DEVOS", "Menu de prueba", por defecto
}
```

`aMenu().build()` da un `Menu` válido y conocido, sin que el test tenga que inventar desde cero "¿qué campos necesita un Menu válido?".

**2. Fixture como el *proceso* de preparar ese estado** (el sentido clásico de los frameworks xUnit — JUnit, etc.):

```java
// MenuWebIntegrationIT.java
@BeforeEach
void setUp() {
    this.standardToken = this.jwtTokenService.generate(AuthenticationCategory.STANDARD);
    this.primeToken = this.jwtTokenService.generate(AuthenticationCategory.PRIME);
    this.existingMenu = this.menuR2dbcRepository.deleteAll()
            .then(this.menuR2dbcRepository.save(aMenuEntity().build()))
            .block();
}
```

Este método `@BeforeEach` (corre antes de **cada** test de la clase) es, en el sentido clásico, "armar el fixture" — deja la base de datos en un estado conocido antes de que cualquier test arranque. Un `@AfterEach` que limpiara algo después sería "desarmar el fixture".

Sin fixtures, cada test tendría que decidir desde cero sus propios datos — con el riesgo real de que dos tests se pisen entre sí (estado compartido mutable), o de que un test pase "por casualidad" con datos que no representan un caso real.

## Una cuarta preocupación, independiente de las tres anteriores: DRY en la construcción de datos de prueba

Las tres preguntas de arriba deciden *cómo* se arma un test (contenedor sí/no, mock vs real). Hay una preocupación separada, ortogonal a esas tres: **¿estoy repitiendo el mismo literal una y otra vez al construir los datos de entrada?**

Se encontró esto, real, en el código:

```
MenuWebIntegrationIT.java:106:  new MenuCreateRequestDTO("Test", "Test description")
MenuWebIntegrationIT.java:156:  new MenuCreateRequestDTO("Test", "Test description")   // idéntico
MenuRateLimiterTest.java:64:    new MenuCreateRequestDTO("Test", "Test description")   // idéntico otra vez
OrderWebIntegrationIT.java:79,91: new OrderCreateRequestDTO(this.existingMenu.getId(), 1)  // idéntico
```

El proyecto ya tenía el patrón correcto para esto — **Test Data Builder** — aplicado a los objetos de dominio (`MenuTestDataBuilder`, `OrderTestDataBuilder`) y a las entidades de persistencia (`MenuEntityTestDataBuilder`, `OrderEntityTestDataBuilder`). Lo que faltaba era extenderlo a los **DTOs de request** de la capa web, que se seguían construyendo con literales sueltos en cada test.

**El fix:** `MenuCreateRequestDTOTestDataBuilder`, `MenuUpdateRequestDTOTestDataBuilder`, `OrderCreateRequestDTOTestDataBuilder` — mismo patrón ya establecido (`aMenuCreateRequestDTO()`, `withTitle(...)`, `.build()`), con valores por defecto sensatos para que el caso común sea `aMenuCreateRequestDTO().build()` sin tener que especificar nada.

```java
// Antes — el mismo literal repetido en 3 archivos distintos
MenuCreateRequestDTO menu = new MenuCreateRequestDTO("Test", "Test description");

// Después
MenuCreateRequestDTO menu = aMenuCreateRequestDTO().build();                           // caso común, con defaults
MenuCreateRequestDTO menu = aMenuCreateRequestDTO().withTitle("  ").build();           // solo lo que importa para ESTE test
```

Por qué importa, más allá de "menos líneas": si `MenuCreateRequestDTO` alguna vez gana un campo nuevo, con el builder se actualiza **un solo lugar**; sin él, hay que tocar cada call site a mano, con el riesgo real de olvidar alguno.

**Verificado:** se corrió el suite completo (87 tests) y PIT (100%, 44/44) antes y después del refactor — mismos números exactos, confirmando que fue un cambio puramente de forma, sin tocar ningún comportamiento.

## Contract/regression pinning: qué es un "pin", cuándo aplica, y golden files

Otra preocupación independiente de las cuatro anteriores — no decide *cómo* testear ni *cómo construir* datos de prueba, decide **qué tan completa** tiene que ser una comparación para proteger un contrato. Surgió al cerrar el ejercicio 3 del Gap F (ver `docs/quality-gates.md`).

**¿Qué es "pinear" (pin) un contrato?**
Fijar el valor esperado completo de una salida (una respuesta JSON, una request armada hacia un SDK externo) y compararlo por **igualdad estructural total** contra ese valor — no por aserciones parciales sobre 1 o 2 campos que "importan para este test". Un assert normal (`assertEquals("Vegano", result.foodTypes().get(0).name())`) solo protege el campo que decidiste mirar; un pin protege *toda la forma* — un campo de más, de menos, o renombrado, aunque nadie lo esté mirando a propósito, también rompe el test.

**¿Cuándo vale la pena pinear un contrato? (no "por cada API")**
Dos condiciones, ambas a la vez:
1. Cruza un límite hacia algo que no controlás en el mismo deploy — un cliente externo que ya parseó la respuesta, o un SDK/API de un tercero al que le mandás una request.
2. Un test de comportamiento normal no lo detectaría — el caso de uso sigue "funcionando" aunque la forma exacta cambie por debajo.

Un pin cubre todos los endpoints que comparten la misma forma, no hace falta uno por ruta. En este proyecto: `MenuResponseDTO`, `OrderResponseDTO` y, el de mayor alcance, `ErrorResponse` — lo reutilizan todos los endpoints de ambos contextos cuando fallan, así que un solo pin protege la forma de error de toda la API. Los DTOs de *entrada* (`MenuCreateRequestDTO`, etc.) no entran en este criterio: ya están protegidos por Bean Validation + tests funcionales, y el riesgo de contract testing pega más fuerte en lo que *sale* del sistema (ya interpretado por alguien afuera) que en lo que entra (nosotros controlamos la forma y la rechazamos si está mal).

**¿Qué es un golden file?**
El valor de referencia contra el que se compara un pin — capturado una vez, tratado como fuente de verdad. Si la salida real se desvía del golden file, el test falla; actualizarlo para reflejar un cambio intencional es una decisión explícita (editar el archivo a mano), no un efecto secundario de tocar código de producción. El término viene de "golden master" testing, usado también para comparar salidas que no son JSON (HTML renderizado, binarios, código generado).

**¿String embebido en el test, o archivo real?**
Depende del mismo umbral que ya decide un Test Data Builder (sección anterior): con un solo caso, un string inline alcanza y evita introducir una convención nueva de carpeta sin necesidad real — así se empezó. Al llegar a varios casos del mismo patrón (en este proyecto, 3: `menu-response.json`, `order-response.json`, `error-response.json`), vale la pena la convención de archivo real en `src/test/resources/golden/`: el diff de un cambio de contrato se ve limpio como diff de archivo en un PR, en vez de escondido dentro de un diff de código Java, y un helper compartido (`GoldenFileAssertions`) evita repetir la lógica de lectura + comparación en cada test.

### Por qué un golden-file interno no alcanza entre servicios desplegados independientemente: el ejemplo completo

El golden-file protege contra **regresiones accidentales propias** — algo que funcionaba deja de funcionar, como efecto secundario de un cambio que, visto desde el propio equipo, parecía sano y bien testeado. Pero tiene un límite real cuando el productor y el consumidor de un contrato se despliegan por separado. El siguiente escenario (hipotético: hoy `Menu` y `Orders` viven en el mismo monolito, pero el ADR 0001 ya deja planteado un futuro donde se separan) lo muestra paso a paso.

**Paso 1 — el contrato de hoy.** `Menu` expone `GET /api/v1/menu/{id}` devolviendo `{"menuId": "...", "menuTitle": "Pizza", ...}`. `Orders`, como otro servicio desplegado por su cuenta, llama a ese endpoint y guarda `response.menuTitle` como snapshot del pedido.

**Paso 2 — la regresión simple (la que SÍ atrapamos hoy).** Alguien en `Menu` renombra `menuTitle` a `title` por prolijidad interna. Sus propios tests quedan en verde (todo es consistente *dentro* de `Menu`). Al desplegar, `Orders` (que no se tocó) sigue leyendo `menuTitle` → recibe `null` → guarda el pedido con el título en `null`, en producción, sin que nadie lo haya decidido. Nuestro `MenuResponseDTOTest` sí frena este caso: pinea `"menuTitle"` exacto, así que el rename rompe el build de `Menu` *antes* de deployar.

**Paso 3 — el caso que el golden-file no atrapa.** `Orders` depende de algo más sutil: necesita `menuCreatedAt` **sin** offset de zona horaria (`"2026-01-15T10:30:00"`), porque su librería de parseo de fechas es estricta. `Menu` migra internamente a `OffsetDateTime` por una razón propia legítima, y el campo empieza a salir con offset (`"2026-01-15T10:30:00-05:00"`). Como el cambio es "intencional" desde el punto de vista de `Menu`, el propio equipo **edita su golden file** para que coincida — su test sigue en verde, porque son dueños del archivo y lo actualizaron a propósito. Nadie le preguntó a `Orders` si podía soportar el formato nuevo. Se despliega. `Orders` explota en producción. El golden-file no ayudó: quien decide si el cambio está "bien" es el mismo equipo que lo hizo, sin ninguna forma de saber qué necesita realmente el otro lado.

**Paso 4 — cómo Contract Testing consumer-driven (Pact) resuelve justo el Paso 3:**
1. `Orders` escribe un test **de su propio lado**, contra un mock de Pact: *"cuando llamo a `GET /menu/{id}`, espero que `menuCreatedAt` tenga este formato exacto"*. Al correrlo, Pact genera un archivo de contrato que describe, literalmente, lo que `Orders` usa de verdad — no lo que `Menu` supone.
2. Ese archivo se publica en un lugar que ambos equipos leen (un "Pact Broker").
3. El CI de `Menu` descarga ese contrato en cada build y corre una verificación: *¿mi respuesta real todavía cumple lo que `Orders` dice necesitar?*
4. Cuando alguien en `Menu` cambia el formato de fecha, esa verificación **falla en el CI de `Menu`, antes de deployar** — no después, en producción.

La diferencia de fondo: el contrato no lo escribió el productor adivinando — lo generó el consumidor real, probando su propio código. Por eso no puede quedar desactualizado en silencio como en el Paso 3: si `Orders` cambia lo que necesita, actualiza su propio pact; si `Menu` rompe lo que `Orders` todavía necesita, el pact lo detecta igual, sin depender de que el productor se acuerde de preguntar.
