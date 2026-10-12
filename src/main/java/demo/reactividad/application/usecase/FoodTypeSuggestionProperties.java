package demo.reactividad.application.usecase;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Kill switch operacional (ver docs/data/feature-flag-reversibility.md) -- apaga la
// sugerencia de FoodType sin redeploy, reiniciando el proceso con
// FOOD_TYPE_SUGGESTION_ENABLED=false.
@ConfigurationProperties(prefix = "features.food-type-suggestion")
public record FoodTypeSuggestionProperties(boolean enabled) {

}
