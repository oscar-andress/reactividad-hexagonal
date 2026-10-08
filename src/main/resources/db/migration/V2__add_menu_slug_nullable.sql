-- Fase 1 de 3 (EXPAND) -- ver docs/data/two-phase-migration.md.
-- Columna nueva, sin restricción todavía: compatible hacia atrás, el código que no
-- sabe que existe sigue funcionando igual, y las filas existentes quedan con
-- menu_slug en NULL hasta que V3 las backfillee.
--
-- Plan de reversa: nadie depende todavía de esta columna, así que revertir es
-- simplemente borrarla, sin perder ningún dato real.
--   ALTER TABLE tbl_menu DROP COLUMN menu_slug;
ALTER TABLE tbl_menu ADD COLUMN menu_slug varchar(70);
