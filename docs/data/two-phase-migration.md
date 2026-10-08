# Migración en dos fases (expand/backfill/contract) con Flyway

Este doc cubre un ejercicio de la ruta de aprendizaje de Fase 4 (eje E3 del informe de nivel): practicar el patrón "expand/backfill/contract" para cambios de esquema, de forma que un cambio nunca pueda romper nada — ni siquiera si algo sale mal a mitad de camino. Sigue la misma estructura que el resto de `docs/`: qué se hizo, por qué, y cómo se verificó.

## El problema que resuelve

El informe cita un caso real: agregar un solo campo a un almacén compartido rompió un sistema vecino. La causa de fondo, en términos generales: agregar una columna `NOT NULL` a una tabla que ya tiene filas falla de inmediato, porque esas filas no tienen ningún valor para esa columna todavía — a menos que un único valor por defecto pueda servir para todas (ver `tbl_food_type.active boolean not null default true`, que sí es seguro en un solo paso). Cuando el valor correcto *depende de cada fila* (como un slug derivado del título), hace falta el patrón de tres pasos: **expand** (columna nueva, sin restricción), **backfill** (calcular el valor para las filas existentes), **contract** (recién ahí endurecer la restricción).

## Cómo funciona Flyway, mecánicamente

**El propósito, en una frase:** llevar un historial ordenado y versionado de cómo cambió el esquema a lo largo del tiempo, aplicando cada cambio una sola vez, en orden, y recordando cuáles ya corrieron.

**Qué pasa al arrancar la app:**
1. Spring Boot arranca el contexto — antes de que la app termine de levantar, la autoconfiguración de Flyway se activa.
2. Flyway se conecta por JDBC, usando `spring.flyway.url`/`user`/`password` de `application.properties`.
3. Busca en `src/main/resources/db/migration/` (la ubicación por defecto, sin configurarla a mano) y encuentra los archivos `V1__...sql`, `V2__...sql`, etc.
4. Revisa si existe la tabla `flyway_schema_history` en esa base. Si la base es nueva, no existe — Flyway la crea, vacía.
5. Compara qué migraciones ya están anotadas en esa tabla contra qué archivos `V*.sql` existen. Aplica, **en orden**, las que falten — nunca repite una ya aplicada.

**¿Lee un "esquema"?** No — no hay una sola foto del estado final en ningún lado (ya no existe `schema.sql`). El esquema final es el resultado acumulado de correr todos los `V*.sql` en orden, de punta a punta.

**`flyway_schema_history` es la memoria de Flyway.** Por cada migración guarda: versión, descripción, checksum del contenido del archivo, y si se aplicó con éxito. Eso permite dos cosas: nunca re-aplicar algo ya corrido, y **negarse** a avanzar si alguien edita un archivo de migración después de que ya se aplicó (el checksum no coincide).

**Dónde se crea esa tabla:** en la misma base de datos a la que Flyway se conecta, en el schema `public` por defecto (el mismo donde viven `tbl_menu`, `tbl_food_type`, etc.) — no hay un lugar "especial" aparte. Confirmado en el log real:
```
Schema history table "public"."flyway_schema_history" does not exist yet
Creating Schema History table "public"."flyway_schema_history" ...
```
En tests (`*IT`), se crea dentro del Postgres efímero de Testcontainers — por eso cada corrida arranca "limpia": una base nueva, sin historial previo, así que las 4 migraciones corren desde cero cada vez.

### Decisión: `public`, no un schema separado

Se consideró un schema dedicado solo para `flyway_schema_history` (vía `spring.flyway.schemas=...`), pero se descartó. La única ventaja real de separarlo es **permisos**: en equipos grandes, el usuario de la app a veces solo tiene permiso de leer/escribir datos de negocio, y un schema aparte permite que *solo* el proceso de deploy (con otro usuario, con permisos de DDL) toque el historial de migraciones. Acá no aplica — un solo usuario de Postgres, sin esa separación de roles. El costo de separarlo sin esa necesidad (un parámetro más que mantener, asegurar que el schema exista con los permisos correctos) no se justifica sin el beneficio real. Se dejó el default: mismo schema que las tablas de negocio.

