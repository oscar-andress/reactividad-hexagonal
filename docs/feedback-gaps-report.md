# Reporte: gaps de feedback recibido → cómo se cubrieron en `reactividad-hexagonal`

Este reporte cita textualmente los 6 gaps del feedback de desempeño recibido, y documenta, por cada uno: el estado, las herramientas usadas, los ejercicios realizados y la teoría aplicada. Sigue el orden de prioridad trabajado (F → E → A → D → B → C), definido en `C:\Users\oscar.vega\.claude\plans\parsed-kindling-pixel.md`.

## Resumen

| Gap | Texto del feedback | Estado |
|---|---|---|
| F | Calidad de Software: contract testing, quality gates, prevención de defectos, causa raíz, anti-regresión | ✅ Completo (4/4) |
| E | SOLID / trade-offs arquitectónicos / concurrencia e inmutabilidad | ✅ Completo (4/4) |
| A | Arquitectura hexagonal: diseño evolutivo y límites de dominio | ✅ Completo (4/4) |
| D | Resiliencia avanzada: aislamiento de recursos, tuning, observabilidad de negocio | ✅ Completo (4/4) |
| B | Threat modeling y controles de seguridad | ✅ Completo (4/4) |
| C | Gobernanza de IA: validación de modelos y gestión de riesgos | ✅ Completo (4/4) |

---

## F — "Requiere fortalecer de forma prioritaria el dominio de Calidad de Software, especialmente en contract testing, quality gates, prevención de defectos, análisis de causa raíz y mecanismos para evitar regresiones."

**Estado:** ✅ Completo — 4 de 4 ejercicios.

**Herramientas:** `jacoco-maven-plugin`, `pitest-maven` + `pitest-junit5-plugin`, Jackson 3 (`tools.jackson.databind`), JUnit 5, Mockito (`ArgumentCaptor`), Logback (`ListAppender`, usado como parche temporal), GitHub Actions, Testcontainers.

**Ejercicios realizados:**
1. **Coverage gate** — `jacoco-maven-plugin` con dos umbrales (90% sobre `domain`/`application`, 60% overall), atado a la fase `test`, más un gate adicional en `verify` que también ve lo cubierto por los `*IT`.
2. **Mutation testing** — PIT corrido sobre `domain.*`/`application.*`. Encontró un mutante `SURVIVED` real en `withPresignedImageUrl` (un `return null` tapado por un efecto secundario sobre `Menu` mutable) y un segundo hallazgo `NO_COVERAGE` en `MenuException.getErrorCode()`.
3. **Contract/regression pinning** — golden-file tests (`MenuResponseDTOTest`, `OrderResponseDTOTest`, `ErrorResponseTest`) comparando por igualdad estructural completa contra archivos reales en `src/test/resources/golden/`, con un helper compartido (`GoldenFileAssertions`); más el pin reforzado del `PutObjectRequest` armado hacia S3 en `S3ImageStorageAdapterTest`.
4. **CI + RCA** — `.github/workflows/ci.yml` corriendo `mvn verify` + PIT en cada PR, con artifacts. En el camino de verificarlo contra Docker real por primera vez, se encontró y arregló un bug real preexistente: `AbstractPostgresContainerTest` compartía un contenedor `static` entre las 4 clases `*IT` (bug mecánico de Java — campo `static` en una clase abstracta es uno solo, no uno por subclase), invisible hasta entonces porque nunca se había podido correr `mvn verify` con Docker en este entorno. Documentado en `docs/rca/0001-shared-testcontainer-across-it-classes.md`, con el gate verificado rojo→verde en 3 runs reales de GitHub Actions.

**Teoría aplicada:** cobertura de líneas vs. mutation score (cada una detecta un hueco distinto); la distinción `SURVIVED` (test débil) vs. `NO_COVERAGE` (ausencia total de test); causa raíz vs. parche explícitamente etiquetado (el `ListAppender` se documentó como parche, y se quitó al resolver la causa raíz en el Gap E); qué es un "pin" y un golden file; el criterio de cuándo vale la pena un contract test (cruza un límite externo + un test de comportamiento no lo detectaría); la distinción entre un golden-file interno y Contract Testing consumer-driven (Pact/Spring Cloud Contract); qué es un RCA (síntoma → línea de tiempo → causa raíz → fix → verificación → prevención) y por qué un campo `static` en una clase abstracta se comparte entre subclases en Java.

---

## E — "Necesita profundizar en la aplicación contextual de principios SOLID, análisis de trade-offs arquitectónicos, concurrencia e inmutabilidad."

**Estado:** ✅ Completo — 4 de 4 ejercicios.

