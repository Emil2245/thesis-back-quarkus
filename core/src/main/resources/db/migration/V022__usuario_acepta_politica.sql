-- Plan 024: consentimiento de la política de privacidad (LOPDP).
-- Usuarios previos (seed) quedan en NULL por diseño.
ALTER TABLE usuario
    ADD COLUMN politica_aceptada_en TIMESTAMPTZ NULL,
    ADD COLUMN politica_version VARCHAR(20) NULL;
