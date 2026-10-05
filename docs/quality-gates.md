# Quality Gates — Guía rápida (Gap F: Calidad de Software)

Este doc documenta, a medida que se van completando, los ejercicios del roadmap de calidad de software (`C:\Users\oscar.vega\.claude\plans\parsed-kindling-pixel.md`, Gap F). Cada sección explica **qué se hizo, por qué, y cómo verificarlo** — para poder repasarlo sin tener que releer la conversación completa.

## 1. Coverage Gate con JaCoCo

### La idea en una frase

Un **quality gate de cobertura** no es un reporte bonito — es una regla que **falla el build** si el porcentaje de código ejecutado por los tests cae bajo un umbral. Si el build falla, no se puede mergear.

### ¿Qué es "cobertura de código"?

Cuando corrés tus tests (`mvn test`), cada uno ejecuta parte de tu código real. Algunas líneas se ejecutan porque un test las usa; otras líneas quizás nunca se ejecutan porque ningún test pasa por ahí.

**Cobertura** = qué porcentaje de tus líneas de código fueron ejecutadas por al menos un test.

Ejemplo: si `MenuUseCasesService.java` tiene 100 líneas, y tus tests en conjunto ejecutan 90 de ellas, tenés 90% de cobertura. Las otras 10 líneas son puntos ciegos — si tienen un bug, ningún test lo va a detectar, porque ningún test pasa por ahí.

### ¿Qué es un "quality gate"?

Es un **checkpoint automático** que le dice a Maven: "antes de dar por bueno este build, revisa esta condición — si no se cumple, para todo."

Analogía: es como el control de calidad en una fábrica de comida. No es un cartel que dice "ojalá esté bien" — es una báscula que literalmente **no deja pasar** la caja si pesa menos de lo esperado. Nadie tiene que acordarse de revisarlo manualmente; la máquina lo hace sola, siempre, para todos.

En nuestro caso: el quality gate es una regla que le agregamos a Maven que dice *"si la cobertura de tests cae bajo cierto número, falla el build"* — no un reporte que alguien podría ignorar, sino algo que literalmente bloquea el `mvn test`/`mvn verify`.

### ¿Qué puede medir un quality gate? (la cobertura es solo uno de varios)

Antes de tocar nada, tu proyecto ya tenía el quality gate más básico de todos, sin que lo configuraras: **si un test falla, el build falla.** Eso ya es un gate — *"¿pasaron todos los tests? Si no, no avances."* Surefire y Failsafe lo traen de fábrica.

Lo que agregamos hoy es más específico: no solo "¿pasaron?", sino "¿qué tanto del código tocaron?". Y eso es solo una fila de una tabla más grande de cosas que un quality gate puede medir:

| Qué mide | Ejemplo concreto | Herramienta típica |
|---|---|---|
| **¿Pasaron los tests?** | Un test rompe → build rojo | Surefire/Failsafe (gratis, ya lo tenías) |
| **Cobertura de líneas/branches** | "el 90% del código de `domain` fue ejecutado por un test" | JaCoCo (lo que armamos hoy) |
| **Calidad de los tests (no solo cantidad)** | "si le meto un bug a propósito al código, ¿algún test lo detecta?" | PIT / mutation testing (ver `docs/mutation-testing.md`) |
| **Código duplicado** | "este bloque de 20 líneas está copiado en 3 archivos" | SonarQube, PMD |
| **Complejidad excesiva** | "este método tiene 15 caminos posibles (if/else anidados) — difícil de entender y de testear" | SonarQube, Checkstyle |
| **Vulnerabilidades de seguridad** | "estás usando una librería con un CVE conocido" | OWASP Dependency-Check, Snyk |
| **Bugs potenciales (análisis estático)** | "esta variable puede ser `null` acá y la usás sin chequear" | SonarQube, SpotBugs |
| **Estilo/formato consistente** | "este archivo no sigue la convención de indentación del equipo" | Checkstyle, Spotless |

