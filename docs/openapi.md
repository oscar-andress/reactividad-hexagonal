# OpenAPI: generar el spec real para alimentar a ZAP

Objetivo del ejercicio: que ZAP (`docs/security/owasp-zap-baseline.md`) pueda conocer todas las rutas reales de la API de una sola vez (importando un spec OpenAPI), en vez de descubrirlas una por una a mano o depender del Spider (que no sirve para una API JSON pura, sin links HTML que seguir).

## Decisión 1: ¿la documentación queda pública o protegida?

Decidido antes de tocar código: **protegida, con el mismo `auth-token` que el resto de la API** — se descartó la opción "pública" (el estándar de facto en la industria) para mantener la postura actual del proyecto de "todo requiere auth, sin excepciones" lo más intacta posible.

**Consecuencia real de esa decisión, encontrada después:** la página de Swagger UI es HTML servido por la propia app, así que también exige el header — y un navegador normal, en una navegación simple de página, no puede mandar un header custom. Sin un workaround (ej. una extensión tipo ModHeader), la UI visual no se puede abrir en un navegador común. El spec JSON (`/v3/api-docs`) sí se puede pedir con `curl`/Postman/ZAP agregando el header a mano, que es lo único que hacía falta para el objetivo real del ejercicio.

Se agregó una excepción puntual en `AuthenticationWebFilter` (`PUBLIC_PATH_PREFIXES`), la primera que tiene ese filtro — solo para `/v3/api-docs`, nunca para rutas reales de negocio.

## Decisión 2: `springdoc-openapi-starter-webflux-ui`, no `-api`... hasta que se rompió

Primer intento: `springdoc-openapi-starter-webflux-ui` 2.8.6 (la última versión realmente publicada en Maven Central — se verificó ahí directamente, no se confió en lo que decía la documentación del propio springdoc, que mencionaba una versión 2.9.x/3.x que no existe como artefacto real todavía).

**Hallazgo real:** esta versión fue compilada contra Spring Framework 6. Este proyecto corre Spring Boot 4.1.0 / Spring Framework 7.0.8. El JSON del spec (`/v3/api-docs`) funciona bien, pero la página de Swagger UI se cae con:

```
java.lang.NoSuchMethodError: 'UriComponentsBuilder UriComponentsBuilder.fromHttpRequest(HttpRequest)'
    at org.springdoc.webflux.ui.SwaggerWelcomeCommon.buildFromCurrentContextPath
```

Es una incompatibilidad binaria real, no un bug de configuración — `springdoc-openapi` todavía no tiene ninguna versión publicada con soporte real para Spring Framework 7 (confirmado contra el índice de Maven Central, no contra la documentación del proyecto, que resultó ambigua/desactualizada en este punto).

**Decisión final:** se cambió a `springdoc-openapi-starter-webflux-api` (sin el `-ui`) — genera el mismo JSON en `/v3/api-docs` (que es lo único que hace falta para ZAP) sin traer la parte de Swagger UI que está rota. Se descartó seguir con la UI rota (nadie la puede usar) y se descartó invertir tiempo en buscar un visor alternativo (Scalar, Redoc) por no ser el objetivo real de este ejercicio.

## Hallazgo 3: el estilo funcional de WebFlux no se autodescubre

Con la dependencia agregada, `/v3/api-docs` devolvía `"paths":{}` — vacío. Causa: este proyecto usa el estilo funcional de WebFlux (`RouterFunction`/`HandlerFunction`, en `MenuRouterConfig`/`OrderRouterConfig`), no `@RestController`. El descubrimiento automático de springdoc busca por default anotaciones de controlador clásico — mismo patrón ya documentado para Bean Validation (`docs/security/bean-validation.md`: `@Valid` tampoco se dispara solo en el estilo funcional).

**Solución:** anotaciones `@RouterOperation`/`@RouterOperations` de springdoc sobre el método `@Bean` que expone el `RouterFunction`, una por cada ruta real, apuntando al método del handler (`beanClass`/`beanMethod`) — springdoc inspecciona ese método para construir la entrada del spec. Aplicado en `MenuRouterConfig` (8 rutas) y `OrderRouterConfig` (2 rutas).

