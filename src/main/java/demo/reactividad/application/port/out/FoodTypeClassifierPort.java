package demo.reactividad.application.port.out;

import java.util.Set;

import demo.reactividad.domain.model.FoodType;
import demo.reactividad.domain.model.FoodTypeSuggestion;
import reactor.core.publisher.Mono;

public interface FoodTypeClassifierPort {
    // candidates es el catálogo cerrado: la sugerencia resultante SIEMPRE debe ser
    // uno de estos valores, o el Mono termina en error (nunca un valor fuera de lista).
    Mono<FoodTypeSuggestion> suggestFoodType(String menuTitle, String menuDescription, Set<FoodType> candidates);
}
