package demo.reactividad.infrastructure.adapter.out.persistence.entity;

import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Table(name = "tbl_food_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FoodTypeEntity {

    @Id
    @Column("food_type_id")
    private UUID foodTypeId;

    @Column("food_type_name")
    private String foodTypeName;

    @Column("active")
    private boolean active = true;
}
