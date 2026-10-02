# Límites de dominio: el bounded context `Orders` (Gap A completo)

Este doc cubre los 4 ejercicios del Gap A del roadmap (`C:\Users\oscar.vega\.claude\plans\parsed-kindling-pixel.md`): levantar un segundo bounded context (`Orders`) con su propio vertical slice (dominio → aplicación → persistencia), el anti-corruption layer que lo conecta con `Menu` sin acoplarlos, el ADR que documenta esa decisión (`docs/adr/0001-orders-menu-boundary.md`), y el vertical slice completo con su propio endpoint HTTP + SSE. Sigue la misma estructura que el resto de `docs/`.

## ¿Por qué un segundo contexto?

Con un solo concepto de negocio (`Menu`) no hay nada que "limitar" — trazar un límite de dominio solo es una habilidad real cuando dos contextos de negocio distintos necesitan relacionarse. `Orders` (pedidos) referencia un menú para saber qué se pidió, pero no debería depender de las clases de dominio de `Menu` — ese acoplamiento accidental es justo lo que este gap busca evitar y, más adelante, verificar mecánicamente con ArchUnit.

## Decisiones de diseño de este primer corte

### `MenuId` como `record`, `Order` como clase

`Orders` referencia el menú por un value object chico (`MenuId`), nunca por la clase `Menu`. Para `MenuId` se eligió un `record` de Java en vez del estilo Lombok que usa el resto del dominio (`Menu`, `FoodType`):

```java
public record MenuId(UUID value) {
    public MenuId {
        if (value == null) {
            throw new IllegalArgumentException("MenuId value must not be null");
        }
    }
}
```

Motivo completo (por qué un `record`, por qué no simplemente un `UUID`, y la técnica general detrás de esto — "primitive obsession") en `docs/domain-modeling-concepts.md`. En resumen: un `record` da inmutabilidad y `equals`/`hashCode` por valor gratis, ideal para un wrapper puro de identidad; `Order` se mantuvo como clase porque es más probable que gane métodos de negocio con el estilo `with*` ya establecido en el Gap E.

### Validación en el constructor del dominio

```java
public Order(UUID id, MenuId menuId, int quantity, LocalDateTime createdAt) {
    if (menuId == null) {
        throw new InvalidOrderException("An order must reference a menu");
    }
    if (quantity < MINIMUM_QUANTITY) {
        throw new InvalidOrderException("Order quantity must be at least " + MINIMUM_QUANTITY);
    }
    ...
}
```

A diferencia de `Menu` (que no valida nada en su constructor), `Order` sí valida sus propias reglas de negocio al construirse — un pedido sin menú o con cantidad inválida nunca debería poder existir como objeto, ni un instante. Esto demuestra la otra cara de la inmutabilidad: si el objeto no se puede mutar después, la única oportunidad de garantizar que sea válido es en el momento de construirlo.

### Una excepción simple, no la jerarquía completa de `Menu`

`Menu` tiene `MenuException` (abstracta, con `errorCode`) + `MenuCodeException` (enum) porque el `GlobalExceptionHandler` del lado web usa ese código para mapear a un status HTTP concreto. `Orders` todavía no tiene capa web (eso es el ejercicio 4 de este gap) — copiar esa misma ceremonia ahora habría sido anticipar una necesidad que no existe todavía. Por eso `InvalidOrderException` es, por ahora, una `RuntimeException` simple con solo un mensaje:

```java
public class InvalidOrderException extends RuntimeException {
    public InvalidOrderException(String message) {
        super(message);
    }
}
```

Si el ejercicio 4 necesita mapear esto a un HTTP 400 con un código específico, ese es el momento de decidir si hace falta más — no antes (CLAUDE.md: evitar funcionalidad no solicitada).

### Sin foreign key hacia `tbl_menu`

```sql
create table tbl_order(
    order_id uuid default gen_random_uuid(),
    menu_id uuid not null,
    order_quantity integer not null,
    order_created_at timestamp default current_timestamp not null,
    CONSTRAINT tbl_order_pk primary key (order_id)
);
```

