package demo.reactividad.domain.model;

public record FoodTypeSuggestion(FoodType foodType, double confidence) {
    public FoodTypeSuggestion {
        if (foodType == null) {
            throw new IllegalArgumentException("FoodTypeSuggestion must reference a FoodType");
        }
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("FoodTypeSuggestion confidence must be between 0 and 1");
        }
    }
}