## Hallazgo 4: `@RouterOperation` sin más no alcanza para contratos reales

Con solo `path`/`method`/`beanClass`/`beanMethod`, el spec quedaba con rutas pero **sin contratos reales** — cada respuesta mostraba `"schema":{"$ref":"#/components/schemas/ServerResponse"}` y `ServerResponse: {"type":"object"}`, es decir, vacío. Causa: `@RouterOperation` mira la *firma* del método del handler para inferir el tipo de respuesta, y en el estilo funcional de WebFlux esa firma siempre es `Mono<ServerResponse>` — una interfaz genérica de Spring, no el DTO real que el handler termina escribiendo en el body.

**Solución:** agregar el atributo `operation = @Operation(...)` dentro de cada `@RouterOperation`, con `requestBody`/`responses` apuntando explícitamente al DTO real vía `@Schema(implementation = MenuResponseDTO.class)` (y equivalentes para `MenuCreateRequestDTO`, `MenuUpdateRequestDTO`, `FoodTypeSuggestionResponseDTO`, `OrderCreateRequestDTO`, `OrderResponseDTO`) — mapeo sacado directo de leer `MenuHandler`/`OrderHandler`, no inventado. springdoc introspecciona esas clases por reflexión para construir el schema real (campos, tipos), igual que haría con un DTO de un `@RestController` anotado normal.

## Resultado verificado

```bash
curl -H "auth-token: <jwt>" http://localhost:8080/v3/api-docs
```

Devuelve las 8 rutas reales de `Menu` y las 2 de `Order`, con método HTTP correcto por cada una — listo para importar en ZAP.

## ¿Por qué el JSON generado no vive en el repo?

El archivo `openapi.json` (lo que se ve en pantalla al pedir `/v3/api-docs`, o lo que genera el plugin de abajo) **no se commitea**, a propósito. La distinción que importa:

- **El código (las anotaciones `@RouterOperation`/`@Operation` en `MenuRouterConfig`/`OrderRouterConfig`) es la fuente de verdad.** Vive en el repo porque es la única cosa que alguien edita a mano.
- **El JSON es un derivado** — se reconstruye en cualquier momento pidiéndoselo a la app corriendo (o corriendo el plugin de abajo). Es la misma categoría que un reporte de cobertura de JaCoCo o las clases compiladas en `target/`: un subproducto del build, no algo que se edita directamente.

Commitear el derivado traería un problema real, no teórico: en cuanto alguien agregue o cambie una ruta sin acordarse de regenerarlo, el archivo commiteado queda mintiendo con autoridad — parece la verdad pero no lo es, y nada en el build avisa que se desactualizó. Es peor que no tener el archivo.

Esto no es lo mismo que un golden file (`docs/testing-strategy.md`), que sí se commitea a propósito: la diferencia es que un golden file protege un contrato que cruza un límite hacia un consumidor que no controlás en el mismo deploy (otro equipo, un cliente externo) — ahí vale la pena pagar el costo de mantenerlo sincronizado a mano, porque la alternativa (que el consumidor se entere en producción) es peor. Este spec no tiene ningún consumidor externo todavía — es para ZAP, que lo puede pedir en vivo cuando lo necesite. El día que alguien dependa de este contrato desde otro repo/equipo, ese es el momento de reconsiderar esta decisión, no antes.

## Generación automatizada: `springdoc-openapi-maven-plugin`

Pedir el JSON a mano con `curl` sirve para una mirada rápida, pero no escala a "generarlo en cada build" (que es justo lo que hace falta para alimentar a ZAP en CI más adelante, ver `docs/security/owasp-zap-baseline.md`). Para eso, `pom.xml` tiene un profile nuevo, **no activo por default**:

```bash
mvn verify -Pgenerate-openapi
```