A propósito, `menu_id` es solo un `uuid not null` — sin `FOREIGN KEY` hacia `tbl_menu(menu_id)`, a diferencia de `tbl_menu_food_type` que sí tiene FKs reales. El límite de dominio que se está practicando en este gap es tanto de código como de datos: si la base de datos fuerza una FK entre las tablas de dos contextos distintos, esos contextos quedan acoplados a nivel de esquema aunque el código Java esté perfectamente separado (por ejemplo, no se podrían migrar a bases de datos separadas sin romper la constraint). Este trade-off concreto (FK real con integridad referencial garantizada, vs. independencia total de esquema) es exactamente el tipo de decisión que se documenta formalmente en el ADR del ejercicio 3 — acá se deja la decisión tomada (sin FK) pero el análisis completo queda pendiente para ese documento.

## El vertical slice construido

Mismas 3 capas que `Menu`, en el paquete paralelo `demo.reactividad.orders.*`:

```
orders/domain/model/{Order, MenuId}.java
orders/domain/exception/InvalidOrderException.java
orders/application/port/in/OrderUseCases.java
orders/application/port/out/OrderRepositoryPort.java
orders/application/usecase/OrderUseCasesService.java
orders/infrastructure/adapter/out/persistence/entity/OrderEntity.java
orders/infrastructure/adapter/out/persistence/mapper/OrderPersistenceMapper.java
orders/infrastructure/adapter/out/persistence/repository/OrderR2dbcRepository.java
orders/infrastructure/adapter/out/persistence/OrderRepositoryAdapter.java
```

Solo un caso de uso por ahora (`createOrder`) — lo mínimo necesario para tener un vertical slice real y testeable, sin inventar operaciones (`getOrder`, `listOrders`) que ningún ejercicio pide todavía.

## Un hallazgo real: las quality gates no cubrían el código nuevo

Después de escribir todo el slice y correr `mvn test`, los 51 tests pasaban y "All coverage checks have been met" — pero antes de confiar en ese resultado (misma disciplina de siempre: no asumir que "pasó" significa "está bien medido"), se revisó el patrón `<includes>` del gate estricto de JaCoCo:

```xml
<!-- pom.xml — antes -->
<includes>
    <include>demo/reactividad/domain/**</include>
    <include>demo/reactividad/application/**</include>
</includes>
```

Este patrón **no matchea** `demo/reactividad/orders/domain/**` — es una ruta distinta (`orders` es un paquete hermano de `domain`, no un padre). Resultado real, confirmado antes de corregir: el gate estricto reportaba **7 clases analizadas**, el mismo número que antes de agregar `Orders` — el código nuevo de dominio/aplicación de `Orders` estaba cayendo en silencio al gate flojo (60% overall, junto con infraestructura) en vez del estricto (90%).

**Fix** (mismo patrón, agregado a ambos lados — JaCoCo y PIT, que tenían el mismo problema):

```xml
<!-- JaCoCo -->
<includes>
    <include>demo/reactividad/domain/**</include>
    <include>demo/reactividad/application/**</include>
    <include>demo/reactividad/orders/domain/**</include>
    <include>demo/reactividad/orders/application/**</include>
</includes>
```
```xml
<!-- PIT -->
<targetClasses>
    <param>demo.reactividad.domain.*</param>
    <param>demo.reactividad.application.*</param>
    <param>demo.reactividad.orders.domain.*</param>
    <param>demo.reactividad.orders.application.*</param>
</targetClasses>
```

**Verificado, no asumido:** tras el fix, el gate estricto pasó de analizar 7 a **11 clases** (confirma que efectivamente empezó a incluir el código de `Orders`), y siguió en verde (90% cumplido). PIT pasó de 33 a **37 mutaciones generadas, 37 killed (100%)** — los mutantes nuevos de `Order`/`MenuId`/`OrderUseCasesService` se generaron y los tests ya escritos los mataron a todos, sin necesidad de ningún test adicional.

Esta es la razón por la que este proyecto corre `mvn -X`/revisa números reales cada vez que se toca una quality gate: un `<include>` mal escrito no falla el build — simplemente deja de medir una parte del código, en silencio, sin ningún error que lo señale.