### Incidente real: baseline de una base que ya tenía las tablas de antes

Al intentar levantar la app real (no los tests) contra un Postgres local ya existente —con `tbl_menu` y el resto de las tablas creadas *antes* de adoptar Flyway—, el arranque falló:

```
Found non-empty schema(s) "public" but no schema history table.
Use baseline() or set baselineOnMigrate to true to initialize the schema history table.
```

Es la misma protección que ya habíamos visto actuar durante la Fase 4 de seguridad: Flyway se niega a tocar un esquema que no reconoce como propio, en vez de asumir que es seguro. **Baseline** es decirle "asumí que el estado actual ya es equivalente a la versión V1 — no la vuelvas a correr, solo anotalo en el historial, y seguí migrando desde ahí". Como esa base tenía exactamente el esquema de V1 (sin `menu_slug`), era la respuesta correcta.

**Se resolvió con una variable de entorno de un solo uso, sin tocar el repo:**
```powershell
$env:SPRING_FLYWAY_BASELINE_ON_MIGRATE = "true"
mvn spring-boot:run
```
Flyway marcó V1 como "ya aplicado" y corrió V2, V3 y V4 de verdad contra esa base — conservando los datos que ya había. A propósito **no** se dejó `baseline-on-migrate=true` en `application.properties`: eso desactivaría la protección para *cualquier* base futura con un esquema inesperado, no solo para este caso puntual — perdería justo la señal de alerta que Flyway está diseñado para dar.

## Decisión de alcance: Flyway de verdad, no una simulación

Se decidió adoptar Flyway como mecanismo real de migraciones versionadas (no simular las fases con dos scripts sueltos corridos a mano en un test), porque sin un framework de migraciones no hay dónde trazar "el paso 1 pasa ahora, el paso 3 pasa después" como eventos separados y rastreables — y la "forma profesional" de resolver esto es justamente tener ese historial.

El proyecto venía con un único `schema.sql` (el estado final completo, sin historial) cargado solo por Testcontainers vía `.withInitScript(...)` — nunca se ejecutaba en la app real (`spring.sql.init.data-locations` estaba comentado). Se reemplazó por completo: `schema.sql` se convirtió en `V1__initial_schema.sql`, y de ahí en más el esquema lo gestiona Flyway, tanto en la app real como en los tests.

## Hallazgo 1: Flyway necesita JDBC, esta app es 100% R2DBC

Spring Boot 4.1.0 ya trae Flyway 12.4.0 gestionado, con `spring-boot-starter-flyway` + `flyway-database-postgresql` (el módulo específico de Postgres, separado desde Flyway 10). Pero Flyway corre por JDBC — no existe un `DataSource` bean en esta app, solo un `ConnectionFactory` reactivo (R2DBC). La solución estándar de Spring Boot para apps R2DBC-only: configurar `spring.flyway.url`/`user`/`password` directamente, sin depender de ningún bean `DataSource`:

```properties
spring.flyway.url=jdbc:postgresql://localhost:5432/food_menu_reactivo_db
spring.flyway.user=${DB_USERNAME:postgres}
spring.flyway.password=${DB_PASSWORD:postgres}
```

También hizo falta mover el driver `org.postgresql:postgresql` de scope `test` (solo lo usaban los tests) a `runtime` (la app real también lo necesita ahora).

## Hallazgo 2: Flyway no es perezoso — rompió 12 tests que nunca tocaban la base

Varios `@SpringBootTest` de este proyecto (`MenuRateLimiterTest`, los `*ResilienceTest`, `LoggingMenuBatchFailurePolicyMetricsEndpointTest`) arrancan el contexto completo de Spring **solo para activar el proxy de AOP de resilience4j** (ver la Pregunta 2 de `docs/testing-strategy.md`) — nunca necesitaron una base de datos real, porque R2DBC es perezoso: no se conecta hasta que alguien ejecuta una query de verdad. Flyway rompe esa ventaja: migra siempre al arrancar el contexto, sin importar si algo va a tocar la base. Confirmado corriendo la suite (no asumido): los 12 tests fallaron con errores de conexión/schema real.