**Herramientas:** Lombok, Project Reactor (`Sinks`, `EmitResult`, `flatMap`), `AtomicLong`, `StepVerifier.withVirtualTime`.

**Ejercicios realizados:**
1. **Inmutabilizar `Menu`** — se quitó `@Setter`, se agregaron los `with*` faltantes (`withFoodTypes`, `withImageKey`, `withImageUrl`) junto al ya existente `withUpdatedDetails`, con un único constructor canónico de 8 argumentos.
2. **Inmutabilizar `FoodType`** — se confirmó por `grep` que ningún sitio real llamaba a sus setters, y se redujo a campos `final` sin agregar métodos especulativos.
3. **Arreglar el silent-failure de concurrencia** — `ReactorMenuEventPublisher.publish` pasó de `tryEmitNext` sin verificar a `emitNext` con `EmitFailureHandler.busyLooping` + conteo de fallos (`AtomicLong`).
4. **DIP + concurrencia explícita** — se extrajo `MenuBatchFailurePolicy` (inyectado) desde `MenuUseCasesService.createMenus`, y se nombró explícitamente el límite de concurrencia del `flatMap` (antes era el default oculto de Reactor, 256).

**Teoría aplicada:** cuándo un objeto debe ser inmutable (value object de dominio) vs. mutable (builder, entidad forzada por un framework); constructor canónico único como garantía de no "olvidar" un campo; el efecto colateral de inmutabilizar sobre tests que dependían de identidad de referencia (`==`) en Mockito; stream caliente vs. frío y qué es un `Sink`; busy loop como estrategia de reintento; DIP aplicado para extraer una política de manejo de errores a un colaborador inyectado.

---

## A — "Puede fortalecer arquitectura hexagonal, diseño evolutivo y definición de límites de dominio."

**Estado:** ✅ Completo — 4 de 4 ejercicios.

**Herramientas:** ArchUnit, R2DBC/Postgres (esquema sin foreign key entre contextos), `record` de Java.

**Ejercicios realizados:**
1. **Bounded context `Orders`** — `Order` inmutable, `MenuId` como `record` (value object), vertical slice propio (domain/application/infrastructure).
2. **Anti-corruption layer** — `MenuLookupPort`/`MenuSnapshot`, implementado por `MenuLookupPortAdapter`, traduciendo tanto el éxito como el fallo (`MenuNotFoundException` → ausencia neutral).
3. **ADR** — `docs/adr/0001-orders-menu-boundary.md`, comparando lectura síncrona (elegida) vs. snapshot dirigido por eventos (descartada, con un bloqueo real de infraestructura como evidencia: `replay().limit(1)`).
4. **Vertical slice completo** — `OrderHandler`/`OrderRouterConfig` con `POST /api/v1/order/` y `GET /api/v1/order/stream`.

**Teoría aplicada:** primitive obsession (por qué `MenuId` y no un `UUID` plano); el criterio de 3 niveles para decidir si un concepto necesita slice de aplicación propio o un bounded context separado; anti-corruption layer como traducción bidireccional (éxito y fallo); ADR como artefacto de decisión con trade-offs documentados; verificación de límites de arquitectura rompiendo a propósito (agregar un campo `Menu` dentro de `OrderUseCasesService` y confirmar que ArchUnit lo detecta).

---

## D — "Presenta oportunidades de mejora en resiliencia avanzada, aislamiento de recursos, tuning de políticas y observabilidad orientada al impacto del negocio."

**Estado:** ✅ Completo — 4 de 4 ejercicios.

**Herramientas:** `resilience4j-spring-boot3` + `aspectjweaver`, Micrometer (vía `spring-boot-starter-actuator`).

**Ejercicios realizados:**
1. **Tuning de políticas** — `@Retry` (backoff exponencial) + `@Bulkhead` en los write paths de `ResilientMenuRepositoryAdapter`. Se encontró que `@CircuitBreaker` llevaba tiempo sin activarse de verdad (faltaba la dependencia que registra los aspectos de Spring AOP), y que `@Retry` necesitaba `Mono.defer` para poder reinvocar la operación real en cada intento.
2. **Aislamiento de recursos entre contextos** — `@Bulkhead` propio y nombrado (`orders-menu-lookup`) en `MenuLookupPortAdapter`, carril separado del de `Menu`, verificado con un stub lento que satura ese carril sin afectar al resto de `Orders`.
3. **Límite de I/O externo** — `@TimeLimiter` en `S3ImageStorageAdapter.upload`/`generatePresignedUrl`.
4. **Observabilidad de negocio** — contadores Micrometer reales en `MenuUseCasesService.compensateImageUpload` (limpieza de imagen huérfana) y `LoggingMenuBatchFailurePolicy`, expuestos en `/actuator/metrics`.