La idea general: un quality gate le pone un número o una condición binaria a algo que, sin automatizar, dependería de que un humano se acuerde de revisarlo en cada PR. La cobertura es la más común para empezar (y la que nos toca practicar primero, porque es el gap que marcó el feedback como prioritario), pero el concepto se extiende a cualquier cosa medible sobre el código — SonarQube, por ejemplo, combina varias filas de esta tabla en un solo "quality gate" compuesto.

### ¿Para qué sirve el umbral?

El **umbral** es ese "cierto número" — el mínimo de cobertura que aceptás. Es la línea entre "pasa" y "no pasa". Pusimos **dos** umbrales distintos, no uno solo, porque no todo el código merece el mismo rigor:

| Umbral | Aplica a | Por qué ese número |
|---|---|---|
| **90%** | Solo `domain` y `application` (ej. `Menu.java`, `MenuUseCasesService.java`) | Ahí vive tu lógica de negocio real. Un bug ahí es grave y difícil de detectar a simple vista, así que exigimos casi todo cubierto. |
| **60%** | Todo el proyecto (incluyendo `infrastructure`: handlers, adapters, config) | Esa capa tiene mucho código repetitivo de configuración (ej. armar un cliente de S3). Exigirle 90% ahí forzaría a escribir tests inútiles solo para "completar el número", sin valor real. |

¿Por qué no un solo umbral para todo? Si pusiéramos 90% global, alguien terminaría escribiendo un test tonto para una clase de configuración solo para "pasar el gate" — trabajo desperdiciado. Si pusiéramos 60% global, tu lógica de negocio real podría tener zonas sin probar y nadie se enteraría. Esto es la misma lógica que ya documenta la tabla de test en `CLAUDE.md`: cada capa tiene un rigor distinto.

### Qué se agregó a `pom.xml`, bloque por bloque

