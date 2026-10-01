# Inmutabilidad en `Menu` y `FoodType` (Gap E: SOLID / Trade-offs / Concurrencia e Inmutabilidad)

Este doc cubre los ejercicios 1 y 2 del Gap E del roadmap (`C:\Users\oscar.vega\.claude\plans\parsed-kindling-pixel.md`): hacer `Menu` y `FoodType` inmutables. El ejercicio 3 (concurrencia en `ReactorMenuEventPublisher`) tiene su propio documento: `docs/reactive-streams-and-concurrency.md`. Sigue la misma estructura que `docs/quality-gates.md` y `docs/mutation-testing.md`.

## ¿Cuándo usar getter/setter y cuándo no?

No es una regla de "nunca usar setters" — depende de qué tipo de objeto es:

| Tipo de objeto | ¿Mutable está bien? | Ejemplos en este proyecto |
|---|---|---|
| Objeto de dominio / value object (representa una regla de negocio) | **No** — debería ser inmutable | `Menu`, `FoodType` |
| Builder / acumulador (existe solo para construir otra cosa, se descarta después) | Sí — es su propósito | `MenuTestDataBuilder` |
| Objeto forzado por un framework (JPA/R2DBC necesita constructor vacío + setters para hidratar filas) | Sí — es una concesión a infraestructura, no a diseño | `MenuEntity` |

La pregunta útil no es "¿puedo poner un setter?" sino **"¿quién es el dueño del cambio de estado?"**. En un objeto de dominio, cualquier cambio debería pasar por un método con nombre de negocio (`withUpdatedDetails`, `withImageKey`) que deja explícito *qué* cambió y garantiza que el objeto resultante sigue siendo válido. Un setter genérico (`setImageUrl`) no impone ninguna de esas garantías — cualquiera puede mutar cualquier campo, en cualquier momento, sin pasar por ninguna regla.

## Qué significa "inmutable" en la práctica

1. Todos los campos son `final`.
2. No hay setters.
3. Un único constructor "canónico" (el más completo) es la única fuente de verdad — todos los demás constructores y métodos `with*` delegan en él con `this(...)`.
4. Cualquier "cambio" en realidad crea y devuelve una **instancia nueva**; la instancia original nunca se toca.

```java
// Antes: Menu tenía @Setter de Lombok + @NoArgsConstructor
public Menu withUpdatedDetails(String title, String description) {
    // (no existía como with* — se mutaba con setTitle/setDescription directamente en el caller)
}

// Después: un solo with* por cada cambio legítimo
public Menu withUpdatedDetails(String title, String description) {
    return new Menu(this.id, title, description, this.createdAt,
            this.version, this.imageKey, this.imageUrl, this.foodTypes);
}

public Menu withFoodTypes(Set<FoodType> foodTypes) {
    return new Menu(this.id, this.title, this.description, this.createdAt,
            this.version, this.imageKey, this.imageUrl, foodTypes);
}
```

Solo se agregaron los `with*` que algún sitio de la aplicación realmente necesita (`withFoodTypes`, `withImageKey`, `withImageUrl`, además del `withUpdatedDetails` que ya existía) — no se agregó `withId`/`withVersion` porque nada los usa (CLAUDE.md: evitar funcionalidad no solicitada).

## Un bug real que esto arregló de paso

El viejo `withUpdatedDetails` usaba un constructor de 6 argumentos que **no incluía** `foodTypes` ni `imageUrl` — es decir, actualizar el título de un menú silenciosamente perdía sus tipos de comida e imagen presignada en el objeto resultante (aunque en la práctica no se notaba porque el flujo real no encadenaba esas operaciones en el mismo request). Al forzar **un solo constructor canónico de 8 argumentos** como única fuente de verdad, ya no es posible escribir un `with*` que "olvide" un campo — el compilador te obliga a pasar los 8.

## Efecto en cascada: de `Menu` mutable a inmutable en toda la capa de aplicación

Se buscaron (`grep`) todos los sitios de producción que llamaban setters de `Menu` y se reemplazaron por reasignación vía `with*`:

```java
// MenuUseCasesService.getMenu — antes
.map(foodTypes -> {
    menu.setFoodTypes(foodTypes);
    return menu;
})
// después
.map(menu::withFoodTypes)

// MenuUseCasesService.withPresignedImageUrl — antes (este es el que PIT encontró sobreviviente, ver docs/mutation-testing.md)
.map(presignedUrl -> {
    menu.setImageUrl(presignedUrl);
    return menu;
});
// después
.map(menu::withImageUrl);

// MenuUseCasesService.uploadAndTagImage — antes
.doOnSuccess(unused -> menu.setImageKey(menuId.toString()))
.thenReturn(menu);
// después
.thenReturn(menu.withImageKey(menuId.toString()));
```

`MenuPersistenceMapper.toDomain` pasó de construir un `Menu` parcial y completarlo con 2 setters, a construir el objeto completo en una sola llamada al constructor canónico. `MenuPersistenceMapper.toEntity` **no cambió** — solo llama setters sobre `MenuEntity` (infraestructura, mutable a propósito), nunca sobre `Menu`.

## La consecuencia inesperada: Mockito `PotentialStubbingProblem`

Al correr los tests después del refactor, 3 fallaron con este error de Mockito:

```
org.mockito.exceptions.misusing.PotentialStubbingProblem
```

**Causa:** los tests stubbeaban así:

