package ec.uce.propuestas.documento.exportacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Stable hashes for the captured projection and the request persisted with an export reservation. */
public final class ExportFingerprint {
    private ExportFingerprint() {}

    public static String hash(ObjectMapper mapper, Object value) {
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(value, "value");
        try {
            MessageDigest digest = newDigest();
            mapper.copy()
                    .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                    .writeValue(new CappedDigestOutputStream(digest), value);
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException invalid) {
            throw new IllegalArgumentException(
                    "Export projection exceeds its limit or cannot be fingerprinted", invalid);
        }
    }

    public static String bind(UUID operationId, String requestHash, String revisionHash) {
        if (operationId == null
                || operationId.version() != 7
                || operationId.variant() != 2
                || requestHash == null
                || !requestHash.matches("[0-9a-f]{64}")
                || revisionHash == null
                || !revisionHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid reservation fingerprint identity");
        }
        return digest(("stage03-export-v1|" + operationId + "|" + requestHash + "|" + revisionHash)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String digest(byte[] value) {
        return HexFormat.of().formatHex(newDigest().digest(value));
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static final class CappedDigestOutputStream extends OutputStream {
        private final MessageDigest digest;
        private long written;

        private CappedDigestOutputStream(MessageDigest digest) {
            this.digest = digest;
        }

        @Override
        public void write(int value) throws IOException {
            if (written >= DocumentosHttpClient.MAX_REQUEST_BYTES)
                throw new IOException("Export projection exceeds 8 MiB");
            digest.update((byte) value);
            written++;
        }

        @Override
        public void write(byte[] value, int offset, int length) throws IOException {
            java.util.Objects.checkFromIndexSize(offset, length, value.length);
            if (length > DocumentosHttpClient.MAX_REQUEST_BYTES - written) {
                throw new IOException("Export projection exceeds 8 MiB");
            }
            digest.update(value, offset, length);
            written += length;
        }
    }
}