## Limitación de verificación: sin Docker en este entorno

El `*IT` nuevo (`OrderR2dbcRepositoryIT`, que necesita Postgres real vía Testcontainers, según la tabla de `CLAUDE.md` para "adapters sin lógica propia") se escribió espejando exactamente la estructura de `MenuR2dbcRepositoryIT` — pero **no se pudo ejecutar ni verificar en este entorno**, porque el sandbox no tiene Docker instalado (`docker: command not found`). Se confirmó que esto no es un problema del código nuevo: los `*IT` ya existentes (`MenuR2dbcRepositoryIT`, `MenuWebIntegrationIT`) fallan exactamente igual, por la misma razón — es una limitación del entorno, no una regresión introducida acá.

**Queda pendiente**: correr `mvn verify` en una máquina con Docker disponible (local o CI) para confirmar que `OrderR2dbcRepositoryIT` pasa de verdad contra Postgres real.

## Resultado del ejercicio 1

- 51 tests unitarios verdes (`mvn test`), incluyendo `OrderTest`, `OrderUseCasesServiceTest`, `OrderPersistenceMapperTest`.
- Gate estricto de JaCoCo corregido para cubrir también `orders.domain`/`orders.application` — 90% cumplido sobre 11 clases.
- PIT: 100% (37/37 mutantes muertos), incluyendo el código nuevo.
- `OrderR2dbcRepositoryIT` escrito pero **no verificado** (sin Docker en este entorno) — pendiente de confirmar en `mvn verify` local/CI.

## Ejercicio 2: el anti-corruption layer (`MenuLookupPort`)

`Orders` necesita saber el título del menú al momento de pedir — ese título se guarda como "snapshot" en el propio pedido (`Order.menuTitleSnapshot`), siguiendo un patrón real y común en e-commerce: si el menú cambia de nombre después, el pedido histórico sigue mostrando el nombre que tenía *en el momento en que se pidió*. Esta decisión (snapshot en vez de referencia viva) se tomó explícitamente, no por accidente — se decidió integrar el ACL de verdad en `createOrder` en vez de dejarlo como código aislado sin un caller real.

### La forma del anti-corruption layer

Tres piezas, cada una en la capa que le corresponde:

```java
// orders/domain/model/MenuSnapshot.java — el tipo "neutral" que Orders entiende
public record MenuSnapshot(MenuId menuId, String title) {
    public MenuSnapshot {
        if (menuId == null) { throw new IllegalArgumentException(...); }
        if (title == null || title.isBlank()) { throw new IllegalArgumentException(...); }
    }
}

// orders/application/port/out/MenuLookupPort.java — el puerto que Orders.application conoce
public interface MenuLookupPort {
    Mono<MenuSnapshot> findMenuSnapshot(MenuId menuId);
}

// orders/infrastructure/adapter/out/menu/MenuLookupPortAdapter.java — el ACL real
@Component
@RequiredArgsConstructor
public class MenuLookupPortAdapter implements MenuLookupPort {

    private final MenuUseCases menuUseCases;   // sí, puede importar esto: es infraestructura

    @Override
    public Mono<MenuSnapshot> findMenuSnapshot(MenuId menuId) {
        return this.menuUseCases.getMenu(menuId.value())
                .map(menu -> new MenuSnapshot(menuId, menu.getTitle()))      // traduce el ÉXITO
                .onErrorResume(MenuNotFoundException.class, error -> Mono.empty());  // traduce el FALLO
    }
}
```

El detalle que hace que esto sea un anti-corruption layer *de verdad*, no solo un mapper: traduce **tanto el éxito como el fallo**. Si el adaptador solo tradujera el caso feliz (`.map(...)`) y dejara pasar la excepción `MenuNotFoundException` sin tocar, esa excepción — que es un tipo de `demo.reactividad.domain.exception`, del otro contexto — llegaría hasta `OrderUseCasesService`, y ahí ya habría una fuga del dominio de `Menu` hacia `Orders`, aunque fuera "solo para capturarla". El `onErrorResume` convierte "no existe" en `Mono.empty()` — una ausencia neutral, sin tipo de `Menu` involucrado — y `OrderUseCasesService` reacciona a esa ausencia con su **propia** excepción:

