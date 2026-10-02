# Primitive obsession y cuándo separar un slice vertical nuevo

Dos conceptos que surgieron trabajando en el Gap A (`Orders`) pero son de diseño general, no específicos de ningún gap — se documentan acá aparte para poder referenciarlos desde cualquier lado. Sigue la misma estructura que el resto de `docs/`.

## Primitive obsession: por qué `MenuId` no es simplemente un `UUID`

`Order` referencia un menú a través de `MenuId`, un value object que por dentro solo tiene un `UUID`:

```java
public record MenuId(UUID value) {
    public MenuId {
        if (value == null) {
            throw new IllegalArgumentException("MenuId value must not be null");
        }
    }
}
```

La pregunta obvia: ¿por qué no usar `UUID` directamente en `Order.menuId`? Para el objetivo de "no depender de la clase `Menu`", un `UUID` plano **también lo lograría** — un `UUID` no importa nada del dominio de `Menu`. Esa no es la razón real. La razón real es **seguridad de tipos en tiempo de compilación**.

### El bug concreto que evita

```java
// Con MenuId (lo que tenemos):
public Order(UUID id, MenuId menuId, int quantity, LocalDateTime createdAt)

// Con UUID plano (lo que NO hicimos):
public Order(UUID id, UUID menuId, int quantity, LocalDateTime createdAt)
```

Con la segunda firma, si alguien invierte el orden de los argumentos por error:

```java
// Con UUID plano: compila perfecto — bug silencioso, el pedido queda apuntando al id equivocado
new Order(menuIdValue, orderIdValue, 3, createdAt);

// Con MenuId: no compila — el compilador exige MenuId, no UUID
new Order(menuIdValue, orderIdValue, 3, createdAt);
//         ^^^^^^^^^^ error: se esperaba MenuId, se encontró UUID
```

Dos `UUID` son indistinguibles para el compilador, sin importar que uno represente "el id del pedido" y el otro "el id del menú referenciado" — son el mismo tipo. `MenuId` convierte un error que solo se notaría en producción (el pedido apunta al menú equivocado) en un error de compilación, visible mientras se escribe el código.

### El beneficio extra: la validación vive en un solo lugar

Una vez que existe una instancia de `MenuId`, se sabe con certeza que ya pasó la validación de su constructor compacto (`value != null`) — no hace falta repetir ese chequeo en cada método que reciba un `MenuId`. Un `UUID` plano siempre *puede* ser null; cada método que lo recibe tendría que decidir de nuevo si vale la pena validarlo.

### El nombre de la técnica

Esto se conoce como evitar **"primitive obsession"** — uno de los code smells más citados en diseño orientado a objetos / DDD: envolver un tipo primitivo (`UUID`, `String`, `int`) en un tipo chico con significado de dominio, cuando ese primitivo representa un concepto específico (no "un UUID cualquiera", sino "específicamente, una referencia a un Menu"). Es la misma idea detrás de no usar un `double` crudo para representar dinero, o un `String` crudo para un email — el tipo en sí mismo documenta y protege la intención.

`record` es la herramienta natural de Java moderno para este patrón: da inmutabilidad y `equals`/`hashCode` por valor gratis, sin escribir nada — ideal para un wrapper puro de identidad que no va a ganar comportamiento propio. Por eso `MenuId` es un `record`, mientras que `Order` (que es más probable que gane métodos de negocio con el tiempo) se mantuvo como una clase normal, con el estilo `with*` ya establecido en el Gap E.

## ¿Cuándo un concepto nuevo necesita su propio slice vertical?

No es una regla mecánica de "todo concepto de dominio nuevo = domain + application + infrastructure". Depende de si ese concepto se comporta como **protagonista de su propia historia** o como **comparsa de otro agregado**.

La pregunta que importa: *¿este concepto tiene sus propios casos de uso, o solo aparece dentro de los casos de uso de otra cosa?*

### Los tres niveles reales (con ejemplos de este proyecto)

| Nivel | Ejemplo actual | Cuándo aplica |
|---|---|---|
| **Sin slice propio** — solo modelo de dominio + mapeo de persistencia, leído a través de un puerto que ya existe | `FoodType` hoy: tiene `FoodTypeRepositoryPort.findFoodTypeByMenuId(...)`, pero ningún `FoodTypeUseCasesService` | El concepto nunca se opera por sí solo — siempre aparece *dentro* del flujo de otro caso de uso (`MenuUseCasesService.getMenu` lo orquesta, pero nadie "crea un FoodType" como operación independiente) |
| **Slice de aplicación propio, mismo contexto** | `FoodType` *si* ganara CRUD propio (crear una categoría nueva, desactivar una existente) | El concepto tiene sus propias reglas/ciclo de vida, pero sigue siendo parte de la misma área de negocio — no necesita el aislamiento arquitectónico fuerte de un bounded context separado |
| **Bounded context separado, con aislamiento verificado** | `Orders` (paquete `orders.*` propio, pensado para que ArchUnit confirme que nunca importa `demo.reactividad.domain`) | El concepto podría evolucionar de forma genuinamente independiente del resto — justifica trazar y proteger mecánicamente una frontera |

### El caso concreto que lo aclara: "asignar tipos de comida a un menú" vs. "gestionar el catálogo de tipos de comida"

Dos operaciones que suenan parecidas pero son conceptualmente distintas:

- **"Elegir qué tipos de comida tiene este menú"** (asignar Vegano+Vegetariano al crear/actualizar un menú): el protagonista sigue siendo `Menu` — es una relación *de Menu*, no una gestión del catálogo. Se resolvería extendiendo lo que ya existe (`MenuUseCasesService`, agregando un método de búsqueda por ids a `FoodTypeRepositoryPort`), sin ningún slice nuevo.
- **"Gestionar el catálogo de tipos de comida"** (crear una categoría nueva, renombrarla, desactivarla): acá `FoodType` sí pasa a ser protagonista — existe independientemente de cualquier menú puntual, tiene sus propias reglas (¿nombre único? ¿se puede borrar uno en uso?). Ahí es cuando se gana su propio `FoodTypeUseCases`/`FoodTypeUseCasesService` — pero seguiría viviendo en `demo.reactividad.*`, sin necesitar el tratamiento de bounded context separado que sí tiene sentido para `Orders`.

En resumen: **slice de aplicación propio** se decide por "¿tiene casos de uso independientes?". **Bounded context separado** (con su propio paquete raíz y fronteras verificadas) se decide por una pregunta más grande: "¿este concepto podría evolucionar, o incluso ser mantenido, de forma genuinamente independiente del resto del sistema?".
