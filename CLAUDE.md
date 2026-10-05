# Guía de Desarrollo y Estándares de Ingeniería

Este documento tiene dos partes. La **Parte 1** es conocimiento de ingeniería de software general — no depende de este proyecto ni de su stack, y está pensada para copiarse tal cual como punto de partida de un `CLAUDE.md` nuevo en cualquier otro repositorio. Formato: **Regla** (DEBE/NUNCA/PREFERÍ) + **Criterio de decisión** + **Convención técnica** — sin prosa narrativa ni ejemplos largos; la teoría y los ejemplos completos viven en `docs/*.md` (Parte 2) y no se copian a otro proyecto. La **Parte 2** es la instanciación concreta de estos principios en `reactividad-hexagonal`.

---

# PARTE 1 — Universal (aplica a cualquier stack o proyecto)

## 1. Arquitectura y Principios
- **Arquitectura:** Clean Architecture / Hexagonal (separación estricta entre Dominio, Casos de Uso/Aplicación e Infraestructura).
- **Principios:** Cumplimiento riguroso de SOLID.
  - SRP: Funciones y clases pequeñas con responsabilidad única.
  - DIP: La lógica de negocio no depende de frameworks ni bases de datos; usa interfaces/puertos.
- **Clean Code:**
  - Nombres reveladores en inglés (métodos = verbos, clases = sustantivos).
  - Uso de Early Returns (Guard Clauses) para evitar anidaciones profundas.
  - Cero valores mágicos; usa constantes o enumeraciones.
  - Manejo controlado de errores con excepciones o tipos de dominio específicos.
  - Código simple y mínimo para lograr el objetivo: evitar sobreingeniería, abstracciones prematuras o funcionalidad no solicitada.

## 2. Disciplina de verificación
- **DEBE** romperse a propósito antes de confiar en un fix/test/gate nuevo: revertirlo, confirmar que falla donde se espera, restaurarlo. *Criterio:* un mecanismo que nunca viste fallar no es confiable.
- **DEBE** verificarse empíricamente el comportamiento real de una librería/framework antes de diseñar sobre él (bytecode, logging verboso, árbol de dependencias real). **NUNCA** asumir por "compila" o "no tiró error".
- **DEBE** etiquetarse explícitamente un parche como tal ("parche, no causa raíz") cuando el fix completo excede el alcance actual — revisitarlo cuando llegue ese alcance.
- **NUNCA** tratar cobertura de líneas, mutation score y "tests en verde" como intercambiables. *Criterio:* cada uno detecta un tipo de hueco distinto; ninguno sustituye a los otros dos.

## 3. Testing: árbol de decisión
- **P1 — ¿objetivo?** Probar mi lógica → unitario (mockear lo externo). Probar una integración real → test de integración, sin mockear justo lo que se integra.
- **P2 (si unitario) — ¿depende de un proxy/contenedor de framework (AOP, DI)?** Sí → levantar el contenedor real para el test. No → instanciar a mano, sin pagar ese costo.
- **P3 (por cada colaborador) — ¿la llamada sale del proceso (red/disco/otro servicio)?** Sí → frontera de infraestructura, mockear (o usar la pieza real en un test de integración). No → usar el objeto real, aunque sea de terceros. *Prueba mental:* 10.000 llamadas en loop — ¿necesito algo externo corriendo?
- **Si se mockea:** con contenedor real, el mock **DEBE** estar gestionado por el contenedor (no solo por la librería de mocking) — un mock que el contenedor no conoce no sustituye al componente real para otros componentes reales que dependen de él (ej. Spring: `@MockitoBean`, no `@Mock` suelto).
- **Fixture:** estado conocido y repetible como punto de partida de un test — datos reutilizables con defaults, y/o el proceso de setup/teardown antes y después de cada test.
- **PREFERÍ** un Test Data Builder sobre literales repetidos cuando construir un tipo válido aparece duplicado en ≥2 tests. *Criterio:* si el tipo gana un campo, se actualiza un solo lugar.

