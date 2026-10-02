CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- tbl_menu
create table tbl_menu(
    menu_id uuid default gen_random_uuid(),
    menu_title varchar(50) not null,
    menu_description varchar(50) not null,
    menu_created_at timestamp default current_timestamp not null,
    menu_version bigint default 0 not null,
    menu_image_key varchar(500),
    CONSTRAINT tbl_menu_pk primary key (menu_id)
);

-- tbl_food_type
create table tbl_food_type(
    food_type_id uuid default gen_random_uuid(),
    food_type_name varchar(60) not null,
    active boolean not null default true,
    CONSTRAINT tbl_food_type_pk primary key (food_type_id)
);

-- tbl_menu_food_type
create table tbl_menu_food_type(
    menu_id uuid not null,
    food_type_id uuid not null,
    CONSTRAINT tbl_menu_food_type_pk primary key (menu_id, food_type_id),
    CONSTRAINT tbl_menu_food_type_fk_menu_id foreign key (menu_id) references tbl_menu(menu_id),
    CONSTRAINT tbl_menu_food_type_fk_food_type_id foreign key (food_type_id) references tbl_food_type(food_type_id)
);

-- tbl_order (bounded context "orders" — sin FK hacia tbl_menu a propósito, ver docs/adr/)
create table tbl_order(
    order_id uuid default gen_random_uuid(),
    menu_id uuid not null,
    order_quantity integer not null,
    order_created_at timestamp default current_timestamp not null,
    CONSTRAINT tbl_order_pk primary key (order_id)
);