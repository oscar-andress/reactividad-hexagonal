-- Fase 2 de 3 (BACKFILL) -- ver docs/data/two-phase-migration.md.
-- Calcula el slug para las filas que ya existían antes de V2 (y para cualquiera que
-- se haya insertado entre V2 y este momento sin especificarlo). No hay un único valor
-- por defecto que sirva para todas las filas -- cada una necesita el suyo, derivado de
-- su propio título -- por eso esto no se pudo resolver con un simple DEFAULT en V2.
--
-- unaccent() saca tildes (así "César" se convierte en "Cesar" antes de bajar a
-- minúsculas), y el sufijo de 8 caracteres del propio menu_id garantiza unicidad sin
-- tener que detectar colisiones entre títulos repetidos.
--
-- Plan de reversa: vuelve al estado de V2 (columna presente, sin valores).
--   UPDATE tbl_menu SET menu_slug = NULL;
CREATE EXTENSION IF NOT EXISTS unaccent;

UPDATE tbl_menu
SET menu_slug = lower(regexp_replace(unaccent(trim(menu_title)), '[^a-zA-Z0-9]+', '-', 'g'))
                || '-' || substr(menu_id::text, 1, 8)
WHERE menu_slug IS NULL;
