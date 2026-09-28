# Guía de Desarrollo y Estándares de Ingeniería

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

## 2. Testing y Calidad
- Cada nuevo caso de uso o lógica de negocio debe incluir sus pruebas unitarias.
- Mocks y stubs solo en las fronteras de infraestructura (I/O, APIs externas, persistencia).
- Cubrir casos de éxito y caminos de fallo/borde.

### 2.1 Organización y tipos de test
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

## 3. Modo de Trabajo de Claude
- **Al crear código:** 
  1. Diseña primero las interfaces/contratos (Dominio).
  2. Implementa la lógica del caso de uso.
  3. Crea las pruebas unitarias.
  4. Agrega los adaptadores de infraestructura necesarios.
- **Al revisar código (Code Review):**
  1. Identifica violaciones a SOLID o Clean Architecture.
  2. Clasifica los hallazgos por severidad.
  3. Propón la versión refactorizada y sus tests.