```java
when(this.menuRepositoryPort.save(existingMenu)).thenReturn(Mono.just(updatedMenu));
```

Eso le dice a Mockito "solo responde si te llaman con *esta referencia exacta* de objeto". Antes, como `Menu` era mutable, `uploadAndTagImage` mutaba `existingMenu` in-place y lo pasaba tal cual a `save(...)` — la referencia coincidía. Después del refactor, `uploadAndTagImage` devuelve `menu.withImageKey(...)`, una **instancia nueva**; `save(...)` nunca recibe la referencia `existingMenu` original. Mockito, en modo estricto (el default), no asume "son iguales en contenido, dejalo pasar" — falla ruidosamente para forzar a que el test refleje lo que el código de verdad hace.

**Arreglo:** cambiar el stub para que acepte cualquier instancia de `Menu` (el contenido exacto que importa se verifica por separado):

```java
when(this.menuRepositoryPort.save(any(Menu.class))).thenReturn(Mono.just(updatedMenu));

// y donde de verdad importa qué se guardó, un ArgumentCaptor:
ArgumentCaptor<Menu> savedArgumentCaptor = ArgumentCaptor.forClass(Menu.class);
verify(this.menuRepositoryPort).save(savedArgumentCaptor.capture());
assertEquals(MENU_ID.toString(), savedArgumentCaptor.getValue().getImageKey());
```

Esta no es una casualidad de testing — es la prueba automatizada de que el refactor cambió identidad de objetos, no solo su forma. Un test que dependía de identidad de referencia (`==`) sobre un dominio mutable deja de tener sentido apenas ese dominio se vuelve inmutable, porque cada paso del pipeline reactivo ahora produce una instancia distinta.

Nota aparte: `Menu` no tiene `equals`/`hashCode` por valor (solo `@Getter`/`@ToString` de Lombok), así que comparar dos instancias con contenido igual (`expectNext(unaInstanciaConstruidaAParte)`) tampoco funciona — hay que comparar campo por campo (`assertNext(result -> assertEquals(...))`) o usar `ArgumentCaptor`.

## Verificación: PIT confirma que el fix es de raíz, no cosmético

`docs/mutation-testing.md` ya documentaba un mutante sobreviviente en `withPresignedImageUrl`, parcheado en su momento con un `ListAppender` que verificaba que no se logueara ningún error en el camino feliz — un parche explícitamente etiquetado como "no la causa raíz". Tras este refactor, se volvió a correr PIT **sin ese parche**:

- El mutante que antes sobrevivía → ahora muere, sin necesidad del `ListAppender` (se quitó del test, ver sección final de `mutation-testing.md`).
- Mutation score final tras cerrar también un segundo hallazgo (un `NO_COVERAGE` real y distinto, en el manejo de conflicto optimista de `uploadMenuImage`): **100%** (33/33 mutantes muertos).

Esta es la prueba concreta de que "hacer inmutable un objeto de dominio" no es una preferencia estética — cierra de verdad un hueco de testing que la mutabilidad ocultaba.

## Ejercicio 2: `FoodType` — un caso más simple (nada que migrar)

El plan original proponía agregar un método `withActive(boolean)` a `FoodType`, siguiendo el mismo patrón que `Menu`. Antes de escribirlo, se revisó (`grep`) si algún sitio de producción llamaba a algún setter de `FoodType` — **ninguno lo hacía**. Todo el código (el mapper de persistencia, los tests) ya construía `FoodType` completo de una sola vez con el constructor de todos los argumentos:

```java
// FoodTypePersistenceMapper — así se usaba antes Y después, sin cambios
return new FoodType(entity.getFoodTypeId(), entity.getFoodTypeName(), entity.isActive());
```

Es decir, `FoodType` ya *se comportaba* como inmutable — solo le faltaba que el compilador lo garantizara. Agregar `withActive(...)` en este punto habría sido especulativo (nada lo llama), así que, siguiendo el mismo criterio de CLAUDE.md que ya aplicamos en `Menu` (no agregar métodos sin un caller real), el cambio se redujo a lo mínimo:

```java
// Antes
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class FoodType {
    private UUID id;
    private String name;
    private boolean active = true;
}

// Después
@Getter @AllArgsConstructor
public class FoodType {
    private final UUID id;
    private final String name;
    private final boolean active;
}
```

Se quitó `@Setter` (sin usuarios), `@NoArgsConstructor` (nada construía un `FoodType` vacío) y el valor por defecto `= true` (ya no tiene sentido con campos `final` — el único constructor que queda exige los 3 valores siempre).

**Verificación:** los 42 tests y las quality gates de JaCoCo pasaron sin tocar ningún otro archivo — señal de que el cambio fue puramente "cerrar una puerta que ya nadie usaba", no un refactor de comportamiento. PIT confirmó `33/33` mutantes muertos (100%), igual que antes del cambio — no se introdujo ni se rompió ninguna garantía.

No se agregó `equals`/`hashCode` por valor tampoco: ningún test compara dos instancias de `FoodType` con `assertEquals(foodTypeA, foodTypeB)` (todos comparan campo por campo, `getId()`/`getName()`), así que no hay un caso real que lo necesite hoy. Si en el futuro aparece un caso de uso que compare o deduplique `FoodType` por valor (por ejemplo, un `Set<FoodType>` que deba tratar dos instancias con el mismo `id` como iguales), ese sería el momento de agregarlo — no antes.