**El fix, en dos partes:**
1. `pom.xml`, configuración de `maven-surefire-plugin`, con `spring.flyway.enabled=false` como propiedad de sistema JVM para **todos** los `*Test` — sin tocar ningún archivo de properties (un intento anterior con `src/test/resources/application.properties` reemplazó *por completo* el `application.properties` de `main` en vez de combinarse con él, un pitfall real de Spring Boot: dos recursos con el mismo nombre en el classpath no se mezclan, el del test gana entero).
2. `AbstractPostgresContainerTest` (la base de los `*IT` que sí tienen Postgres real vía Testcontainers) lo reactiva con `@TestPropertySource(properties = "spring.flyway.enabled=true")` — las propiedades inline de `@TestPropertySource` tienen más precedencia que las system properties, así que esto gana para esas subclases específicas.

**Verificado:** `mvn test` (sin Docker) pasa los 106 tests con Flyway desactivado; `mvn verify` (con Docker) muestra en el log que Flyway sí corre y migra contra el Postgres real del Testcontainers (`Database: jdbc:postgresql://localhost:32771/test`, `Creating Schema History table`).

## El ejercicio: expand/backfill/contract sobre `tbl_menu.menu_slug`

Un slug (versión de un texto apta para URL, ej. `"Ensalada César"` → `"ensalada-cesar"`) es el caso de manual de este patrón: dos menús no pueden compartir slug (`UNIQUE`), no puede estar vacío (`NOT NULL`), pero ningún menú existente tiene uno — y no hay un único valor por defecto que sirva para todas las filas, porque cada una necesita el suyo, derivado de su propio título. **No se conectó a dominio ni a ningún endpoint real a propósito** — es práctica del mecanismo de migración, no una feature pedida (mismo criterio de alcance que otras decisiones de este proyecto, ver `docs/ai-governance.md`).

```sql
-- V2__add_menu_slug_nullable.sql (EXPAND)
ALTER TABLE tbl_menu ADD COLUMN menu_slug varchar(70);

-- V3__backfill_menu_slug.sql (BACKFILL)
CREATE EXTENSION IF NOT EXISTS unaccent;
UPDATE tbl_menu
SET menu_slug = lower(regexp_replace(unaccent(trim(menu_title)), '[^a-zA-Z0-9]+', '-', 'g'))
                || '-' || substr(menu_id::text, 1, 8)
WHERE menu_slug IS NULL;

-- V4__menu_slug_not_null_unique.sql (CONTRACT)
ALTER TABLE tbl_menu ALTER COLUMN menu_slug SET DEFAULT gen_random_uuid()::text;
ALTER TABLE tbl_menu ALTER COLUMN menu_slug SET NOT NULL;
ALTER TABLE tbl_menu ADD CONSTRAINT tbl_menu_slug_unique UNIQUE (menu_slug);
```

`unaccent()` saca tildes antes de bajar a minúsculas; el sufijo de 8 caracteres del propio `menu_id` garantiza unicidad sin tener que detectar colisiones entre títulos repetidos.

## Hallazgo 3 (el más importante): sin un DEFAULT, el patrón rompe toda la app

Al correr la suite completa después de agregar V2-V4 (sin el `DEFAULT` de V4 todavía), **20 tests fallaron** con `null value in column "menu_slug" ... violates not-null constraint`. La razón: el dominio/la app nunca se enteraron de que esta columna existe (decisión explícita de alcance) — así que cada `INSERT` real que hace `MenuR2dbcRepository.save(...)` no manda ningún valor para `menu_slug`, y sin un default, eso viola el `NOT NULL` que V4 acababa de activar.

