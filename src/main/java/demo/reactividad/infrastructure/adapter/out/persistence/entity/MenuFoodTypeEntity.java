package demo.reactividad.infrastructure.adapter.out.persistence.entity;

import java.util.UUID;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;

// Tabla puente tbl_menu_food_type. Sin @Id a propósito: su clave es compuesta
// (menu_id, food_type_id) y solo se usa vía consultas @Query, nunca con save().
@Table(name = "tbl_menu_food_type")
@Getter
@AllArgsConstructor
public class MenuFoodTypeEntity {

    @Column("menu_id")
    private UUID menuId;

    @Column("food_type_id")
    private UUID foodTypeId;
}
