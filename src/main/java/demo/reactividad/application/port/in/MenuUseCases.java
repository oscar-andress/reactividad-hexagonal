package demo.reactividad.application.port.in;

import java.util.Set;
import java.util.UUID;

import demo.reactividad.domain.model.FoodTypeSuggestion;
import demo.reactividad.domain.model.Menu;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MenuUseCases {
    Mono<Menu> getMenu(UUID menuId);
    Mono<Menu> createMenu(Menu menu);
    Mono<Void> deleteMenu(UUID menuId);
    Flux<Menu> streamMenus();
    Flux<Menu> createMenus(Flux<Menu> menus);
    // foodTypeIds null = no tocar los food types del menú; no-null (incluso vacío) los reemplaza.
    Mono<Menu> updateMenu(Menu menu, Set<UUID> foodTypeIds);
    Mono<Menu> uploadMenuImage(UUID menuId, byte[] imageContent, String contentType);
    // Nunca escribe nada — human-in-the-loop: un humano debe aplicar la sugerencia a
    // propósito vía updateMenu/foodTypeIds. Mono.empty() significa "sin sugerencia segura".
    Mono<FoodTypeSuggestion> suggestFoodType(UUID menuId);
}
