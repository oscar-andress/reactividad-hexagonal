# RCA 0001: `*IT` fallaban en cadena por un contenedor de Postgres compartido entre clases

Este documento es el primer RCA (Root Cause Analysis) del proyecto — sirve también de plantilla para los que vengan después (Gap F, ejercicio 4, `C:\Users\oscar.vega\.claude\plans\parsed-kindling-pixel.md`). Un RCA no se queda en "pasó X y lo arreglamos": explica el mecanismo real detrás del síntoma, cómo se detectó, y qué cambia para que la misma categoría de error no vuelva a pasar.

## Resumen

| | |
|---|---|
| **Severidad** | Alta — bloqueaba `mvn verify` por completo; nunca se había podido correr con éxito en este proyecto. |
| **Detectado** | 2026-10-06, al conseguir por primera vez un Docker real alcanzable desde este entorno de desarrollo (WSL2, ver nota al final). |
| **Afectaba a** | `OrderWebIntegrationIT`, `OrderR2dbcRepositoryIT` (los `*IT` del contexto `Orders`). |
| **No afectaba a** | `MenuWebIntegrationIT`, `MenuR2dbcRepositoryIT` — pasaban siempre, lo cual retrasó el diagnóstico. |

## Síntoma

Al correr `mvn verify` por primera vez con Docker real, los `*IT` de `Menu` pasaban bien, pero los de `Orders` fallaban siempre, de forma inmediata (menos de 2 segundos), con:

```
io.r2dbc.postgresql.PostgresqlConnectionFactory$PostgresConnectionException: [08003] Cannot connect to localhost/<unresolved>:32770
Caused by: java.net.ConnectException: Connection refused: getsockopt
```

El puerto del error (`32770`) no coincidía con el de ningún contenedor realmente iniciado en esa corrida — la primera señal de que no era un problema de red ni de Docker, sino de una referencia a algo que ya no existía.

## Línea de tiempo

Este bug existía en el código desde que se escribió `OrderR2dbcRepositoryIT`/`OrderWebIntegrationIT` (Gap A), pero **nunca se había manifestado** porque este entorno de desarrollo no tenía Docker disponible — todos los `*IT` quedaban escritos pero sin ejecutar, confirmados "pendientes de verificar en una máquina con Docker" en cada doc de gap correspondiente. Recién al conseguir Docker real (corriendo dentro de WSL2, expuesto por TCP y alcanzable desde Windows gracias al networking "mirrored" de WSL2) se pudo correr `mvn verify` de punta a punta por primera vez, y el bug se hizo visible de inmediato, en la primera corrida real.

**Lección aparte:** un bug puede vivir meses en el código sin que nadie lo sepa si el mecanismo que lo detectaría (aquí, `mvn verify` con Docker real) nunca se ejecuta. Esto no es un defecto del código en sí — es un hueco en el pipeline de verificación, y es exactamente lo que el CI (este mismo ejercicio) cierra: correr esto en *cada* PR, no solo cuando alguien tiene Docker a mano.

## Causa raíz

```java
// AbstractPostgresContainerTest.java — antes
@Testcontainers
public abstract class AbstractPostgresContainerTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withInitScript("db/schema.sql");
}
```

`MenuWebIntegrationIT`, `MenuR2dbcRepositoryIT`, `OrderWebIntegrationIT` y `OrderR2dbcRepositoryIT` extienden esta misma clase abstracta. El detalle que rompía todo: en Java, un campo `static` declarado en una clase **no se duplica por cada subclase** — es uno solo, compartido por todas. Las cuatro clases `*IT` no tenían "su propio" contenedor cada una; las cuatro apuntaban al **mismo** campo `postgres`.

`@Container` le dice a la extensión de JUnit 5 de Testcontainers: *"arrancá este contenedor antes de los tests de ESTA clase, y pará lo cuando termine ESTA clase"*. Como el campo es compartido, la secuencia real fue:

1. `MenuWebIntegrationIT` arranca → la extensión inicia el contenedor compartido (puerto real, ej. `32775`) → tests pasan → la extensión lo **para** al terminar esta clase.
2. `MenuR2dbcRepositoryIT` arranca → la extensión vuelve a iniciar el mismo campo (puerto nuevo, ej. `32776`) → tests pasan → se vuelve a **parar**.
3. `OrderWebIntegrationIT` arranca → en este punto, dependiendo del estado interno de la extensión, el contenedor no se reinicia correctamente — Spring (`@ServiceConnection`) ya había capturado la configuración de conexión apuntando a un contenedor que, para cuando este contexto intenta conectarse, ya está parado. `Connection refused`.

El mismo mecanismo rompía a `OrderR2dbcRepositoryIT` después.

