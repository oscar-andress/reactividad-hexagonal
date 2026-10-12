# EXPLAIN ANALYZE: decidir un índice con evidencia, no por instinto

Ejercicio de la Fase 4 del learning path (datos/reversibilidad). Objetivo: tomar una decisión real de índice basada en medir con `EXPLAIN ANALYZE`, no en "esta columna no tiene índice, agreguemos uno".

## La query elegida

`FoodTypeR2dbcRepository.findAllActive()` → `SELECT * FROM tbl_food_type WHERE active = true`. Es la única query real de este proyecto que filtra por una columna genuinamente sin índice (`tbl_food_type.active` — la tabla solo tiene la PK sobre `food_type_id`). Las otras candidatas revisadas (`findFoodTypeByMenuId`, `deleteByMenuId`, ambas filtrando `tbl_menu_food_type.menu_id`) ya quedan cubiertas por la PK compuesta `(menu_id, food_type_id)` — el `menu_id` es la columna izquierda de esa PK, así que Postgres ya puede usarla.

## Por qué hizo falta sembrar datos sintéticos

La tabla real hoy tiene un puñado de filas — con una tabla chica, Postgres elige correctamente un Seq Scan sin importar si hay índice o no (leer todo secuencial es más barato que la sobrecarga de usar un índice cuando hay pocas páginas de datos). Medir contra la tabla tal como está hoy no hubiera probado nada. Se sembraron **500.000 filas sintéticas** contra un Postgres descartable (nunca contra la base real de desarrollo, mismo criterio que con ZAP), con una distribución realista: ~95% `active = true`, ~5% `false` — como pasaría con categorías de comida, que rara vez se desactivan una vez creadas.

## Medición 1: la query real, sin índice

```sql
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM tbl_food_type WHERE active = true;
```

```
Seq Scan on tbl_food_type (actual time=0.007..26.366 rows=475179 loops=1)
  Filter: active
  Rows Removed by Filter: 24821
Execution Time: 39.522 ms
```

## Medición 2: la misma query, después de agregar el índice

```sql
CREATE INDEX idx_food_type_active ON tbl_food_type (active);
```

```
Seq Scan on tbl_food_type (actual time=0.007..26.556 rows=475179 loops=1)
  Filter: active
  Rows Removed by Filter: 24821
Execution Time: 39.149 ms
```

**Postgres ignora el índice recién creado por completo** — mismo plan, mismo tiempo. No es un error: `active = true` es el 95% de la tabla, y saltar por un índice para terminar trayendo casi todas las filas es más caro que leer todo secuencial. El planner lo sabe (usa las estadísticas reales de la tabla, actualizadas con `ANALYZE`) y elige bien.

## Medición 3: la query contraria (`active = false`, la minoría), con el mismo índice

```sql
EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM tbl_food_type WHERE active = false;
```

```
Index Scan using idx_food_type_active on tbl_food_type (actual time=0.084..7.466 rows=24821 loops=1)
  Index Cond: (active = false)
Execution Time: 8.138 ms
```

Acá el índice sí se usa, y el tiempo baja de ~39ms (el equivalente Seq Scan) a ~8ms. La diferencia entre las mediciones 2 y 3 es la misma tabla, el mismo índice — lo único que cambia es qué tan selectivo es el filtro.

## Decisión

**No se agrega el índice.** La única query real que existe en el código filtra el valor mayoritario (`true`), y para ese filtro el índice nunca se usa — solo agregaría costo de escritura (cada `INSERT`/`UPDATE` sobre `active` tendría que mantenerlo) sin ningún beneficio de lectura real. El índice sería genuinamente útil si algún día apareciera una query real filtrando `active = false` específicamente — hoy no existe, y agregar infraestructura para una query hipotética no verificada es el mismo error que agregarlo "porque la columna no tiene índice".

## Verificación

Medido tres veces, con datos reales (sintéticos pero a escala realista) contra un Postgres descartable — nunca asumido. Contenedor descartado al terminar, la base real de desarrollo nunca se tocó.