## 4. Diseño de dominio
- **Mutabilidad:** value object de dominio → inmutable. Builder/acumulador → mutable está bien. Objeto forzado por un framework de persistencia → mutable es una concesión a infraestructura, no un diseño. *Criterio:* ¿quién es el dueño del cambio de estado?
- **Convención de inmutabilidad:** campos `final`, sin setters, un único constructor canónico (todos los campos), cualquier "cambio" devuelve una instancia nueva. Un solo constructor evita que un método de modificación olvide un campo.
- **DEBE** revisarse, al inmutabilizar algo mutable, cualquier stub de test basado en identidad de referencia (`==`) — reemplazar por matcher laxo (`any()`) + capturador de argumentos para verificar el contenido.
- **PREFERÍ** un value object sobre un primitivo crudo cuando ese primitivo representa un concepto de negocio específico (primitive obsession). *Criterio:* si dos conceptos distintos comparten el mismo primitivo, invertir el orden de argumentos compila igual y produce un bug silencioso; con value objects distintos, no compila.
- **Criterio — slice de aplicación propio:** el concepto tiene casos de uso independientes, pero sigue siendo la misma área de negocio.
- **Criterio — bounded context separado:** el concepto podría evolucionar o mantenerse de forma genuinamente independiente del resto — requiere aislamiento verificado mecánicamente (ej. reglas de arquitectura automatizadas), no solo organizativo.
- **DEBE** traducirse tanto el éxito como el fallo en un anti-corruption layer. *Criterio:* dejar pasar sin traducir una excepción/tipo del otro contexto (aunque sea solo para capturarla) ya es una fuga del límite.
- **Convención — ADR:** decisión con trade-offs reales → contexto + ≥2 opciones con pros/contras + decisión + consecuencias conscientemente aceptadas.

## 5. Resiliencia
| Patrón | Pregunta que responde |
|---|---|
| Retry | ¿Fallo pasajero? Reintentemos. |
| Circuit Breaker | ¿Viene fallando mucho? Dejemos de llamarlo un rato. |
| Bulkhead | ¿Cuántas llamadas simultáneas permito a este recurso? |
| Time Limiter | ¿Cuánto espero como máximo antes de darme por vencido? |

- **DEBE** verificarse empíricamente que un mecanismo dependiente de proxy/AOP se activa de verdad (ver Sección 2) — que compile o que un test con `new X(...)` pase no lo prueba.
- **DEBE** envolverse la llamada de un Retry en una construcción diferida (lazy) para que cada intento la reinvoque de verdad. *Criterio:* Circuit Breaker y Time Limiter no lo necesitan — no reintentan, solo traducen un fallo o cancelan una espera.
- **DEBE** aislarse cada recurso/consumidor en un carril nombrado e independiente. *Criterio:* una saturación en un carril no debe filtrarse a otro que no depende de él.

## 6. Seguridad
- **Convención — STRIDE** (Spoofing / Tampering / Repudiation / Information Disclosure / Denial of Service / Elevation of Privilege) como checklist de scoping, no auditoría exhaustiva. Cada hallazgo → un fix con test que lo cierre, o queda documentado explícitamente como fuera de alcance.
- **DEBE** validarse en el borde de entrada con error estructurado (4xx claro). **NUNCA** duplicar una validación que el dominio ya garantiza como invariante.
- **NUNCA** secretos/tokens estáticos en código fuente. **DEBE** ser firmado, de corta duración, verificable (firma + expiración) y revocable sin redeploy.
- **DEBE** tener rate limiting carriles separados por endpoint/perfil de costo. *Convención de test:* ráfagas disparadas concurrentemente, nunca en secuencia — un test secuencial es flaky bajo carga variable de la máquina.

## 7. Observabilidad orientada a negocio
- **PREFERÍ** un contador dedicado sobre un log cuando la pregunta es agregada ("¿cuánto pasó esto en total?") en vez de puntual ("¿qué pasó en este caso?").
- *Convención de test:* un contador no existe hasta el primer incremento — comprobar que no fue creado, no que vale cero.

## 8. Modo de Trabajo de Claude
- **Al crear código:**
  1. Diseña primero las interfaces/contratos (Dominio).
  2. Implementa la lógica del caso de uso.
  3. Crea las pruebas unitarias.
  4. Agrega los adaptadores de infraestructura necesarios.