Esto no es un efecto secundario del ejercicio — es la mitad que completa la lección: el backfill (V3) resuelve las filas *viejas*; pero las filas *nuevas*, creadas por código que no sabe que la columna existe, necesitan su propia respuesta. Se agregó `ALTER TABLE tbl_menu ALTER COLUMN menu_slug SET DEFAULT gen_random_uuid()::text` antes del `NOT NULL` — no es un slug "lindo" para filas nuevas, pero es la forma honesta de documentar que, mientras nadie conecte esto al dominio, las filas nuevas reciben un valor sin sentido legible en vez de romper. El día que alguien quiera un slug real para menús nuevos, ese `DEFAULT` se reemplaza por lógica de aplicación.

**Verificado:** se corrió la suite completa antes del `DEFAULT` (20 errores reales, confirmados) y después (0 errores) — el mismo build, los mismos tests, sin tocar nada más que esa línea.

## El test dedicado, y por qué no usa Spring

`MenuSlugTwoPhaseMigrationIT` prueba el patrón contra una fila "legacy": migra solo hasta V1, inserta un menú directo por JDBC (sin slug, simulando una fila que ya existía antes de V2), migra el resto (V2-V4), y confirma que el backfill llenó el slug y que el `NOT NULL` ya está activo de verdad (un `UPDATE` que intenta dejarlo en `NULL` es rechazado por Postgres, no solo "no se prueba").

No usa `@SpringBootTest`/`AbstractPostgresContainerTest` a propósito: ese contenedor compartido ya tiene V1-V4 aplicados de punta a punta en cuanto arranca el primer test del suite (por el auto-configure de Spring Boot, que siempre migra a la última versión). Para simular "solo V1, después el resto", hace falta controlar manualmente hasta qué versión migra Flyway en cada paso — eso se hace con la API de Flyway directamente (`Flyway.configure()...target(MigrationVersion.fromVersion("1"))...migrate()`), sobre su **propio** contenedor de Testcontainers (con ciclo de vida por clase vía `@Testcontainers`/`@Container`, que es exactamente lo correcto acá — a diferencia del bug de `docs/rca/0001-...`, este contenedor no se comparte entre subclases).

**Verificado rompiendo a propósito:** se comentó temporalmente el `UPDATE` de V3 (el backfill) — Flyway mismo rechazó aplicar V4 (`ERROR: column "menu_slug" of relation "tbl_menu" contains null values`), confirmando que la secuencia depende de verdad del backfill, no es solo una formalidad. Restaurado, el test vuelve a pasar.

## Plan de reversa (por fase, manual — Flyway Community no tiene "undo" automático)

```sql
-- Revertir V4 (contract): los datos no se tocan, solo se relaja la restricción.
ALTER TABLE tbl_menu DROP CONSTRAINT tbl_menu_slug_unique;
ALTER TABLE tbl_menu ALTER COLUMN menu_slug DROP NOT NULL;
ALTER TABLE tbl_menu ALTER COLUMN menu_slug DROP DEFAULT;

-- Revertir V3 (backfill): vuelve al estado de V2.
UPDATE tbl_menu SET menu_slug = NULL;

-- Revertir V2 (expand): nadie dependía de la columna todavía.
ALTER TABLE tbl_menu DROP COLUMN menu_slug;
```

En cada fase, revertir nunca implica perder datos reales — es la propiedad central del patrón, y la razón por la que vale el costo de hacerlo en tres pasos en vez de uno.

## Resultado final

- 106 tests unitarios + 24 de integración, todos verdes (`MenuSlugTwoPhaseMigrationIT` es el nuevo).
- `schema.sql` eliminado — Flyway (`src/main/resources/db/migration/`) es ahora la única fuente de verdad del esquema, para la app real y para los tests.
- Dos hallazgos reales encontrados y resueltos en el camino (Flyway vs. R2DBC perezoso; Flyway rompiendo tests que no necesitaban DB), más el hallazgo central del ejercicio (un `NOT NULL` nuevo necesita una respuesta tanto para filas viejas — backfill — como para filas nuevas — default), cada uno verificado rompiendo a propósito antes de confiar en el fix.
