package demo.reactividad.infrastructure.adapter.out.persistence.entity;

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

@Table(name = "tbl_menu")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MenuEntity {

    @Id
    @Column("menu_id")
    private UUID id;

    @Column("menu_title")
    private String title;

    @Column("menu_description")
    private String description;

    @Column("menu_created_at")
    private LocalDateTime createdAt;
}