```java
// orders/application/usecase/OrderUseCasesService.java
public Mono<Order> createOrder(MenuId menuId, int quantity) {
    return this.menuLookupPort.findMenuSnapshot(menuId)
            .switchIfEmpty(Mono.error(() -> new MenuNotFoundForOrderException(   // excepción propia de Orders
                    "Cannot order menu " + menuId.value() + ": it does not exist")))
            .map(snapshot -> new Order(menuId, snapshot.title(), quantity))
            .flatMap(this.orderRepositoryPort::save);
}
```

### Por qué `MenuUseCases` y no `MenuRepositoryPort`

El plan dejaba abierta la elección entre que el adaptador llame a `MenuRepositoryPort` (el puerto de persistencia de `Menu`) o a `MenuUseCases` (su puerto de entrada/casos de uso). Se eligió `MenuUseCases` a propósito: es el contrato público real de `Menu` — pasa por sus reglas de negocio, su manejo de errores (`MenuNotFoundException`), etc. Depender de `MenuRepositoryPort` en cambio sería saltarse la capa de aplicación de `Menu` y hablar directo con lo que es, conceptualmente, un detalle de infraestructura de *otro* contexto — funcionaría, pero rompería la encapsulación de `Menu` igual que si `Orders` leyera directamente su tabla SQL.

### La regla mecánica: ArchUnit

Con el ACL ya construido, se agregó la dependencia `com.tngtech.archunit:archunit` y `ArchitectureRulesTest` con 3 reglas:

```java
@Test
void ordersDomainNeverDependsOnMenuDomainOrApplication() {
    noClasses().that().resideInAPackage("demo.reactividad.orders.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "demo.reactividad.domain..", "demo.reactividad.application..")
            .check(ALL_CLASSES);
}

@Test
void ordersApplicationNeverDependsOnMenuDomainOrApplication() {
    // mismo patrón, para orders.application..
}

@Test
void ordersNeverDependsOnMenuRepositoryPortDirectly() {
    noClasses().that().resideInAPackage("demo.reactividad.orders..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("demo.reactividad.application.port.out.MenuRepositoryPort")
            .check(ALL_CLASSES);
}
```

**Verificado rompiendo a propósito, no asumido:** se agregó temporalmente un campo `Menu` dentro de `OrderUseCasesService` y se corrió el test — falló señalando exactamente el campo culpable:

```
Architecture Violation [Priority: MEDIUM] - Rule 'no classes that reside in a package
'demo.reactividad.orders.application..' should depend on classes that reside in any package
[...]' was violated (1 times):
Field <...OrderUseCasesService.leakedMenuReference> has type <demo.reactividad.domain.model.Menu>
```

Y lo mismo para la tercera regla: se agregó temporalmente `MenuRepositoryPort` como dependencia de `MenuLookupPortAdapter` y el test lo señaló igual de preciso. Ambos reverts confirmados, suite verde de nuevo.

### El mutante que PIT encontró en `Order`

Al agregar `menuTitleSnapshot`, PIT encontró un mutante `SURVIVED` real: `CONDITIONALS_BOUNDARY` cambió `quantity < MINIMUM_QUANTITY` por `quantity <= MINIMUM_QUANTITY` en el constructor de `Order`. Ningún test probaba el valor límite exacto (`quantity = 1`, que debería ser válido) — los tests existentes solo cubrían "claramente inválido" (`0`, `-1`) y "claramente válido" (`3`), dejando el límite mismo sin probar. Se agregó `constructor_WhenQuantityIsExactlyTheMinimum_DoesNotThrow` y el mutante murió.

**Resultado final:** 59 tests unitarios verdes, gate estricto de JaCoCo en verde, PIT en **100%** (41/41 mutantes muertos).

## Ejercicio 3: el ADR

Documentado en `docs/adr/0001-orders-menu-boundary.md` — compara el diseño síncrono elegido (ejercicio 2) contra un snapshot dirigido por eventos, y documenta un bloqueo real encontrado al revisar el código (`ReactorMenuEventPublisher` usa `replay().limit(1)`, así que la alternativa por eventos no funcionaría hoy sin cambios adicionales).

