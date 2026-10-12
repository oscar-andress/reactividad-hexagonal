# Reversibilidad: kill switch para la sugerencia de FoodType

Ejercicio de la Fase 4 del learning path (datos/reversibilidad). Objetivo: poder apagar una feature de riesgo operacional real sin redeploy — solo reiniciando el proceso con una variable de entorno.

## Por qué esta feature puntual

`suggestFoodType` (Gap C, el clasificador gobernado de FoodType) depende de un LLM externo — con riesgo real de fallo, costo impredecible o latencia alta que los límites de `docs/ai-governance.md` (timeout, rate limit) mitigan pero no eliminan. Si algo sale mal en producción de una forma que esos límites no cubrieron, hace falta poder apagar la feature entera, ya, sin esperar un ciclo de build+deploy.

No es un patrón para aplicar a cualquier endpoint — ver la regla general en `CLAUDE.md` Parte 1, Sección 1: tiene sentido solo cuando hay un riesgo operacional genuino (depende de algo externo propenso a fallar, es nueva y sin probar en producción real, comportamiento difícil de predecir). Una operación CRUD estable no lo necesita.

## Dónde vive el flag

En la capa de **caso de uso** (`MenuUseCasesService.suggestFoodType`), no en el handler HTTP — es una regla de negocio/operacional, no un detalle de transporte. El handler y el router nunca se enteran de la property; solo reciben una excepción de dominio (`FeatureDisabledException`, mismo patrón que `MenuNotFoundException`) y la traducen a un código HTTP, igual que con cualquier otro error de este proyecto.

La property se expone como un `@ConfigurationProperties(prefix = "features.food-type-suggestion")` (`FoodTypeSuggestionProperties`, un record con un solo campo `enabled`) — mismo patrón ya establecido en este proyecto para `S3Properties`/`S3Config`, en vez de un `@Value` suelto directo sobre el campo. `MenuUseCasesService` la recibe como una dependencia más, inyectada por constructor junto con el resto de los puertos — habilitada con `@EnableConfigurationProperties(FoodTypeSuggestionProperties.class)` en la propia clase, ya que es su único consumidor (mismo criterio que `S3Config`, que habilita y usa `S3Properties` en el mismo lugar).

## Qué código HTTP devuelve, y por qué uno distinto

`503 Service Unavailable` — deliberadamente distinto de los otros dos casos "no hay sugerencia" que ya existían:
- `204 No Content`: la IA corrió, no encontró nada seguro que sugerir (camino normal).
- `404 Not Found`: el menú no existe.
- `503 Service Unavailable` (nuevo): la feature está apagada a propósito, no intentó correr nada.

Si los tres fueran el mismo código, no se podría distinguir desde logs/monitoreo "la IA no encontró nada" de "alguien apagó la feature a propósito" — justo el tipo de ambigüedad que la Sección 7 de `CLAUDE.md` (observabilidad orientada a negocio) ya pide evitar.

## Cómo se apaga en un incidente real

```bash
FOOD_TYPE_SUGGESTION_ENABLED=false
```

y reiniciar el proceso. Sin redeploy, sin tocar código — la property tiene como default `true` (`features.food-type-suggestion.enabled=${FOOD_TYPE_SUGGESTION_ENABLED:true}`), consistente con el resto de las properties de este proyecto que ya se overridean por variable de entorno.

## Hallazgo real encontrado haciendo el ejercicio (primer intento, corregido)

El primer intento usó un `@Value("${features.food-type-suggestion.enabled:true}")` directo sobre un campo `boolean` del propio `MenuUseCasesService` — y eso expuso un problema real. Ese campo, por ser `@Value`, no puede ser `final` (no pasa por el constructor que genera Lombok con `@RequiredArgsConstructor`, a diferencia del resto de los campos de esa clase). En un `new MenuUseCasesService(...)` sin Spring (como hacen los `*Test` de este archivo), ese campo nunca recibía el valor de `@Value` — y el default de un `boolean` en Java es `false`. Sin un valor explícito en la declaración del campo, **todos los tests existentes de `suggestFoodType` hubieran roto en silencio** contra la rama nueva de "feature apagada", sin que tuviera nada que ver con un bug real del código de producción.

Pasar a `FoodTypeSuggestionProperties` (un record inyectado por constructor, como cualquier otro puerto) resuelve esto de raíz, no con un parche: vuelve a ser un campo `final` normal, construido explícitamente en cada test (`new FoodTypeSuggestionProperties(true)` o `(false)`) — nada queda implícito ni depende de que Spring esté presente o no.

## Verificación

- `MenuUseCasesServiceTest` nuevo: con `FoodTypeSuggestionProperties(false)`, `suggestFoodType` lanza `FeatureDisabledException` **sin tocar el repositorio** (ni siquiera consulta si el menú existe) — construido como una instancia separada del servicio, sin reflexión. Roto a propósito (sacando el chequeo) → el test nuevo rompe (`NullPointerException` real, porque además intentó usar un mock sin stubear) → restaurado → verde de nuevo.
- `mvn test` y `mvn verify` (con Docker real, Testcontainers) completos en verde — corridos en local *antes* de pushear, después de la sorpresa de CI del ejercicio de autorización.