- **Al revisar código (Code Review):**
  1. Identifica violaciones a SOLID o Clean Architecture.
  2. Clasifica los hallazgos por severidad.
  3. Propón la versión refactorizada y sus tests.

---

# PARTE 2 — Específico de este proyecto (`reactividad-hexagonal`)

Instanciación concreta de los principios de la Parte 1 en este repositorio. No se copia a un proyecto nuevo — describe decisiones y herramientas puntuales de este stack (Spring Boot WebFlux + R2DBC + resilience4j + Nimbus JOSE+JWT + JaCoCo + PIT + ArchUnit). El detalle completo de cada tema, con código real y verificaciones paso a paso, vive en `docs/*.md`.

## 2.1 Organización y tipos de test
`src/test/java` espeja exactamente el árbol de paquetes de `src/main/java` (un test por clase de producción, mismo paquete) — no se usan subcarpetas por tipo de test (`unit/`, `integration/`); el tipo se distingue por el sufijo de la clase.

| Capa / Alcance | Componentes (ejemplo real) | Tipo de test | Qué cubre | Qué NO cubre | Dependencias externas | Cómo se ejecuta |
|---|---|---|---|---|---|---|
| **Regla transversal** (todas las capas) | — | Sufijo `*Test` = unitario, sufijo `*IT` = integración (convención Maven Failsafe) | — | Forzar "unitario" mockeando 4+ colaboradores para aislar una clase — revisa si viola SRP antes de forzar el test | — | `mvn test` corre solo `*Test`; `mvn verify` corre `*Test` + `*IT`. Nunca mezclar: una clase con BD/Docker real nunca lleva sufijo `Test` |
| **Domain** (Núcleo) | `Menu`, lógica como `withUpdatedDetails` | Unitario puro (`*Test`) | Reglas de negocio del propio objeto de dominio | Getters/setters de Lombok, records sin lógica | Ninguna — cero mocks, cero frameworks | `mvn test` (Surefire), milisegundos |
| **Application** (Casos de Uso) | `MenuUseCasesService` + puertos in/out | Unitario (`*Test`) | Orquestación del caso de uso: validaciones, mapeo de errores, secuencia de llamadas a los puertos | Que Spring resuelva bien un `@Bean`; lógica interna de los adaptadores reales | Solo mocks de los Output Ports (`MenuRepositoryPort`, `ImageStoragePort`...) | `mvn test`, sin Spring ni BD |
| **Adapters con lógica propia** | `S3ImageStorageAdapter`, `ResilientMenuRepositoryAdapter`, mappers | Unitario (`*Test`) | Que tu código arme correctamente la request/mapeo hacia el SDK o el delegate | Que el I/O real funcione (fila de abajo) | Mock del colaborador inmediato (SDK client, delegate) | `mvn test`, sin red real |
| **Adapters sin lógica propia** (repos Spring Data) | `MenuR2dbcRepository` | Integración (`*IT`) | Que el mapeo objeto-relacional y el esquema real funcionen (constraints, tipos) — ver bug de `menu_created_at NOT NULL`, solo detectable así | — (no hay lógica propia que aislar con mocks) | Postgres real vía Testcontainers (`AbstractPostgresContainerTest`) | `mvn verify` (Failsafe) |
| **Entrypoints — routing/wiring** | `MenuRouterConfig` | Unitario (`*Test`) | Que las rutas resuelvan bien los path variables y deleguen al handler correcto | Seguridad real, serialización JSON real (fila de abajo) | Mock de `MenuUseCases` + `WebTestClient.bindToRouterFunction` | `mvn test`, sin Spring context ni Docker |
| **Entrypoints — flujo completo** | `MenuHandler` + filtros de seguridad + serialización | Integración (`*IT`) | Flujo HTTP real de punta a punta dentro del proceso: auth, JSON, persistencia | — | `@SpringBootTest` + `WebTestClient` + Postgres real | `mvn verify` |
| **Sistema Completo** (fuera del hexágono) | HTTP → S3/Postgres reales, desplegado | E2E | El sistema como lo ve un cliente externo | — | Entorno desplegado/contenerizado completo | No aplica hoy en este proyecto |

