package ec.uce.propuestas.documentos.contract;

/** Internal upload request: a single immutable projection and a private object identity supplied by Core. */
public record ExportUploadV1(
        int schemaVersion,
        String key,
        String operationId,
        String fingerprint,
        String filename,
        DocumentProjectionV1 projection,
        SpecificationProjectionV1 specification,
        CronogramaProjectionV1 cronograma) {
    public static final int VERSION = 1;

    /** Existing budget/APU/specification callers keep their stage-03 constructor. */
    public ExportUploadV1(
            int schemaVersion,
            String key,
            String operationId,
            String fingerprint,
            String filename,
            DocumentProjectionV1 projection,
            SpecificationProjectionV1 specification) {
        this(schemaVersion, key, operationId, fingerprint, filename, projection, specification, null);
    }
}
