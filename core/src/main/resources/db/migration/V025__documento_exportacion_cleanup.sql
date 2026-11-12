-- Cleanup claims are fenced; the export/audit metadata remains after S3 deletion.
ALTER TABLE documento_exportacion
    ADD COLUMN cleanup_fence BIGINT NOT NULL DEFAULT 0 CHECK (cleanup_fence >= 0),
    ADD COLUMN cleanup_claim_until TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_documento_cleanup_confirmed
        CHECK (deleted_at IS NULL OR uploaded_at IS NOT NULL);
CREATE INDEX idx_documento_exportacion_cleanup
    ON documento_exportacion (uploaded_at, id)
    WHERE uploaded_at IS NOT NULL AND deleted_at IS NULL;