Spring resuelve la distinción "mock plano vs. mock gestionado por el contenedor" (Parte 1, Sección 3) con `@Mock`/`new X(mock)` para el primer caso y `@MockitoBean` para el segundo — ver `docs/testing-strategy.md` para la tabla completa con las combinaciones reales usadas en este proyecto.

## 2.2 Stack y herramientas de calidad
- **Framework:** Spring Boot WebFlux (programación reactiva, `Mono`/`Flux` de Project Reactor) + R2DBC (acceso no bloqueante a Postgres).
- **NUNCA** invocar operaciones bloqueantes (`.block()`/`.blockFirst()`/`.blockLast()`, I/O síncrono, `Thread.sleep()`) dentro de un pipeline reactivo. *Criterio:* el event loop de Netty corre con un pool chico de hilos compartidos entre todas las requests — bloquear uno degrada o cuelga en cascada al resto de la aplicación, no solo a la request actual.
- **Resiliencia:** `resilience4j-spring-boot3` + `aspectjweaver` (el starter `spring-cloud-starter-circuitbreaker-reactor-resilience4j` por sí solo **no** activa las anotaciones `@Retry`/`@Bulkhead`/`@CircuitBreaker`/`@TimeLimiter`/`@RateLimiter` — trae solo la integración funcional de Spring Cloud, no los aspectos de Spring AOP que interceptan esas anotaciones; ver `docs/resilience.md`).
- **Seguridad:** sin `spring-boot-starter-security` — autenticación/autorización implementadas como dos `WebFilter` a medida (`AuthenticationWebFilter`, `AuthorizationWebFilter`); JWT firmado con Nimbus JOSE+JWT (HS256, corta duración), ver `docs/security/jwt-authentication.md`.
- **Validación de entrada:** `jakarta.validation` (Bean Validation) aplicada a mano en el `RouterFunction` (el estilo funcional de WebFlux no dispara `@Valid` solo, a diferencia de `@RestController`), ver `docs/security/bean-validation.md`.
- **Quality gates:** `jacoco-maven-plugin` con dos umbrales — 90% de líneas sobre `domain.*`/`application.*` (y sus equivalentes en `orders.*`), 60% overall — atados a la fase `test` para feedback rápido, con un gate adicional en `verify` que ya ve también lo cubierto por los `*IT`. Ver `docs/quality-gates.md`.
- **Mutation testing:** `pitest-maven` sobre `domain.*`/`application.*` (y `orders.domain.*`/`orders.application.*`), corrido a demanda (`mvn org.pitest:pitest-maven:mutationCoverage`), no atado a ninguna fase del build por su costo. Ver `docs/mutation-testing.md`.
- **Límites de dominio:** `com.tngtech.archunit` (`ArchitectureRulesTest`) verifica mecánicamente que `demo.reactividad.orders.*` nunca importe `demo.reactividad.domain`/`demo.reactividad.application`, y que nunca dependa de `MenuRepositoryPort` directamente (debe pasar por `MenuUseCases` vía el anti-corruption layer `MenuLookupPortAdapter`). Ver `docs/hexagonal-boundaries.md`.
- **Observabilidad:** Micrometer (vía `spring-boot-starter-actuator`), contadores de negocio expuestos en `/actuator/metrics/...`. Ver `docs/observability.md`.

## 2.3 Bounded contexts del proyecto
- `demo.reactividad.*` — contexto `Menu` (dominio original).
- `demo.reactividad.orders.*` — contexto `Orders`, con su propio vertical slice completo (domain/application/infrastructure, incluyendo HTTP + SSE), que referencia `Menu` solo a través de `MenuId` (value object) y un anti-corruption layer (`MenuLookupPort`/`MenuSnapshot`). Ver `docs/hexagonal-boundaries.md` y `docs/adr/0001-orders-menu-boundary.md` para el trade-off documentado entre lectura síncrona (la opción elegida) y snapshot dirigido por eventos (considerada, no implementada).

## 2.4 Modo de Trabajo

Ver Parte 1, Sección 8 — aplica igual en este proyecto, sin ninguna particularidad adicional.
