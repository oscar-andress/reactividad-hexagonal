# Límites de dominio: el bounded context `Orders` (Gap A, ejercicio 1)

Este doc cubre el ejercicio 1 del Gap A del roadmap (`C:\Users\oscar.vega\.claude\plans\parsed-kindling-pixel.md`): levantar un segundo bounded context (`Orders`) con su propio vertical slice (dominio → aplicación → persistencia), como terreno real para practicar límites de dominio en los ejercicios siguientes (anti-corruption layer, ADR, SSE propio). Sigue la misma estructura que el resto de `docs/`.

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

## Resultado de este ejercicio

- 51 tests unitarios verdes (`mvn test`), incluyendo `OrderTest`, `OrderUseCasesServiceTest`, `OrderPersistenceMapperTest`.
- Gate estricto de JaCoCo corregido para cubrir también `orders.domain`/`orders.application` — 90% cumplido sobre 11 clases.
- PIT: 100% (37/37 mutantes muertos), incluyendo el código nuevo.
- `OrderR2dbcRepositoryIT` escrito pero **no verificado** (sin Docker en este entorno) — pendiente de confirmar en `mvn verify` local/CI.
