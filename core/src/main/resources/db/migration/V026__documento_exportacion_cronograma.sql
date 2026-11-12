-- Extend the existing private export reservation to the captured cronograma lane.
-- Existing document values remain valid; no rows or object identities are rewritten.
ALTER TABLE documento_exportacion
    DROP CONSTRAINT documento_exportacion_documento_check;
ALTER TABLE documento_exportacion
    ADD CONSTRAINT documento_exportacion_documento_check
    CHECK (documento IN ('presupuesto', 'apus', 'especificaciones-tecnicas', 'cronograma'));
