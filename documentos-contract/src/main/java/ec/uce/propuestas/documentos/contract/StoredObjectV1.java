package ec.uce.propuestas.documentos.contract;

/** Internal confirmed object metadata. Never contains a bucket URL or S3 credential. */
public record StoredObjectV1(String key, long size, String checksumSha256, String mimeType, String filename) {}
