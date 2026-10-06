# CI: qué hace cada paso de `.github/workflows/ci.yml` (Gap F, ejercicio 4)

Este doc explica el workflow de CI comando por comando — complementa a `docs/rca/0001-shared-testcontainer-across-it-classes.md` (que cuenta la historia del bug que encontramos al verificarlo) y a `docs/quality-gates.md`/`docs/mutation-testing.md` (que explican JaCoCo y PIT en sí). Sigue la misma estructura que el resto de `docs/`.

## El archivo completo

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      - name: Make mvnw executable
        run: chmod +x mvnw

      - name: Run tests and quality gates (mvn verify)
        run: ./mvnw verify

      - name: Run mutation testing (PIT)
        if: always()
        run: ./mvnw org.pitest:pitest-maven:mutationCoverage

      - name: Upload JaCoCo report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: target/site/jacoco/
          if-no-files-found: ignore

      - name: Upload PIT report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: pit-report
          path: target/pit-reports/
          if-no-files-found: ignore
```

## `on:` — cuándo dispara

`push: branches: [main]` corre después de cada merge a `main`. `pull_request` (sin filtro de rama) corre en cada PR hacia cualquier rama — es lo que disparó los 3 runs reales usados para verificar este mismo ejercicio (ver el RCA).

## `runs-on: ubuntu-latest`

La máquina virtual donde corre todo. Estos runners ya traen **Docker corriendo nativamente** — a diferencia del entorno de desarrollo local de este proyecto (Docker viviendo dentro de WSL2), en CI no hace falta ningún `DOCKER_HOST` especial para que Testcontainers encuentre el daemon.

## `actions/checkout@v4`

Clona el repo en el commit exacto que disparó el workflow. Sin este paso la VM arranca vacía — es el primer paso obligatorio de cualquier workflow que necesite el código.

## `actions/setup-java@v4`

Instala el JDK (Temurin, una distribución de OpenJDK) en la versión 17 — la misma que pide `pom.xml`. `cache: maven` cachea `~/.m2/repository` entre corridas, indexado por el hash de los archivos de Maven del repo, así no vuelve a descargar todas las dependencias (Spring, Reactor, etc.) en cada run, solo cuando cambian.

## `chmod +x mvnw`

El Maven Wrapper (`mvnw`) necesita el bit de ejecución para poder correrse como `./mvnw`. Como este repo se creó desde Windows, git nunca marcó ese bit (`git ls-files -s mvnw` mostraba `100644`, no `100755`). Sin este paso, el siguiente falla con "Permission denied" — el runner de GitHub Actions es Linux, y ahí sí importa el bit de ejecución (en Windows no existe ese concepto de la misma forma).

## `./mvnw verify` — el paso principal

Dispara, en orden: Surefire (`*Test`, unitarios) → los gates de JaCoCo atados a la fase `test` (90% domain/application, 60% overall) → Failsafe (`*IT`, contra Postgres real vía Testcontainers) → el gate de JaCoCo full en `verify`. Si cualquiera de esos falla, este paso falla, y por default **todo el job se marca en rojo** — la base de la verificación "romper un umbral a propósito, confirmar rojo" del RCA.

## `if: always()`

Por default, un paso se saltea si el paso anterior falló. `always()` lo fuerza a correr igual. La intención: aunque `mvn verify` falle, igual queremos el reporte de PIT y los artifacts subidos para poder inspeccionarlos — esto **no "perdona"** el fallo: el job sigue en rojo por el paso de `verify`, solo garantiza que los pasos informativos de después no se salteen en silencio.

## `./mvnw org.pitest:pitest-maven:mutationCoverage`

### Por qué necesita su propio paso, y no alcanza con que ya corrió `mvn verify`

En `pom.xml`, JaCoCo tiene bloques `<execution>` con `<phase>test</phase>`/`<phase>verify</phase>` — eso es lo que le dice a Maven "corré esto automáticamente cuando alguien invoque esa fase". PIT **no tiene ningún `<execution>` con `<phase>`** en su configuración — Maven nunca lo dispara solo, por más veces que corras `mvn test` o `mvn verify`. La única forma de que corra es nombrándolo por sus coordenadas completas (`groupId:artifactId:goal`), que es justo lo que hace este paso.

### Por qué está diseñado así (y por qué eso NO hace más rápido a este CI)

El motivo de no atar PIT a ninguna fase **no es que el CI corra más rápido** — el trabajo de mutar el código y correr los tests una vez por mutante tarda lo mismo esté atado a una fase o se invoque directo; el tiempo de esta corrida de CI sería idéntico en ambos casos. El motivo real es proteger el **loop local**: `mvn test`/`mvn verify` se corren muchas veces por día mientras se programa, no una vez por PR como en CI. Si PIT estuviera atado a esas fases, cada corrida local pasaría de tardar segundos a tardar minutos. En CI, que corre una sola vez por push/PR, ese costo importa mucho menos en términos absolutos — por eso ahí sí se corre siempre, como paso aparte.

Lo que sí gana el CI al tenerlo separado (no velocidad, sino control):
- **Independencia del resultado**: al no estar atado a `verify`, PIT no puede hacer fallar ese paso — permite que sea puramente informativo (`if: always()`, nunca bloquea el merge) sin que esa decisión viva mezclada en el `pom.xml`.
- **Visibilidad propia**: aparece con su propio nombre, tiempo y log en la UI de GitHub Actions, en vez de quedar enterrado dentro del log de `mvn verify`.

### Por qué nunca falla el build por un mutation score bajo

El bloque `<configuration>` de PIT en `pom.xml` solo tiene `targetClasses`/`targetTests` (qué paquetes mutar) y `outputFormats` (HTML) — **no tiene `<mutationThreshold>` ni `<coverageThreshold>`**, los parámetros que PIT ofrece justamente para convertirlo en un gate duro (ej. `<mutationThreshold>80</mutationThreshold>` haría fallar el goal si el score cae bajo 80%). Sin ellos configurados, el goal `mutationCoverage` **siempre termina en éxito**, sin importar el score — es una decisión consciente (ver `docs/mutation-testing.md`): un gate duro de mutation score forzaría perseguir el 100% todo el tiempo para no romper nunca el build, en vez de usarlo como señal para decidir dónde vale la pena escribir un test mejor.

## `actions/upload-artifact@v4`

Toma una carpeta del sistema de archivos de la VM (`target/site/jacoco/`, `target/pit-reports/`) y la guarda como un archivo descargable adjunto a esa corrida del workflow, visible en la pestaña "Summary" del run en GitHub. `if-no-files-found: ignore` evita que el workflow tire un error aparte si, por ejemplo, `mvn verify` falló tan temprano que ni llegó a generar el reporte de JaCoCo — en ese caso simplemente no sube nada, en silencio, en vez de agregar un fallo extra sin información nueva.

## Verificación real de este workflow

Documentada con links a los runs reales en `docs/rca/0001-shared-testcontainer-across-it-classes.md`: una corrida verde (sin cambios), una corrida roja (umbral de JaCoCo subido a propósito de 90% a 99%), y una corrida verde de nuevo tras revertir — las tres en GitHub Actions real, no simulado.