**Por qué un profile aparte y no atado al `verify` normal:** este plugin necesita la app real arrancada de punta a punta (Flyway migrando contra un Postgres real alcanzable en `localhost:5432`, igual que `mvn spring-boot:run`) — no es algo que deba correr en cada build de todos los días, y atarlo al ciclo de vida default arriesgaba romper el `mvn verify` que ya funciona para cualquiera que no tenga un Postgres real levantado en ese momento (a diferencia de los `*IT`, que levantan su propio Postgres efímero vía Testcontainers).

**Cómo funciona:** dos plugins coordinados dentro del profile — `spring-boot-maven-plugin` con los goals `start`/`stop` (arranca la app real en segundo plano, usando `-Dspring.application.admin.enabled=true` para habilitar el canal JMX que el goal `stop` usa después para apagarla) y `springdoc-openapi-maven-plugin` (`generate`, en la fase `integration-test`) que le pide el JSON a la app ya arrancada y lo escribe en `target/openapi.json` — ya ignorado por git, coherente con la sección anterior.

**Verificado paso a paso** (sin correr el `mvn verify` completo, que además dispararía todos los `*IT` con Testcontainers — innecesario para validar solo esto): se invocaron los tres goals por separado (`spring-boot-maven-plugin:start`, `springdoc-openapi-maven-plugin:generate`, `spring-boot-maven-plugin:stop`), confirmando en cada paso que el puerto 8080 se ocupaba/liberaba como se esperaba y que `target/openapi.json` quedó con 4672 bytes y los contratos reales (no el `{}` genérico).

Nota real encontrada verificando contra Maven Central (no contra la documentación de springdoc, que volvió a mencionar una versión que no existe como artefacto publicado): la última versión real de `springdoc-openapi-maven-plugin` es **1.4**, no la 1.5 que aparece en algunos ejemplos de la doc oficial.

## Hallazgo 5: faltaban los parámetros de path (spec inválido para herramientas estrictas)

Encontrado por ZAP, no por nosotros: al importar el spec, tiró warnings tipo `Declared path parameter menuId needs to be defined as a path parameter in path or operation level` para las 5 rutas que tienen `{menuId}` en el path (`getMenu`, `suggestFoodTypeForMenu`, `deleteMenu`, `updateMenu`, `uploadMenuImage`). Causa: la especificación OpenAPI exige que todo parámetro de path mencionado en el template (`{menuId}`) tenga una entrada `parameters` correspondiente (nombre, `in: path`, `required: true`, tipo) — nunca lo declaramos, solo estaba el placeholder en el string del path. Verificado contra el JSON real: cero ocurrencias de `"parameters"` en todo el spec antes del fix.

El JSON "funcionaba" para nuestras propias verificaciones (`curl` devolvía 200, los contratos de request/response se veían bien) porque nada de lo que habíamos probado hasta ahora validaba el spec contra las reglas de OpenAPI en sí — recién una herramienta que sí lo hace (el importador de ZAP) lo detectó.

**Solución:** `parameters = @Parameter(name = "menuId", in = ParameterIn.PATH, required = true, schema = @Schema(type = "string", format = "uuid"))` agregado a cada uno de los 5 `@Operation` afectados en `MenuRouterConfig`.

## Decisión 3: excluir las rutas SSE del spec que se le da a ZAP (sin tocar el código real)

Al importar el spec completo en ZAP, el importador intenta "acceder" a cada ruta como parte de la validación — y se traba en las dos rutas SSE (`/api/v1/menu/stream`, `/api/v1/order/stream`), que por diseño nunca cierran la respuesta. El import terminaba en un estado inconsistente (rutas faltantes o mal ubicadas en el árbol de Sites), y el Active Scan directamente parecía colgarse.

**Decisión:** no tocar `MenuRouterConfig`/`OrderRouterConfig` — esas dos rutas son reales y vale la pena seguir documentándolas para cualquier otro consumidor del spec. En cambio, se genera una **copia filtrada solo para ZAP**, sacando esas dos entradas de `paths` antes de importar. El archivo real (`target/openapi.json`) queda completo; la copia (`target/openapi-for-zap.json`) es desechable, generada a demanda, nunca commiteada (mismo criterio que el resto de `target/`).

