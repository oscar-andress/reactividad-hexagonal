package demo.reactividad.infrastructure.adapter.out.persistence.repository;

import java.util.Set;
import java.util.UUID;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import demo.reactividad.infrastructure.adapter.out.persistence.entity.FoodTypeEntity;
import reactor.core.publisher.Flux;

public interface FoodTypeR2dbcRepository extends ReactiveCrudRepository<FoodTypeEntity, UUID> {
    @Query("""
            SELECT ft.*
            FROM tbl_menu_food_type mft
            INNER JOIN tbl_food_type ft ON ft.food_type_id = mft.food_type_id
            WHERE mft.menu_id = :menuId
            """)
    Flux<FoodTypeEntity> findFoodTypeByMenuId(UUID menuId);

    @Query("SELECT * FROM tbl_food_type WHERE food_type_id IN (:foodTypeIds)")
    Flux<FoodTypeEntity> findByIds(Set<UUID> foodTypeIds);

    @Query("SELECT * FROM tbl_food_type WHERE active = true")
    Flux<FoodTypeEntity> findAllActive();
}