**Por qué `Menu` nunca lo mostró:** sus dos clases son las *primeras* en correr (orden alfabético/de paquete de Failsafe) — el bug necesita que el contenedor ya haya sido parado y reiniciado **al menos una vez** antes de manifestarse, así que las primeras dos clases en usar el campo compartido siempre lo veían "fresco".

## Cómo se detectó (no se asumió, se verificó)

1. Se leyó el log completo de la corrida, no solo el stack trace del error — buscando cuántos contenedores se habían iniciado de verdad (`grep "JDBC URL"`). Resultado: solo 3 líneas de contenedor iniciado para 4 clases `*IT` — la primera señal concreta de que algo se estaba compartiendo cuando no debía.
2. Se leyó `AbstractPostgresContainerTest.java` con esa pista en mente, y se identificó el campo `static` en la clase abstracta como la causa mecánica.
3. Se aplicó el fix y se corrió `mvn verify` dos veces más para confirmar — no alcanza con que "ahora compile", hace falta verlo pasar repetidamente.

## El fix: Singleton Container Pattern

```java
// AbstractPostgresContainerTest.java — después
public abstract class AbstractPostgresContainerTest {

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withInitScript("db/schema.sql");

    static {
        postgres.start();
    }
}
```

Se quitaron `@Testcontainers`/`@Container` — ya no hace falta que la extensión de JUnit 5 gestione el ciclo de vida por clase, porque ahora el contenedor se arranca **una sola vez, a propósito, para todo el proceso de `mvn verify`**, en un bloque `static { }` que corre la primera vez que cualquier subclase carga esta clase abstracta. `@ServiceConnection` se mantiene — Spring sigue autoconfigurando la URL/usuario/password desde el contenedor real, pero ahora contra uno que nunca se detiene a mitad de camino. Al terminar toda la JVM, Ryuk (el contenedor "reaper" de Testcontainers) limpia el contenedor igual, sin que haga falta pararlo a mano.

Este es el patrón que la propia documentación de Testcontainers recomienda explícitamente para este escenario (un contenedor compartido entre múltiples clases de test) — no es una solución improvisada.

## Verificación

| Corrida | Resultado |
|---|---|
| `mvn verify` (antes del fix, intento 1) | `OrderWebIntegrationIT` y `OrderR2dbcRepositoryIT` fallan — `Connection refused` |
| `mvn verify` (antes del fix, intento 2) | Mismo resultado — reproducible, no un fluke |
| `mvn verify` (después del fix, intento 1) | **90 unitarios + 22 de integración, todos verdes** |
| `mvn verify` (después del fix, intento 2) | Mismo resultado — estable |

## El gate verificado en CI real, no solo en local

Con el fix ya en una rama (`feature/ci-quality-gates`, PR #14), se usó ese mismo PR para probar que el gate de calidad (JaCoCo, no este bug puntual) de verdad frena un PR en GitHub Actions real — no solo en `mvn verify` local:

| Corrida en GitHub Actions | Cambio | Resultado |
|---|---|---|
| [run 37470806775](https://github.com/oscar-andress/reactividad-hexagonal/actions/runs/37470806775) | Fix + workflow, sin alterar nada | ✅ verde (1m56s) |
| [run 37471194203](https://github.com/oscar-andress/reactividad-hexagonal/actions/runs/37471194203) | Umbral de `domain`/`application` subido a propósito de 90% a 99% (real: ~92%) | ❌ rojo — falló exactamente en el paso `mvn verify` |
| [run 37471475750](https://github.com/oscar-andress/reactividad-hexagonal/actions/runs/37471475750) | Revertido el umbral | ✅ verde de nuevo |

## Qué previene que esto se repita

- **El CI de este mismo ejercicio** (`.github/workflows/ci.yml`) corre `mvn verify` en cada PR — de ahora en más, un bug de este tipo se ve en rojo antes de mergear, no se queda invisible durante meses como pasó acá.
- El patrón Singleton Container queda documentado acá como referencia — cualquier `*IT` nuevo que necesite Postgres real debe seguir extendiendo `AbstractPostgresContainerTest` (no declarar su propio contenedor `static` suelto), para no reintroducir el mismo problema por otro camino.

## Nota sobre el entorno: cómo se consiguió Docker real para esta verificación

Este proyecto no tenía Docker disponible en el entorno de desarrollo asistido hasta esta sesión. Se confirmó que Docker corre dentro de WSL2 (Ubuntu) en la máquina, y que su daemon ya escuchaba por TCP en `127.0.0.1:2375` — alcanzable directo desde Windows gracias al modo de networking "mirrored" de WSL2 (sin necesitar Docker Desktop ni reenvío de puertos manual). Alcanzó con exportar `DOCKER_HOST=tcp://localhost:2375` antes de correr `mvn verify` desde Maven en Windows.
