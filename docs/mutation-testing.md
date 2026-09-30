# Mutation Testing con PIT — Guía rápida (Gap F: Calidad de Software)

Este doc documenta el ejercicio de mutation testing del roadmap de calidad de software (`C:\Users\oscar.vega\.claude\plans\parsed-kindling-pixel.md`, Gap F). Sigue la misma estructura que `docs/quality-gates.md` — **qué se hizo, por qué, y cómo verificarlo** — y lo complementa: si `quality-gates.md` cubre el coverage gate (JaCoCo), este archivo cubre el paso siguiente, que ataca justo el punto ciego que la cobertura de línea deja pasar.

## El problema que resuelve

La cobertura de línea mide si un test **ejecutó** código, no si ese test **verificaría un bug** si lo hubiera. Ya vimos un ejemplo real en `quality-gates.md`: deshabilitar `updateMenu_Success_...` (que sí revisa valores) no bajó ni una línea de cobertura, porque otro test pasaba por el mismo código sin comprobar nada.

**Mutation testing** ataca ese punto ciego directamente:

1. La herramienta (PIT) toma tu código real y le mete un bug a propósito y mecánico — un **mutante**. Ejemplos: cambiar `return menu;` por `return null;`, invertir un `if`, borrar una llamada a un método.
2. Corre **todos tus tests** contra esa versión mutada.
3. Si algún test **falla** → el mutante murió (*killed*) → bien, tus tests detectan ese bug.
4. Si todos los tests **siguen pasando** → el mutante sobrevivió (*survived*) → mala señal: ese bug podría llegar a producción sin que nadie se entere.

El **mutation score** = % de mutantes muertos. Es una medida mucho más honesta que el % de cobertura de líneas.

Analogía: es como si un instructor de manejo, en vez de solo mirar si diste la vuelta completa al circuito (cobertura), te pusiera obstáculos a propósito (mutantes) para ver si reaccionás de verdad o solo memorizaste el trayecto.

## Tipos de mutantes (mutators) que usa PIT

PIT no inventa un bug al azar — tiene un catálogo de transformaciones mecánicas predefinidas ("mutators"), cada una especializada en un tipo de cambio. No todas están activas por defecto: nosotros no configuramos `<mutators>` en el `pom.xml`, así que PIT usa su grupo `DEFAULTS`.

| Mutator | Qué hace | Ejemplo | ¿En `DEFAULTS`? | ¿Lo vimos en nuestro código? |
|---|---|---|---|---|
| `VOID_METHOD_CALLS` | Borra una llamada a un método que no devuelve nada | `menu.setImageUrl(x);` → (nada) | Sí | ✅ Sí (3 mutantes) |
| `NULL_RETURNS` | Cambia el valor de retorno de un método por `null` | `return menu;` → `return null;` | Sí | ✅ Sí (29 mutantes — acá encontramos nuestro sobreviviente) |
| `EMPTY_RETURNS` | Cambia el retorno por una colección/objeto "vacío" (lista vacía, `Optional.empty()`, etc.) | `return foodTypes;` → `return Set.of();` | Sí | ✅ Sí (1 mutante) |
| `NEGATE_CONDITIONALS` | Invierte una comparación | `if (x == null)` → `if (x != null)` | Sí | ✅ Sí (1 mutante) |
| `CONDITIONALS_BOUNDARY` | Mueve el límite de una comparación numérica | `if (x > 0)` → `if (x >= 0)` | Sí | No (no tenemos comparaciones numéricas en `domain`/`application`) |
| `MATH` | Cambia un operador aritmético por otro | `a + b` → `a - b` | Sí | No |
| `INCREMENTS` | Cambia `i++` por `i--` (o viceversa) | `count++` → `count--` | Sí | No |
| `INVERT_NEGS` | Invierte una negación numérica | `-x` → `x` | Sí | No |
| `FALSE_RETURNS` / `TRUE_RETURNS` | Fuerza que un método que devuelve `boolean` siempre devuelva `false`/`true` | `return active;` → `return false;` | Sí | No (no tenemos métodos que devuelvan `boolean` en ese alcance) |
| `NON_VOID_METHOD_CALLS` | Borra una llamada a un método que sí devuelve algo, reemplazándolo por un valor por defecto | `menu.getImageKey()` → (nunca se llama, usa `null`) | **No** (grupo `STRONGER`) | ✅ Sí, pero solo cuando probamos con `ALL` habilitado |
| `CONSTRUCTOR_CALLS` | Reemplaza el resultado de un constructor por `null` | `new Menu(...)` → `null` | **No** (grupo `STRONGER`) | No probado |
| `REMOVE_CONDITIONALS_*` | Elimina una condición completa (fuerza que siempre entre o nunca entre a un `if`) | `if (x)` → siempre `true` (o siempre `false`) | No | No probado |

