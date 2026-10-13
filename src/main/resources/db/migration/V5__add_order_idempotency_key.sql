-- Columna opcional (nullable): no hace falta expand->backfill->contract porque no se
-- agrega un NOT NULL (ver CLAUDE.md, Sección 4) -- las filas existentes se quedan con
-- NULL para siempre, y eso es un valor válido (pedido sin clave de idempotencia).
alter table tbl_order add column order_idempotency_key varchar(255);

-- Índice único parcial: Postgres ya trata cada NULL como distinto entre sí en un
-- índice único normal, así que el "where" no es necesario para permitir varios
-- pedidos sin clave -- se agrega para dejar la intención explícita (unicidad real
-- solo cuando la clave está presente) y para no indexar filas que nunca se van a
-- buscar por esta columna.
create unique index tbl_order_idempotency_key_uk
    on tbl_order (order_idempotency_key)
    where order_idempotency_key is not null;
