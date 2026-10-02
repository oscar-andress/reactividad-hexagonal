package demo.reactividad.orders.infrastructure.adapter.out.persistence.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Table(name = "tbl_order")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class OrderEntity {

    @Id
    @Column("order_id")
    private UUID id;

    @Column("menu_id")
    private UUID menuId;

    @Column("order_menu_title_snapshot")
    private String menuTitleSnapshot;

    @Column("order_quantity")
    private int quantity;

    @Column("order_created_at")
    private LocalDateTime createdAt;
}