**El detalle importante:** que un mutator esté en `DEFAULTS` no garantiza que genere mutantes — solo se activa si tu código tiene el patrón correspondiente. Por eso, aunque `CONDITIONALS_BOUNDARY`/`MATH`/`INCREMENTS` están en nuestro set activo, generaron **cero** mutantes: nuestro `domain`/`application` no tiene comparaciones numéricas ni aritmética para mutar — son puros objetos, validaciones y llamadas a puertos.

## Qué se agregó a `pom.xml`

```xml
<plugin>
    <groupId>org.pitest</groupId>
    <artifactId>pitest-maven</artifactId>
    <version>1.17.0</version>
    <dependencies>
        <dependency>
            <groupId>org.pitest</groupId>
            <artifactId>pitest-junit5-plugin</artifactId>
            <version>1.2.1</version>
            <exclusions>
                <exclusion>
                    <groupId>org.junit.platform</groupId>
                    <artifactId>junit-platform-launcher</artifactId>
                </exclusion>
                <exclusion>
                    <groupId>org.junit.platform</groupId>
                    <artifactId>junit-platform-engine</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
    </dependencies>
    <configuration>
        <targetClasses>
            <param>demo.reactividad.domain.*</param>
            <param>demo.reactividad.application.*</param>
        </targetClasses>
        <targetTests>
            <param>demo.reactividad.domain.*</param>
            <param>demo.reactividad.application.*</param>
        </targetTests>
        <outputFormats>
            <outputFormat>HTML</outputFormat>
        </outputFormats>
    </configuration>
</plugin>
```

- **`pitest-junit5-plugin`**: obligatorio aparte del core de PIT. El core de PIT es viejo y por defecto solo entiende JUnit 3/4 — sin este plugin, no reconoce tests con `@Test` de Jupiter (JUnit 5).
- **`targetClasses`/`targetTests`**: igual que el `<includes>` de JaCoCo (ver `quality-gates.md`) — solo mutamos `domain`/`application`, no vale la pena mutar clases de configuración de infraestructura.
- **A propósito, NO está atado a ninguna fase** (`test`/`verify`). Mutation testing es lento — corre tus tests una vez por cada mutante, docenas de veces. Meterlo en el loop normal de `mvn test` mataría la velocidad que protegimos en el coverage gate. Se corre a demanda:

```bash
mvn org.pitest:pitest-maven:mutationCoverage
```

## El bug que encontramos al agregarlo (otra vez, no funcionó a la primera)

La primera corrida falló con `Coverage generator Minion exited abnormally! (UNKNOWN_ERROR)` — un error genérico sin pista. Prendimos el logging verboso propio de PIT (`-Dverbose=true`, distinto del `-X` de Maven) y encontramos la causa real:

```
Caused by: org.junit.platform.commons.JUnitException: OutputDirectoryCreator not available;
probably due to unaligned versions of the junit-platform-engine and junit-platform-launcher
jars on the classpath/module path.
```

Confirmamos con `grep` que había **dos versiones de `junit-platform-engine`** en el classpath: `6.0.3` (la de nuestro proyecto, vía Spring Boot 4.1.0) y `1.9.2` (una vieja, traída como dependencia transitiva de `pitest-junit5-plugin:1.2.1`, que es más viejo que el esquema de versionado unificado de JUnit 6). Se arregló excluyendo esa dependencia transitiva vieja (ver `<exclusions>` arriba) — dejamos que las versiones correctas de nuestro propio proyecto fluyan sin chocar.

**La misma lección de siempre:** no asumimos "el plugin dice que falló, debe ser nuestro código" ni "el plugin corrió, debe estar bien" — prendimos logging verboso y leímos la causa real antes de tocar nada.

## El mutante real que encontramos

Primera corrida: **34 mutaciones generadas, 31 muertas (91%), 1 sobrevivió.**

Usamos `javap -p target/classes/.../MenuUseCasesService.class` para identificar con certeza (no adivinando) qué línea era el mutante sobreviviente — resultó ser este código dentro de `withPresignedImageUrl`:

```java
.map(presignedUrl -> {
    menu.setImageUrl(presignedUrl);
    return menu;          // PIT cambió esto por "return null;"
});
```

**¿Por qué sobrevivió?** En teoría, un `.map()` que devuelve `null` debería explotar (Reactor prohíbe elementos `null`). Pero la línea *anterior* (`menu.setImageUrl(presignedUrl)`) ya se había ejecutado — es un efecto secundario sobre un objeto **mutable**. El método que envuelve esta llamada:

```java
private Mono<Menu> withPresignedImageUrlSafely(Menu menu) {
    return withPresignedImageUrl(menu)
            .doOnError(error -> log.error(...))
            .onErrorReturn(menu);   // atrapa la excepción y devuelve `menu`
}
```

`.onErrorReturn(menu)` atrapa la excepción causada por el `null`, y devuelve la **misma referencia** `menu` — que ya tenía el `imageUrl` correcto seteado. Resultado: el test ve el valor esperado igual, sin darse cuenta de que por debajo pasó por una excepción silenciada. Cobertura de línea no lo detecta (la línea sí se ejecutó); un test de caja negra tampoco (el resultado final es idéntico).

