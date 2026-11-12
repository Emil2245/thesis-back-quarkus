-- V013__plantilla_proyecto_tipo.sql
-- Plan 044 — plantillas de proyecto SISTEMA/PERSONAL (bugs-pendientes §6).
--
-- Hasta aquí toda plantilla de proyecto era personal por diseño
-- (`usuario_id NOT NULL`, P-46). Se añade el mismo modelo que ya tiene
-- `plantilla_apu` (V001 §2.12): SISTEMA = `usuario_id NULL`, gestionada por
-- SUPER_ADMIN y de solo lectura para USUARIO; PERSONAL = del dueño.
--
-- Las filas existentes quedan PERSONAL (todas tienen dueño), así que el
-- CHECK se cumple sin backfill.

ALTER TABLE plantilla_proyecto
  ADD COLUMN tipo VARCHAR(10) NOT NULL DEFAULT 'PERSONAL'
    CHECK (tipo IN ('SISTEMA','PERSONAL'));

ALTER TABLE plantilla_proyecto ALTER COLUMN usuario_id DROP NOT NULL;

ALTER TABLE plantilla_proyecto
  ADD CONSTRAINT ck_plantilla_proyecto_tipo_dueno
    CHECK ((tipo = 'SISTEMA' AND usuario_id IS NULL)
        OR (tipo = 'PERSONAL' AND usuario_id IS NOT NULL));
