package demo.reactividad.infrastructure.adapter.out.persistence.repository;

import java.util.UUID;

import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.Repository;

import demo.reactividad.infrastructure.adapter.out.persistence.entity.MenuFoodTypeEntity;
import reactor.core.publisher.Mono;

public interface MenuFoodTypeR2dbcRepository extends Repository<MenuFoodTypeEntity, UUID> {

    @Modifying
    @Query("DELETE FROM tbl_menu_food_type WHERE menu_id = :menuId")
    Mono<Integer> deleteByMenuId(UUID menuId);

    @Modifying
    @Query("INSERT INTO tbl_menu_food_type (menu_id, food_type_id) VALUES (:menuId, :foodTypeId)")
    Mono<Integer> insert(UUID menuId, UUID foodTypeId);

    @Modifying
    @Query("DELETE FROM tbl_menu_food_type")
    Mono<Integer> deleteAllAssignments();
}
