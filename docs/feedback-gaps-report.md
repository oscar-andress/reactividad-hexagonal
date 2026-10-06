# Reporte: gaps de feedback recibido → cómo se cubrieron en `reactividad-hexagonal`

Este reporte cita textualmente los 6 gaps del feedback de desempeño recibido, y documenta, por cada uno: el estado, las herramientas usadas, los ejercicios realizados y la teoría aplicada. Sigue el orden de prioridad trabajado (F → E → A → D → B → C), definido en `C:\Users\oscar.vega\.claude\plans\parsed-kindling-pixel.md`.

## Resumen

| Gap | Texto del feedback | Estado |
|---|---|---|
| F | Calidad de Software: contract testing, quality gates, prevención de defectos, causa raíz, anti-regresión | 🟡 Parcial (3/4 ejercicios) |
| E | SOLID / trade-offs arquitectónicos / concurrencia e inmutabilidad | ✅ Completo (4/4) |
| A | Arquitectura hexagonal: diseño evolutivo y límites de dominio | ✅ Completo (4/4) |
| D | Resiliencia avanzada: aislamiento de recursos, tuning, observabilidad de negocio | ✅ Completo (4/4) |
| B | Threat modeling y controles de seguridad | ✅ Completo (4/4) |
| C | Gobernanza de IA: validación de modelos y gestión de riesgos | ⬜ Sin empezar (0/4) |

---

## F — "Requiere fortalecer de forma prioritaria el dominio de Calidad de Software, especialmente en contract testing, quality gates, prevención de defectos, análisis de causa raíz y mecanismos para evitar regresiones."

**Estado:** 🟡 Parcial — 3 de 4 ejercicios cerrados.

**Herramientas:** `jacoco-maven-plugin`, `pitest-maven` + `pitest-junit5-plugin`, Jackson 3 (`tools.jackson.databind`), JUnit 5, Mockito (`ArgumentCaptor`), Logback (`ListAppender`, usado como parche temporal).

**Ejercicios realizados:**
1. **Coverage gate** — `jacoco-maven-plugin` con dos umbrales (90% sobre `domain`/`application`, 60% overall), atado a la fase `test`, más un gate adicional en `verify` que también ve lo cubierto por los `*IT`.
2. **Mutation testing** — PIT corrido sobre `domain.*`/`application.*`. Encontró un mutante `SURVIVED` real en `withPresignedImageUrl` (un `return null` tapado por un efecto secundario sobre `Menu` mutable) y un segundo hallazgo `NO_COVERAGE` en `MenuException.getErrorCode()`.
3. **Contract/regression pinning** — golden-file tests (`MenuResponseDTOTest`, `OrderResponseDTOTest`, `ErrorResponseTest`) comparando por igualdad estructural completa contra archivos reales en `src/test/resources/golden/`, con un helper compartido (`GoldenFileAssertions`); más el pin reforzado del `PutObjectRequest` armado hacia S3 en `S3ImageStorageAdapterTest`.

**Pendiente:** ejercicio 4 — CI (GitHub Actions corriendo `mvn verify`, publicando reportes de JaCoCo/PIT) + nota de RCA retroactiva sobre el bug de `menu_created_at NOT NULL`.

**Teoría aplicada:** cobertura de líneas vs. mutation score (cada una detecta un hueco distinto); la distinción `SURVIVED` (test débil) vs. `NO_COVERAGE` (ausencia total de test); causa raíz vs. parche explícitamente etiquetado (el `ListAppender` se documentó como parche, y se quitó al resolver la causa raíz en el Gap E); qué es un "pin" y un golden file; el criterio de cuándo vale la pena un contract test (cruza un límite externo + un test de comportamiento no lo detectaría); y la distinción entre un golden-file interno y Contract Testing consumer-driven (Pact/Spring Cloud Contract), reservado para cuando productor y consumidor se despliegan de forma independiente.

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

**Estado:** ⬜ Sin empezar — 0 de 4 ejercicios.

**Planeado (capstone del roadmap, no iniciado):** `FoodTypeClassifierPort`/`LlmFoodTypeClassifierAdapter` para sugerir un `FoodType` vía LLM, con gobernanza de entrada (defensa contra prompt injection, plantilla estricta), gobernanza de salida (validar la respuesta cruda contra el catálogo cerrado antes de cruzar el puerto), límites de costo/riesgo (`@TimeLimiter` + `@RateLimiter` reutilizando el Gap D), y human-in-the-loop (la sugerencia nunca se auto-aplica, requiere un `PUT` explícito).

---

## Pendientes generales, fuera de los 6 gaps

- Varios `*IT` (`OrderR2dbcRepositoryIT`, extensiones de `MenuWebIntegrationIT`/`OrderWebIntegrationIT` en los Gaps A/B) se escribieron pero nunca se corrieron con `mvn verify` en este sandbox (sin Docker) — pendiente confirmarlos en una máquina/CI con Docker disponible.
