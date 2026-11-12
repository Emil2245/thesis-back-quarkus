-- Plan 001 — PostgreSQL FTS for paginated plantilla search.
CREATE EXTENSION IF NOT EXISTS unaccent;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_ts_config WHERE cfgname = 'spanish_unaccent'
    ) THEN
        CREATE TEXT SEARCH CONFIGURATION public.spanish_unaccent (COPY = pg_catalog.spanish);
    END IF;
END
$$;

ALTER TEXT SEARCH CONFIGURATION public.spanish_unaccent
    ALTER MAPPING FOR asciiword, asciihword, hword_asciipart, word, hword, hword_part
    WITH unaccent, spanish_stem;

ALTER TABLE plantilla_apu
    ADD COLUMN busqueda_fts tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('public.spanish_unaccent'::regconfig, coalesce(nombre, '')), 'A') ||
        setweight(to_tsvector('public.spanish_unaccent'::regconfig, coalesce(descripcion_rubro, '')), 'B')
    ) STORED;

CREATE INDEX ix_plantilla_apu_busqueda_fts ON plantilla_apu USING GIN (busqueda_fts);