Esto es consistente con el límite de OpenAPI en sí: la especificación 3.x no modela bien un stream infinito (lo máximo que permite es decir "200, content-type text/event-stream", sin ninguna semántica real de streaming) — excluirlas de lo que ataca una herramienta de request/response clásica como ZAP no es perder información real, es reconocer que ese tipo de endpoint no encaja en ese modelo para empezar.

## De dónde sale realmente el contenido del spec (y de dónde no)

Pregunta que surgió revisando esto: ¿el spec usa datos reales de la base (ej. nombres de menús reales)? **No, nunca.** Vale la pena ser explícito sobre esto porque no es obvio de afuera:

- **Rutas y métodos** (`GET /api/v1/menu/{menuId}`): texto fijo escrito en las anotaciones `@RouterOperation` de `MenuRouterConfig`/`OrderRouterConfig`.
- **Forma de cada request/response** (ej. que `menuTitle` es un string de hasta 50 caracteres): sale de leer por reflexión la clase Java del DTO real (`MenuResponseDTO`, etc.) — incluyendo las restricciones de Bean Validation que ya tenía el DTO (`@Size(max = 50)` es de donde sale el `maxLength: 50`, no de la columna SQL).
- **Valores de ejemplo** (del tipo `"menuTitle": "Pizza Margherita"`): no hay ninguno en este spec. Si se quisiera alguno, se escribe a mano en el código (`@Schema(example = "...")`) — nunca se "saca" de una fila real de la base.

La base de datos **no aporta nada al contenido**. Entonces, ¿para qué hace falta que la app esté conectada a una base real para generar el spec? Por una razón de arranque, no de contenido: Spring Boot no termina de inicializar el contexto (R2DBC necesita poder conectar, Flyway necesita validar el esquema) sin una base real alcanzable — y hasta que el contexto termina de inicializar, springdoc no tiene nada que inspeccionar todavía. Es un prerequisito técnico para que la app llegue a arrancar, no un insumo de la documentación.

## Por qué aislar la base de todos modos (la razón real, no la que parecía obvia)

Dado el punto anterior, cabía preguntarse: si ningún dato real se expone, ¿tiene sentido insistir en una base descartable en vez de usar el Postgres real de desarrollo? **Sí, pero por un motivo distinto al de "que no se filtren datos".** Dos razones concretas:

1. **Flyway no es de solo lectura.** El paso de arranque (`spring-boot-maven-plugin:start`) corre la app de punta a punta, y si hubiera una migración pendiente, Flyway la aplicaría de verdad contra la base a la que apunte — incluso si la intención era solo "quiero ver la documentación". Usar la base real de desarrollo como efecto colateral de una tarea de documentación es un acoplamiento inesperado, no algo deliberado.
2. **Reproducibilidad.** Este profile tiene que poder correr igual en la máquina de cualquier otra persona, o eventualmente en CI (el destino final, para alimentar a ZAP) — ninguno de esos entornos tiene "el Postgres real de este desarrollador en particular". Depender de él es exactamente el mismo problema que los `*IT` de este proyecto ya resuelven con Testcontainers, aplicado a un nivel distinto (proceso completo, no JVM de test).

## Mecanismo de aislamiento: manual, no Testcontainers (y su fragilidad real, comprobada)

Importante no confundir esto con Testcontainers. Los `*IT` de este proyecto (`AbstractPostgresContainerTest`) usan Testcontainers *desde adentro de la JVM de un test de JUnit* — la librería arranca y para el contenedor automáticamente, atada al ciclo de vida de esa JVM, con un watchdog (`Ryuk`) que garantiza la limpieza incluso si algo sale mal.