## Ejercicio 4: el vertical slice completo (HTTP + SSE)

Último ejercicio del gap: exponer `Orders` como un recurso HTTP real, con su propio stream SSE — mismo patrón que `MenuHandler`/`MenuRouterConfig`, aplicando desde el día uno el manejo correcto de `EmitResult` (no hubo que "primero romperlo, después arreglarlo" como en el Gap E, porque ya conocíamos la forma correcta).

### Las piezas nuevas

- **`OrderEventPublisher`** (puerto) + **`ReactorOrderEventPublisher`** (adaptador): idéntico a `MenuEventPublisher`/`ReactorMenuEventPublisher`, con `Sinks.EmitFailureHandler.busyLooping(...)` y `failedEmissionCount` desde la primera versión.
- **`OrderUseCasesService.createOrder`** ahora publica el pedido guardado (`.doOnNext(this.orderEventPublisher::publish)`), y gana `streamOrders()` delegando al publisher — mismo patrón que `MenuUseCasesService.createMenu`/`streamMenus`.
- **`OrderHandler`** + **`OrderRouterConfig`**: `POST /api/v1/order/` (crea un pedido) y `GET /api/v1/order/stream` (SSE). Las rutas no necesitaron ningún cambio en los filtros de seguridad (`AuthenticationWebFilter`/`AuthorizationWebFilter` son globales, no atados a paths de `Menu`) — un `POST` requiere el token `PRIME`, un `GET` funciona con `STANDARD`, igual que en `Menu`.
- **`OrderExceptionHandler`**: mapea `InvalidOrderException` → 400 y `MenuNotFoundForOrderException` → 404, reusando el `ErrorResponse` genérico que ya existía (no es específico de `Menu`, es una forma de respuesta de error reutilizable entre contextos).

### Una decisión de diseño: `OrderExceptionHandler` propio, no reusar `GlobalExceptionHandler`

El `GlobalExceptionHandler` existente está atado a `MenuException` (usa `ex.getErrorCode()`, que solo las excepciones de `Menu` tienen). Las excepciones de `Orders` (`InvalidOrderException`, `MenuNotFoundForOrderException`) se mantuvieron simples a propósito en el ejercicio 1 (sin esa ceremonia de `errorCode`, ver `docs/hexagonal-boundaries.md` ejercicio 1) — forzarlas a heredar de `MenuException` para poder reusar el handler existente hubiera sido, literalmente, acoplar `Orders` a una clase de `Menu`. Se optó por un `OrderExceptionHandler` propio, chico, que construye el código de error a mano por cada excepción — más repetición entre los dos handlers, pero cero acoplamiento entre contextos.

### Verificación

- `OrderUseCasesServiceTest` extendido: verifica que `createOrder` publica el evento tras guardar, y que `streamOrders()` delega al publisher.
- `ReactorOrderEventPublisherTest`: mismo test de concurrencia que `ReactorMenuEventPublisherTest` (200 pedidos desde 16 threads). **Verificado rompiendo a propósito**: se revirtió momentáneamente a `tryEmitNext` sin manejo y se corrió el test 3 veces — **3/3 fallaron** detectando pérdida de eventos; con el fix restaurado, vuelve a pasar siempre.
- `OrderWebMapperTest`, `OrderRouterConfigTest` (mockeando `OrderUseCases`, igual que `MenuRouterConfigTest`).
- `OrderWebIntegrationIT`: escrito espejando exactamente `MenuWebIntegrationIT` (auth, forbidden, not-found, y el flujo SSE completo: crear un pedido y confirmar que llega por el stream). **No se pudo ejecutar en este entorno** (sin Docker) — se confirmó que compila y que el resto del suite (65 tests) sigue en verde; queda pendiente correrlo en `mvn verify` local/CI.
- PIT: **100%** (42/42 mutantes muertos) tras agregar la lógica nueva de `createOrder`/`streamOrders`.

Con esto se cierran los 4 ejercicios del Gap A (Arquitectura Hexagonal: Diseño Evolutivo y Límites de Dominio).
