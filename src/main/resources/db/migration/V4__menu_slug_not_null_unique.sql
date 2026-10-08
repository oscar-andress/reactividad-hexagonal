-- Fase 3 de 3 (CONTRACT) -- ver docs/data/two-phase-migration.md.
-- Recién ahora es seguro endurecer la restricción para las filas EXISTENTES: todas ya
-- tienen un valor, porque V3 corrió antes que esta migración -- Flyway aplica siempre
-- en orden, nunca fuera de secuencia.
--
-- Pero falta la otra mitad, la que este mismo ejercicio demostró rompiendo a propósito:
-- el dominio/la app NUNCA se enteraron de que esta columna existe (decisión explícita,
-- es práctica de esquema, no una feature pedida -- ver docs/ai-governance.md para el
-- mismo criterio aplicado a otra decisión de alcance). Sin un DEFAULT, cada INSERT que
-- hace la app real (que no manda menu_slug) rompería con "violates not-null
-- constraint" -- confirmado corriendo la suite completa antes de agregar esta línea.
-- gen_random_uuid() no es un slug "lindo", pero es la forma honesta de documentar que
-- las filas nuevas creadas por la app real no tienen un slug legible hasta que alguien
-- decida conectar esto al dominio -- ese día, el DEFAULT se reemplaza por lógica real.
ALTER TABLE tbl_menu ALTER COLUMN menu_slug SET DEFAULT gen_random_uuid()::text;
ALTER TABLE tbl_menu ALTER COLUMN menu_slug SET NOT NULL;
ALTER TABLE tbl_menu ADD CONSTRAINT tbl_menu_slug_unique UNIQUE (menu_slug);

-- Plan de reversa: los datos no se tocan, solo se relaja la restricción (sin esto,
-- Flyway Community no tiene "undo" automático -- este es el script manual que lo
-- reemplaza, probado como cualquier otra migración).
--   ALTER TABLE tbl_menu DROP CONSTRAINT tbl_menu_slug_unique;
--   ALTER TABLE tbl_menu ALTER COLUMN menu_slug DROP NOT NULL;
--   ALTER TABLE tbl_menu ALTER COLUMN menu_slug DROP DEFAULT;
