# ADR 0001: Cómo `Orders` lee datos de `Menu` a través del límite de contexto

## Status

Aceptado (2026-10-02).

## Contexto

`Orders` necesita saber el título de un menú al momento de crear un pedido, para guardarlo como snapshot (`Order.menuTitleSnapshot`) — si el menú cambia de nombre después, el pedido histórico debe seguir mostrando el nombre que tenía cuando se pidió. `Orders` no debe depender de las clases de dominio de `Menu` (ver `docs/hexagonal-boundaries.md`), así que hace falta decidir **cómo** cruza esa información el límite entre los dos bounded contexts.

Se evaluaron dos diseños.

## Opción A — `MenuLookupPort` síncrono (la elegida)

`OrderUseCasesService.createOrder` llama, como parte de su propio flujo reactivo, a un puerto (`MenuLookupPort`) que por dentro invoca `MenuUseCases.getMenu(...)` en el momento mismo de crear el pedido, y traduce el resultado (éxito o "no encontrado") a tipos propios de `Orders` (`MenuSnapshot`, ausencia). Implementado en `MenuLookupPortAdapter` (ver `docs/hexagonal-boundaries.md`, ejercicio 2).

**A favor:**
- Sin piezas nuevas de infraestructura — ni tabla extra, ni suscriptor, ni proceso de sincronización.
- Sin ventana de inconsistencia: el título que se guarda es el real, leído en el instante exacto en que se crea el pedido.
- Simple de testear: un mock de `MenuLookupPort`/`MenuUseCases`, sin necesidad de simular el paso del tiempo ni condiciones de carrera (`MenuLookupPortAdapterTest`, ya escrito).

**En contra:**
- Acopla la *disponibilidad* de `Orders` a la de `Menu` en el momento de crear un pedido: si `Menu` está lento o caído, `createOrder` también lo está, aunque el código de ambos contextos siga totalmente desacoplado.
- Agrega latencia a cada `createOrder` (una llamada adicional a la pila completa de `Menu`: caso de uso, circuit breaker, R2DBC).
- Si `Orders` y `Menu` alguna vez se separan en servicios desplegables distintos, esta llamada síncrona se convertiría en una llamada de red — el acoplamiento de disponibilidad se volvería mucho más caro.

## Opción B — snapshot dirigido por eventos (considerada, no implementada)

`Orders` mantendría su propia tabla local de snapshots de menú, actualizada de forma asíncrona suscribiéndose al stream de eventos de `Menu` (`ReactorMenuEventPublisher.subscribe()`, el mismo que alimenta el SSE de `streamMenus()`). `createOrder` solo leería de esa copia local — sin ninguna llamada síncrona a `Menu` en el camino caliente.

**A favor:**
- Desacopla disponibilidad: `Orders` puede seguir creando pedidos aunque `Menu` esté caído, siempre que su copia local ya tenga el dato.
- Latencia más baja y predecible para `createOrder` (solo lectura local).
- Escala naturalmente a una futura separación física en servicios distintos — el stream de eventos ya sería el único canal entre contextos, sin acoplamiento de request/response.

**En contra:**
- Inconsistencia eventual real: existe una ventana entre "el menú cambió" y "la copia local de `Orders` se actualizó".
- Más piezas para construir y operar: un suscriptor de eventos, una tabla local, y lógica de backfill/resincronización.
- **Bloqueo concreto, no hipotético, encontrado al revisar el código real**: `ReactorMenuEventPublisher` usa `Sinks.many().replay().limit(1)` (ver `docs/reactive-streams-and-concurrency.md`) — solo reproduce **el último evento** a un suscriptor nuevo. Si el suscriptor de `Orders` arrancara después de que ya existan varios menús, solo vería el más reciente — perdería el resto sin aviso. Esta opción no se podría implementar correctamente *hoy* sin cambiar primero la infraestructura de eventos de `Menu` (un log persistido, o un endpoint de backfill).
- Hereda toda la complejidad de manejo de fallos de emisión que ya tuvimos que resolver en el Gap E (`EmitResult` ignorado, reintentos, `failedEmissionCount`) — un evento perdido silenciosamente dejaría la copia de `Orders` desactualizada sin que nadie se entere, el mismo tipo de bug que ya cazamos una vez.

## Decisión

Se elige la **Opción A** (`MenuLookupPort` síncrono).

Para la escala actual del proyecto (un monolito, ambos contextos en el mismo proceso y la misma base de datos), el costo de acoplar disponibilidad en el momento de `createOrder` es bajo — no hay salto de red, ambos viven en la misma JVM. El costo de la Opción B, en cambio, es real e inmediato: requeriría primero resolver el límite de `replay().limit(1)` en `Menu`, y construir un mecanismo de resincronización confiable, para resolver un problema de escala (separación física de servicios) que **todavía no existe**. Priorizar la Opción B hoy sería diseñar para un futuro hipotético en vez del problema real (CLAUDE.md: evitar abstracciones prematuras).

## Consecuencias

- `MenuLookupPortAdapter` no tiene hoy ninguna decoración de resiliencia propia (sin `@CircuitBreaker`/`@Retry`/`@TimeLimiter`, a diferencia de `ResilientMenuRepositoryAdapter.findById`) — es una brecha conocida y aceptada por ahora, candidata natural para el Gap D (Resiliencia Avanzada), que explícitamente planea aislar recursos entre bounded contexts.
- Si `Orders` y `Menu` llegan a separarse en despliegues distintos, esta decisión debe revisitarse: la llamada síncrona pasaría a ser una llamada de red, y el acoplamiento de disponibilidad descrito arriba se volvería mucho más costoso de ignorar.
- Verificación: el diseño elegido ya tiene su test correspondiente según la tabla de `CLAUDE.md` (`MenuLookupPortAdapterTest`, un `*Test` unitario que mockea el colaborador inmediato, `MenuUseCases`) — no hace falta ningún test nuevo para este ADR, ya estaba cubierto al implementar el ejercicio 2.