Acá no hay ningún test de JUnit en el medio — el profile `generate-openapi` arranca la **aplicación real** como proceso (`spring-boot:start`), así que no hay nada a lo que Testcontainers pueda engancharse. El aislamiento se hace a mano: un `docker run postgres:16-alpine -p 5433:5432 ...` tirado directamente contra el daemon de Docker, sin ninguna supervisión automática, y se tira abajo a mano al final con `docker stop`/`docker rm`.

**Esto se comprobó frágil en la práctica, no en teoría:** mientras se verificaba este mismo profile, la VM de WSL2 se reinició por una actualización en curso del lado de Windows, y el contenedor descartable desapareció por completo (ni siquiera quedó en `docker ps -a`) sin que ningún código se enterara — la app, si hubiera seguido corriendo, habría quedado apuntando a una base que ya no existe, en silencio. Es la clase de fragilidad que Testcontainers (con su watchdog) evita, y que acá no está cubierta porque el mecanismo es manual. Documentado como limitación conocida, no resuelta todavía — candidato a un plugin de Maven que gestione el ciclo de vida del contenedor atado al build (arranca-si-falla-limpia-igual) en vez de comandos sueltos.

## Mantenibilidad a futuro: duplicación entre la ruta real y su anotación

Cada ruta queda definida **dos veces**: una en el `RouterFunction` (`.GET("/{menuId}", this.menuHandler::getMenu)`) y otra en el `@RouterOperation` (`path = "/api/v1/menu/{menuId}", beanMethod = "getMenu"`) — dos fuentes de verdad separadas, sin ninguna relación mecánica entre ellas. Si se agrega un método nuevo a `MenuHandler`/`OrderHandler` sin el `@RouterOperation` correspondiente: compila, los tests pasan, la ruta funciona — y el spec simplemente no la menciona, en silencio.

**Hueco real, sin cerrar todavía.** La solución que encajaría con el proyecto (mismo patrón que `ArchitectureRulesTest` para los límites de `orders.*`): un test que inspeccione por reflexión los métodos públicos de cada handler y verifique que todos tienen un `@RouterOperation` que los referencia como `beanMethod` — así, si alguien agrega un endpoint y se olvida de documentarlo, un test rompe en vez de quedar en silencio. Pendiente de implementar.

## Nota sobre bounded contexts separados (y un futuro de microservicios)

`Menu` y `Orders` conviven en el mismo proceso/deploy (ver `docs/adr/0001-orders-menu-boundary.md`), así que un solo contenedor descartable y una sola corrida de este profile alcanza para documentar ambos de una — springdoc escanea toda la aplicación, no contexto por contexto.

Si algún día estos contextos se separaran en servicios desplegados de forma independiente, generar el spec de uno **nunca** necesitaría que el otro esté corriendo — es una operación local, por reflexión sobre el propio código de ese servicio. Si `Orders` alguna vez necesitara que `Menu` esté vivo para poder describirse a sí mismo, sería señal de que el límite entre los dos contextos está roto — justo lo que el anti-corruption layer y la regla de ArchUnit (`orders.domain` nunca importa `demo.reactividad.domain`) existen para evitar, verificado mecánicamente.

## Verificación

- Tests de `AuthenticationWebFilterTest` nuevos para la excepción de `/v3/api-docs`, rotos a propósito (comentando el chequeo) → fallan como se esperaba → restaurados → verde de nuevo.
- `mvn test` completo en verde después del cambio de dependencia (`webflux-ui` → `webflux-api`) y de nuevo después de agregar los `@Operation` por ruta.
- Confirmado contra la app real: `components.schemas.MenuResponseDTO` (y el resto de los DTOs) aparecen con sus campos reales (`menuId`, `menuTitle`, etc.), ya no el `ServerResponse: {}` genérico del hallazgo 4.
- Verificado en la app real corriendo: `/v3/api-docs` sin token → 200 (pública); `/swagger-ui/*` sin token → 401 (protegida, y ya no rompe con `NoSuchMethodError` porque no se sirve nada ahí); cualquier endpoint real de negocio sin token → 401 (sin cambios).
