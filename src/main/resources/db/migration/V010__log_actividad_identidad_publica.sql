-- Plan 033 (I-11 / P-42) — public identity for the activity log.
-- Existing BIGINT PK/FKs and the legacy entidad_id column remain unchanged.

ALTER TABLE log_actividad
    ADD COLUMN public_id UUID NOT NULL DEFAULT uuidv7(),
    ADD COLUMN entidad_public_id UUID;

CREATE UNIQUE INDEX ux_log_actividad_public_id
    ON log_actividad(public_id);

CREATE TRIGGER trg_log_actividad_public_id_immutable
    BEFORE UPDATE OF public_id ON log_actividad
    FOR EACH ROW EXECUTE FUNCTION fn_assert_public_id_immutable();