**Teoría aplicada:** los 4 patrones de resiliencia y la pregunta que responde cada uno (Retry, Circuit Breaker, Bulkhead, Time Limiter); por qué las anotaciones de resilience4j necesitan un proxy de Spring AOP y hay que verificar empíricamente que se activan; Bulkhead como "mamparo" que aísla carriles por consumidor; contador de negocio acumulado vs. log puntual.

---

## B — "Requiere mayor profundidad en threat modeling, controles de seguridad."

**Estado:** ✅ Completo — 4 de 4 ejercicios.

**Herramientas:** Nimbus JOSE+JWT, `jakarta.validation` (Bean Validation), `resilience4j` `@RateLimiter`.

**Ejercicios realizados:**
1. **Threat model STRIDE** — `docs/security/threat-model.md`, mapeando cada hallazgo 1:1 a un ejercicio, y documentando explícitamente los hallazgos que quedaron fuera de alcance.
2. **Bean Validation** en `MenuCreateRequestDTO`/`MenuUpdateRequestDTO`, conectada a mano en el `RouterFunction` (el estilo funcional de WebFlux no dispara `@Valid` solo).
3. **JWT firmado** (HS256, corta duración) reemplazando el mapa de tokens hardcodeado (`AUTH_CATEGORY_MAP`).
4. **Rate limiting** en `POST /million` y `POST /{menuId}/image`, con `RequestNotPermitted` mapeado a HTTP 429.

**Teoría aplicada:** STRIDE como checklist de scoping (no auditoría exhaustiva); validar en el borde con error estructurado sin duplicar lo que el dominio ya garantiza; tokens estáticos vs. firmados de corta duración; rate limiting como control de seguridad explícito contra DoS; el hallazgo de que un test de ráfaga secuencial es inherentemente flaky bajo carga variable, y que hay que dispararlo concurrentemente.

---

## C — "Debe reforzar criterios de gobernanza, validación de modelos y gestión de riesgos en el uso de IA."

**Estado:** ✅ Completo — 4 de 4 ejercicios.

**Herramientas:** cliente de LLM simulado (`StubChatCompletionClient`, decisión explícita — sin proveedor real ni costo/credenciales), `resilience4j` (`@TimeLimiter`+`@RateLimiter`), Micrometer, Spring Data R2DBC (`@Query` en un repositorio dedicado para la tabla puente).

**Ejercicios realizados:**
1. **Gobernanza de entrada** — `LlmFoodTypeClassifierAdapter` arma el prompt desde una plantilla estricta (solo título/descripción, HTML/script removido, truncado a 200 caracteres), con los candidatos de `FoodType` como lista cerrada explícita en el propio prompt.
2. **Gobernanza de salida** — la respuesta cruda del LLM se valida por coincidencia exacta (case-insensitive) contra el catálogo cerrado antes de cruzar el puerto; cualquier otra cosa (nombre alucinado, JSON malformado, vacío) termina en `UnsafeAiResponseException`, contada en un contador de Micrometer y nunca propagada como error ruidoso.
3. **Límites de costo/riesgo** — `@TimeLimiter`(3s)/`@RateLimiter`(5 req/s) sobre la llamada al clasificador, reutilizando los patrones del Gap D.
4. **Human-in-the-loop** — `GET /{menuId}/suggest-food-type` solo lee, nunca escribe. Se encontró que el `PUT` existente no podía aplicar ninguna sugerencia (no soportaba asignar food types a un menú) — se extendió explícitamente (`MenuUpdateRequestDTO.foodTypeIds`) para que la verificación fuera real de punta a punta, no solo "la sugerencia no escribe" aislado.

**Teoría aplicada:** por qué un vehículo de práctica (la feature de IA) no necesita un proveedor real para enseñar los patrones de gobernanza que importan; defensa en profundidad (plantilla estricta + saneado + presupuesto de caracteres en la entrada, validación por lista cerrada en la salida — ninguna sola alcanza); por qué el campo `static @Container` de `AbstractPostgresContainerTest` no aplicaba al nuevo `*IT` de este gap de la misma forma que a los demás (ya resuelto en Gap F); reutilización de los 4 patrones de resiliencia del Gap D en un contexto nuevo (I/O hacia un LLM en vez de hacia S3/Postgres).

---

## Pendientes generales, fuera de los 6 gaps

Ninguno — con Docker disponible (vía WSL2, confirmado en el Gap F), se corrió `mvn verify` completo contra Postgres real para todos los `*IT` de todos los gaps, sin excepciones.
