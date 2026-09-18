# Arquitectura Hexagonal — Guía rápida (con el ejemplo de este repo)

## La idea en una frase

Separar **reglas de negocio** de **detalles técnicos** (BD, HTTP, frameworks), para poder cambiar cualquier detalle técnico sin tocar el negocio.

## Las 3 capas

```
domain          → QUÉ es el negocio (modelos, reglas, errores de negocio)
application     → QUÉ hace la app (casos de uso) + los "enchufes" (puertos)
infrastructure  → CÓMO se conecta con el mundo real (HTTP, BD, eventos)
```

| Capa | En este repo | Sabe de Spring/BD/HTTP? |
|---|---|---|
| `domain` | `Menu`, `FoodType`, `MenuNotFoundException` | No — son clases Java puras |
| `application` | `MenuUseCases` (puerto), `MenuUseCasesService` (lógica) | No — solo interfaces propias |
| `infrastructure` | `MenuHandler`, `MenuRepositoryAdapter`, filtros, router | Sí — aquí vive todo lo técnico |

**Regla de oro (DIP):** las dependencias siempre apuntan hacia adentro.
`infrastructure → application → domain`. Nunca al revés. `domain` no importa nada de las otras dos capas.

## La pieza clave: los Puertos

Un **puerto** es una interfaz que define un contrato, sin decir cómo se cumple.

- **Puerto de entrada** (`application/port/in/MenuUseCases`): lo que el mundo exterior le puede pedir a la app.
  - Lo *implementa* `MenuUseCasesService` (el caso de uso real).
  - Lo *consume* `MenuHandler` (el adapter web) — no conoce `MenuUseCasesService`, solo la interfaz.
- **Puerto de salida** (`application/port/out/MenuRepositoryPort`, `MenuEventPublisher`): lo que la app necesita del exterior (guardar un menú, publicar un evento), descrito en términos de negocio.
  - Lo *implementa* `MenuRepositoryAdapter` (habla con Postgres) o `ReactorMenuEventPublisher` (habla con un `Sinks.Many` de Reactor).
  - Lo *consume* `MenuUseCasesService` — no sabe que hay un R2DBC o un Sink detrás, solo la interfaz.

Un **adapter** es la implementación concreta de un puerto: el punto donde entra un framework/protocolo específico.

## Flujo real: `GET /api/v1/menu/{id}`

```
HTTP request
   │
   ▼
MenuRouterConfig            (infrastructure/adapter/in/web)   ← enruta
   │
   ▼
MenuHandler                 (infrastructure/adapter/in/web)   ← adapter de entrada
   │  usa el puerto
   ▼
MenuUseCases                (application/port/in)             ← contrato
   │  implementado por
   ▼
MenuUseCasesService         (application/usecase)             ← lógica del caso de uso
   │  usa el puerto
   ▼
MenuRepositoryPort           (application/port/out)           ← contrato
   │  implementado por
   ▼
MenuRepositoryAdapter        (infrastructure/adapter/out)      ← adapter de salida
   │
   ▼
MenuR2dbcRepository → Postgres
```

`MenuUseCasesService` nunca importa `MenuHandler`, `MenuR2dbcRepository` ni nada de `infrastructure`. Solo conoce las interfaces `MenuRepositoryPort`/`MenuEventPublisher`.

## Por qué vale la pena (beneficios concretos, no teóricos)

- **Tests rápidos y sin Docker**: `MenuUseCasesServiceTest` mockea los puertos (`MenuRepositoryPort`, `MenuEventPublisher`) — no levanta Spring ni Postgres, corre en milisegundos.
- **Cambiar de BD sin tocar el negocio**: si mañana migras de Postgres a MongoDB, reescribes solo `infrastructure/adapter/out/persistence`. `domain` y `application` no cambian una línea.
- **Agregar un canal nuevo es aditivo**: si sumas gRPC además de REST, agregas otro adapter en `infrastructure/adapter/in/`, reutilizando el mismo `MenuUseCases` sin duplicar lógica.

## Guía mental para código nuevo

| Pregunta | Va en |
|---|---|
| ¿Es un modelo o regla de negocio? | `domain` |
| ¿Es "cómo se resuelve un caso de uso"? | `application/usecase` |
| ¿Es un contrato entre capas? | `application/port/in` o `/out` |
| ¿Es HTTP, SQL, colas, un framework? | `infrastructure/adapter/...` |

## Las 2 excepciones pragmáticas que tomamos en este repo

La pureza total no siempre vale el costo. Dos decisiones conscientes:

1. **`@Transactional` se quedó en `MenuUseCasesService`** (capa `application`). Es una anotación declarativa fina que marca el límite de una unidad de trabajo del caso de uso — no un acoplamiento real a un motor de base de datos concreto.
2. **`@CircuitBreaker` se movió a `infrastructure`** (`ResilientMenuRepositoryAdapter`, decorando `MenuRepositoryAdapter`). Protege una llamada de I/O (la BD puede fallar), que es un problema de infraestructura — el caso de uso no necesita saber que existe un breaker.

## Glosario corto

- **Puerto**: interfaz que define un contrato entre capas (qué se puede hacer, no cómo).
- **Adapter**: implementación concreta de un puerto, atada a una tecnología específica (Postgres, HTTP, Reactor).
- **Caso de uso**: una acción de negocio completa (`getMenu`, `createMenu`) — vive en `application`.
- **DIP** (Dependency Inversion Principle): las capas externas dependen de las internas mediante interfaces; nunca al revés.