Se agregó un plugin (`jacoco-maven-plugin`) con 4 tareas (`<execution>`), cada una con su propósito:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.13</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>
        <execution>
            <id>check-domain-and-application</id>
            <phase>test</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <haltOnFailure>true</haltOnFailure>
                <includes>
                    <include>demo/reactividad/domain/**</include>
                    <include>demo/reactividad/application/**</include>
                </includes>
                <rules>
                    <rule>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <minimum>0.90</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
        <execution>
            <id>check-overall-baseline</id>
            <phase>test</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <haltOnFailure>true</haltOnFailure>
                <rules>
                    <rule>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <minimum>0.60</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Bloque 1 — `prepare-agent`**: le dice a Maven *"antes de correr los tests, engancha un 'espía' al programa que va a medir qué líneas se ejecutan."* Sin esto, nadie está contando nada — es como poner la cámara antes de que empiece la función.

**Bloque 2 — `report`**: después de correr los tests, genera un reporte visual (HTML) en `target/site/jacoco/` que podés abrir en el navegador y ver, clase por clase, qué líneas están en verde (cubiertas) y cuáles en rojo (no cubiertas). Esto **todavía no bloquea nada** — es solo información.

**Bloque 3 — `check-domain-and-application`** (el gate real #1): traducido, *"mirá **solamente** las clases dentro de `domain/` y `application/` (eso hace `<includes>` — un filtro). De esas clases, contá qué porcentaje de LÍNEAS (`<counter>LINE</counter>`) fueron ejecutadas por los tests. Si ese porcentaje es menor a 0.90 (90%), **falla el build**."*

**Bloque 4 — `check-overall-baseline`** (el gate real #2): igual que el anterior, pero **sin** `<includes>` — mira **todas** las clases del proyecto (incluyendo `infrastructure`), y exige solo 60%.

### Cómo se ve esto funcionando

Cuando corrés `mvn test`, después de que terminan los tests, Maven ejecuta estos dos chequeos. Si ambos se cumplen:
```
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```
Si por ejemplo la cobertura de `domain`/`application` cayera a 87%, verías:
```
[WARNING] Rule violated for bundle reactividad: lines covered ratio is 0.87, but expected minimum is 0.90
[BUILD FAILURE]
```
Y el build **se detiene ahí** — no llega a empaquetar la app, no se podría hacer deploy, nada.

### Cómo correrlo

Ambos gates están atados a la fase `test` (no `verify`), así corren con el comando de siempre, **sin necesitar Docker**:

```bash
mvn test
```

### ¿Y los tests de integración (`*IT`)? — el gate combinado en `verify`

Los dos gates de arriba (`check-domain-and-application`, `check-overall-baseline`) están atados a la fase `test` — y **la fase `test` sucede antes que la fase `integration-test`**, donde corren tus `*IT` (`MenuWebIntegrationIT`, `MenuR2dbcRepositoryIT`, vía Failsafe). Las fases de Maven son como una línea de ensamblaje, siempre en el mismo orden:

```
1. compile
2. test-compile
3. test              ← Surefire corre los *Test. Nuestros gates de arriba miden ACÁ.
4. package
5. integration-test  ← Failsafe corre los *IT (necesita Docker)
6. verify            ← acá agregamos un gate NUEVO, que ya ve todo lo de arriba
```

Como nuestros gates originales miden en el paso 3, **no ven** lo que cubren los `*IT` — literalmente todavía no corrieron en ese punto del build. Por eso clases como `MenuHandler` (que se prueba casi enteramente vía `MenuWebIntegrationIT`, no con un test unitario propio) aparecían con cobertura muy baja en el reporte, aunque en la práctica están bien probadas.

**La solución:** agregar un segundo par de ejecuciones (`report-full`, `check-overall-baseline-full`) atadas a la fase `verify` (paso 6) — para ese entonces, tanto Surefire como Failsafe ya corrieron, y JaCoCo acumula los datos de cobertura de **ambos** en el mismo archivo (`target/jacoco.exec`).

```xml
<execution>
    <id>report-full</id>
    <phase>verify</phase>
    <goals><goal>report</goal></goals>
</execution>
<execution>
    <id>check-overall-baseline-full</id>
    <phase>verify</phase>
    <goals><goal>check</goal></goals>
    <configuration>
        <haltOnFailure>true</haltOnFailure>
        <rules>
            <rule>
                <limits>
                    <limit>
                        <counter>LINE</counter>
                        <minimum>0.60</minimum>
                    </limit>
                </limits>
            </rule>
        </rules>
    </configuration>
</execution>
```

**Comparación real, medida en esta sesión** (justo cuando lo necesitábamos, Docker estuvo disponible y pudimos correr `mvn verify` completo):

| | Solo unitarios (`mvn test`) | Unitarios + integración (`mvn verify`) |
|---|---|---|
| `MenuHandler` | 2/33 líneas (6%) | **22/33 líneas (67%)** |
| Proyecto completo | 72% | **92.3%** |

Ahí queda demostrado: `MenuHandler` no estaba mal probado — estaba probado por el tipo de test que el primer gate no podía ver.

**Dos gates, dos propósitos, no uno reemplaza al otro:**

| Gate | Fase | Necesita Docker | Cuándo se usa |
|---|---|---|---|
| `check-domain-and-application` + `check-overall-baseline` | `test` | No | Loop rápido local, muchas veces por día |
| `check-overall-baseline-full` | `verify` | Sí (para los `*IT`) | Antes de mergear / en CI — la foto completa |

### ¿Qué son Surefire y Failsafe?

Son los plugins que **corren tus tests** — Surefire los `*Test`, Failsafe los `*IT`. Importante: son **plugins**, no dependencias. Una dependencia (`<dependency>`) es una librería que tu código usa para compilar/correr; un plugin es una herramienta que **Maven mismo** usa durante el build. No se distribuye con tu app.

**Surefire no aparece en ningún lado de nuestro `pom.xml`** — cero declaración, cero configuración — y aun así corre en cada `mvn test`. Es así porque Maven trae un *lifecycle* por defecto para proyectos empaquetados como `jar` (el nuestro lo es), que ya dice de fábrica *"en la fase `test`, usá Surefire"*. No es algo que Spring Boot ni nosotros hayamos agregado.

**Failsafe sí lo agregamos nosotros a propósito** (es el bloque `<artifactId>maven-failsafe-plugin</artifactId>` que armamos la sesión pasada), porque a diferencia de Surefire, **no** viene incluido en ese lifecycle por defecto. Sin declararlo, los archivos `*IT.java` nunca correrían con ningún comando.

**¿Por qué no usar Surefire para todo, ya que viene gratis?** Porque Surefire tiene filosofía "fail fast": si un test falla, para el build ahí mismo, en plena fase `test`. Eso está bien para unitarios (rápidos, sin recursos externos), pero es riesgoso para integración — si `MenuR2dbcRepositoryIT` fallara a mitad de camino, el contenedor de Postgres levantado por Testcontainers podría quedar corriendo sin apagarse bien. Failsafe separa esto en dos pasos:

1. **Fase `integration-test`**: corre los `*IT` y guarda los resultados, pero **no rompe el build todavía** — así el framework tiene chance de limpiar (apagar contenedores) pase lo que pase.
2. **Fase `verify`**: ahí sí revisa los resultados guardados y, si algo falló, recién ahí rompe el build.

**La exclusión mutua que nos da la separación limpia:** Surefire por convención **ignora** los archivos `*IT.java`; Failsafe por convención **ignora** los archivos `*Test.java`. Por eso `mvn test` corre solo unitarios (rápido, sin Docker) y `mvn verify` corre ambos — sin que tengamos que decirle explícitamente a cada plugin qué archivo tocar o no tocar.

**Ninguno de los dos exige tocar código Java.** Failsafe solo necesita que el archivo se llame `*IT.java` (convención de nombre, no anotación ni clase base obligatoria). JaCoCo tampoco — instrumenta el bytecode desde afuera vía un `-javaagent` (lo vimos en el log de `mvn -X test`), sin marcar nada en el código fuente. Contraste útil: Lombok (`@Getter`, `@Setter` en `Menu.java`) sí necesita anotaciones en el código, porque genera código a partir de ellas — Surefire/Failsafe/JaCoCo operan distinto, por convención de nombres o instrumentación externa.

### El bug real que encontramos (y por qué importa)

La primera versión de la configuración **no funcionaba** — el gate nunca fallaba, sin importar qué tan bajo pusiera el umbral (hasta probamos con un umbral matemáticamente imposible, 101%, y "pasaba"). La causa: en `jacoco-maven-plugin`, las etiquetas `<includes>`/`<excludes>` van en el **nivel superior de `<configuration>`**, no anidadas dentro de cada `<rule>`. Maven ignora en silencio los elementos XML que no reconoce — no tira error, simplemente no hace nada.

Lo confirmamos corriendo Maven en modo debug (`mvn -X test`) y leyendo el log real de cómo se configuró el objeto interno del plugin (`RuleConfiguration`), en vez de asumir que "si compila, funciona". Ahí vimos que el campo `includes` nunca se seteaba.

**La lección:** un mecanismo de control (gate, validación, chequeo) que nunca lo viste fallar es un mecanismo en el que no podés confiar. Antes de dar por bueno un gate nuevo, hay que **forzar el fallo a propósito** y ver que efectivamente frena el build — exactamente lo que hicimos: deshabilitamos temporalmente dos tests, confirmamos que el build se rompía con el mensaje correcto (`Analyzed bundle 'reactividad' with 7 classes` — las 7 clases reales de `domain`+`application`), y después restauramos los tests para volver a verde.

### Otra lección secundaria: cobertura de línea ≠ comportamiento verificado

Al buscar qué test deshabilitar para forzar el fallo, encontramos que deshabilitar `updateMenu_Success_...` (un test que sí hace aserciones detalladas sobre los valores devueltos) **no cambiaba ni una sola línea de cobertura** — porque otro test (`updateMenu_WhenSaveFailsWithOptimisticLock_...`) pasa por exactamente las mismas líneas de código, solo que sin revisar los valores resultantes. La línea "se ejecutó", pero nadie comprobó que el resultado fuera el correcto.

Esto es la motivación directa del siguiente ejercicio — mutation testing con PIT, documentado en `docs/mutation-testing.md` — porque la cobertura de línea mide si el código *corrió*, no si los tests realmente *detectarían un bug*.

## 2. Contract/regression pinning (Gap F, ejercicio 3)

### El problema que resuelve

Un test funcional (de caja negra) confirma que un caso de uso "funciona hoy", pero no protege contra un cambio accidental en la forma exacta de un contrato que otros ya consumen — el JSON de una respuesta HTTP, o la request exacta armada hacia un SDK externo. Si alguien renombra un campo de un DTO de respuesta, o agrega sin querer un campo a una request antes de mandarla a un proveedor externo, ningún test de comportamiento lo nota — el caso de uso sigue "funcionando", solo que el contrato cambió por debajo, y cualquier cliente externo que dependa de la forma anterior se rompe en silencio.

### Cuatro pines distintos, mismo propósito

1. **`MenuResponseDTOTest`**, **`OrderResponseDTOTest`**, **`ErrorResponseTest`** (golden-file real): cada uno serializa un DTO de respuesta con el mismo motor Jackson que usa la aplicación real, y compara el árbol resultante (`JsonNode`) contra un archivo JSON real en `src/test/resources/golden/*.json` — no un string embebido en el test. `JsonNode.equals` compara estructura completa (claves + valores): un campo de más, de menos, o renombrado rompe la comparación, tanto si el cambio viene del código como si viene de editar el golden file directamente (verificado rompiendo ambos). `ErrorResponse` es el de mayor alcance: lo reutilizan todos los endpoints de `Menu` y `Orders` cuando fallan, así que un solo pin protege la forma de error de toda la API, no solo de una ruta.

   La comparación la hace un helper compartido, `GoldenFileAssertions.assertMatchesGoldenFile(valor, "/golden/archivo.json")` (`testsupport/golden/`), que centraliza la lectura del archivo y el uso del mismo `ObjectMapper` en los 3 tests — con un solo caso no se justificaba la convención de archivo aparte (se empezó con un string inline), pero al llegar a 3 casos del mismo patrón sí, mismo umbral que ya usamos para los Test Data Builders (ver `docs/testing-strategy.md`).

2. **`S3ImageStorageAdapterTest.upload_Success_PinsTheExactPutObjectRequestShapeSentToS3`** (reforzado — ya pineaba los 3 campos por separado): ahora además compara el `PutObjectRequest` capturado por igualdad completa contra uno construido a mano con exactamente esos 3 campos. Si el adaptador alguna vez agrega un campo nuevo al builder sin que nadie lo note, esta comparación lo detecta, aunque los 3 campos ya verificados sigan siendo correctos. Este caso no usa `GoldenFileAssertions` porque no es JSON — es un objeto Java del SDK de AWS.

### Detalle real: Jackson 3, no Jackson 2

Spring Boot 4.1 (la versión del `spring-boot-starter-parent` de este proyecto) migró su starter de JSON a **Jackson 3** — un cambio de *groupId*, no solo de versión: las clases pasaron de `com.fasterxml.jackson.databind.*` a `tools.jackson.databind.*`. Se confirmó inspeccionando el árbol de dependencias real (`mvn dependency:tree`) y el contenido del jar (no asumido): `tools.jackson.core:jackson-databind:3.1.4` es el motor real que usa la aplicación, y ya trae soporte nativo de `java.time` (`LocalDateTime` serializa como ISO-8601 sin necesitar el módulo `jackson-datatype-jsr310` de Jackson 2, que en este proyecto ni siquiera está en el classpath). Las anotaciones (`@JsonProperty`, etc.) sí siguen viniendo del artefacto clásico `com.fasterxml.jackson.core:jackson-annotations` — confirmado leyendo el POM real de `jackson-databind-3.1.4`, que lo dice explícitamente: *"Annotations remain at Jackson 2.x group id"*.

Por qué importa para este ejercicio: el test tiene que usar el mismo motor que usa la aplicación en producción (`tools.jackson.databind.json.JsonMapper`) — el Jackson 2 clásico también está en el classpath (traído de forma transitiva por el SDK de AWS), pero nunca se usa para serializar respuestas HTTP; si el test lo hubiera usado por error, podría pasar o fallar por razones que no reflejan el comportamiento real de la aplicación.

### Verificado rompiendo a propósito

- **`MenuResponseDTOTest`**: se agregó temporalmente `@JsonProperty("title")` sobre `menuTitle` en `MenuResponseDTO` (sin tocar el nombre del accessor Java, para no romper la compilación de otros archivos que lo llaman) y se corrió el test — falló mostrando `"menuTitle"` esperado vs. `"title"` real. Revertido, vuelve a pasar.
- **`OrderResponseDTOTest`**: se editó directamente el golden file (`order-response.json`, `"quantity": 2` → `"quantity": 99`) sin tocar ningún código — el test falló igual, confirmando que el archivo real es la fuente de verdad, no solo el código de producción. Revertido, vuelve a pasar.
- **`S3ImageStorageAdapterTest`**: se agregó temporalmente `.cacheControl("no-cache")` al `PutObjectRequest.builder()` de `S3ImageStorageAdapter.upload` — el test falló por la comparación de igualdad completa, aunque los 3 campos individuales seguían siendo correctos. Revertido, vuelve a pasar.

**Resultado final:** 90 tests verdes (87 → 90: `MenuResponseDTOTest`, `OrderResponseDTOTest`, `ErrorResponseTest`), gates de JaCoCo sin cambios. Con esto queda cerrado el ejercicio 3 de Gap F — queda pendiente solo el ejercicio 4 (CI + RCA).

### Glosario corto

- **Coverage gate**: regla automatizada que falla el build si la cobertura de tests cae bajo un umbral.
- **LINE / COVEREDRATIO**: el contador de JaCoCo que mide qué fracción de líneas ejecutables fueron ejecutadas por al menos un test.
- **Bundle**: en JaCoCo, el conjunto de clases sobre el que se calcula una métrica — puede ser "todo el proyecto" o un subconjunto filtrado con `includes`.
- **Fase de Maven** (`test`, `integration-test`, `verify`...): un paso fijo en el orden de build. Siempre corren en la misma secuencia; un `<execution>` atado a una fase temprana no puede "ver" lo que pasa en una fase posterior.
- **Surefire / Failsafe**: los plugins de Maven que corren los tests. Surefire corre los `*Test` en la fase `test`; Failsafe corre los `*IT` en las fases `integration-test`/`verify`. Por convención, cada uno ignora los archivos del otro por su sufijo.
- **Contract/regression pinning**: un test que fija la forma exacta de un contrato (JSON de salida, request hacia un SDK externo) comparándolo por igualdad completa contra un valor esperado — cualquier cambio accidental en la forma, no solo en el comportamiento, rompe el test.
- **Golden-file**: el valor de referencia contra el que se compara un contract test — en este proyecto, un archivo real en `src/test/resources/golden/*.json`, no un string embebido en el test. Si la salida real se desvía del golden file, el test falla; actualizarlo a propósito es una decisión explícita, no un efecto accidental de tocar código de producción.