## La causa raíz vs. el parche que aplicamos hoy

La causa de fondo es que `Menu` es mutable, y un efecto secundario tapa un bug de valor de retorno. La solución "de raíz" sería hacer `Menu` inmutable (`withImageUrl(...)` en vez de `setImageUrl(...)`) — eso está planeado para el Gap E del roadmap (SOLID/inmutabilidad), no para este ejercicio de Gap F. Mezclar ambos gaps en el mismo cambio hubiera sido scope creep.

**Lo que hicimos hoy fue un parche dirigido**, con una técnica nueva — capturar logs con `ListAppender` de Logback, para verificar que `log.error(...)` **nunca** se dispara en el camino feliz:

```java
Logger logger = (Logger) LoggerFactory.getLogger(MenuUseCasesService.class);
ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
logAppender.start();
logger.addAppender(logAppender);
try {
    // ... correr el código bajo test ...

    boolean loggedAnError = logAppender.list.stream()
            .anyMatch(event -> event.getLevel() == Level.ERROR);
    assertFalse(loggedAnError, "No debería haberse logueado ningún error en el camino feliz");
} finally {
    logger.detachAppender(logAppender);  // limpieza: no dejarlo pegado para otros tests
}
```

No hizo falta agregar ninguna dependencia — `logback-classic` ya viene con Spring Boot.

Con el código real (`return menu;`), no se loguea nada → `loggedAnError` es `false` → test pasa. Con el mutante (`return null;`), sí se loguea el error en el `doOnError` → `loggedAnError` es `true` → `assertFalse` falla → **el mutante muere**.

**Resultado verificado:** `NullReturnValsMutator` pasó de `29 generados, 27 killed, 1 SURVIVED` a `29 generados, 28 killed, 0 SURVIVED`. Mutation score general: 91% → **94%**. Test strength (ignorando mutantes sin cobertura): 97% → **100%**.

## Segundo hallazgo: `NO_COVERAGE` no es lo mismo que `SURVIVED`

Explorando el reporte por curiosidad, encontramos que el paquete `demo.reactividad.domain.exception` mostraba **0% de mutation coverage** — pero el score general seguía en 94%. La explicación no es un bug del reporte, es aritmética simple: ese paquete solo generó **1 mutante en total** (`0/1`), y ese único mutante pesa poco sobre el total de 34. El resto (`Menu` + `MenuUseCasesService`) aportaba 33 mutantes con 32 killed — el promedio ponderado queda alto aunque un paquete puntual esté en 0%.

Mirando el detalle de ese mutante, el status no era `SURVIVED`, era **`NO_COVERAGE`** — una distinción importante:

| Status | Significa |
|---|---|
| `SURVIVED` | Hay un test que pasa por esa línea, pero no detecta el cambio (test **débil**) |
| `NO_COVERAGE` | **Ningún** test pasa por esa línea — ni siquiera se intentó (hueco de cobertura real, ni JaCoCo ni PIT tienen nada que evaluar ahí) |

El mutante caía en `MenuException.getErrorCode()`:

```
1. getErrorCode : replaced return value with "" for MenuException::getErrorCode → NO_COVERAGE
   Killed by: none
```

`getErrorCode()` se usa en producción (`GlobalExceptionHandler.buildResponse`), pero ningún test lo llamaba directamente — nuestros tests de `MenuUseCasesServiceTest` solo verifican el *tipo* de excepción lanzada (`expectError(MenuNotFoundException.class)`), nunca inspeccionan el código de error en sí. Se cerró con un test directo y mínimo:

```java
class MenuExceptionTest {

    @Test
    void getErrorCode_ReturnsTheCodeGivenAtConstruction() {
        MenuException exception = new MenuNotFoundException("mensaje", "NOT_FOUND");

        assertEquals("NOT_FOUND", exception.getErrorCode());
    }
}
```

(`MenuException` es abstracta, así que se instancia a través de una subclase concreta cualquiera — `MenuNotFoundException` — solo para poder probar el comportamiento heredado.)

**Resultado verificado:** el mutante pasó de `NO_COVERAGE` a `KILLED` por `MenuExceptionTest`. Mutation score general: 94% → **97%**.

## Glosario corto

- **Mutante**: una copia del código de producción con un bug metido a propósito y mecánico (ej. `+` → `-`, `return x` → `return null`).
- **Killed / Survived**: un mutante "muere" si algún test falla al correr contra esa versión mutada; "sobrevive" si todos los tests igual pasan.
- **No coverage**: distinto de "survived" — significa que ningún test siquiera pasa por esa línea. No es un test débil, es la ausencia total de un test.
- **Mutation score**: % de mutantes muertos sobre el total generado.
- **Test strength**: igual que mutation score, pero excluyendo los mutantes que caen en código sin ninguna cobertura de línea (esos no cuentan como "test débil", cuentan como "no hay test en absoluto").
- **Mutante equivalente**: un mutante que, aunque técnicamente cambia el bytecode, no puede producir ningún comportamiento observable distinto — imposible de matar con cualquier test, no es un hueco real.
