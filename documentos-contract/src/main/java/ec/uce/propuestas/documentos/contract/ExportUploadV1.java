package ec.uce.propuestas.documentos.contract;

/** Internal upload request: a single immutable projection and a private object identity supplied by Core. */
public record ExportUploadV1(
        int schemaVersion,
        String key,
        String operationId,
        String fingerprint,
        String filename,
        DocumentProjectionV1 projection,
        SpecificationProjectionV1 specification) {
    public static final int VERSION = 1;
}
