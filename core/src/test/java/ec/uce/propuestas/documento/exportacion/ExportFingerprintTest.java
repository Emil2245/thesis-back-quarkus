package ec.uce.propuestas.documento.exportacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExportFingerprintTest {
    private static final UUID FIRST = UUID.fromString("01900000-0000-7000-8000-000000000001");
    private static final UUID SECOND = UUID.fromString("01900000-0000-7000-8000-000000000002");
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void persistedRequestAndRevisionHashesBindAnOperationWithoutMapOrderingDependence() {
        var forward = new LinkedHashMap<String, String>();
        forward.put("formato", "pdf");
        forward.put("orientacion", "horizontal");
        var reverse = new LinkedHashMap<String, String>();
        reverse.put("orientacion", "horizontal");
        reverse.put("formato", "pdf");
        String request = ExportFingerprint.hash(json, forward);
        assertEquals(request, ExportFingerprint.hash(new ObjectMapper().findAndRegisterModules(), reverse));
        String revision = ExportFingerprint.hash(json, Map.of("total", "100.00", "project", "Example"));
        String bound = ExportFingerprint.bind(FIRST, request, revision);
        assertEquals(64, bound.length());
        assertEquals(bound, ExportFingerprint.bind(FIRST, request, revision));
        assertNotEquals(bound, ExportFingerprint.bind(SECOND, request, revision));
        assertNotEquals(
                bound,
                ExportFingerprint.bind(FIRST, ExportFingerprint.hash(json, Map.of("formato", "xlsx")), revision));
        assertNotEquals(
                bound,
                ExportFingerprint.bind(
                        FIRST, request, ExportFingerprint.hash(json, Map.of("total", "101.00", "project", "Example"))));
        assertThrows(IllegalArgumentException.class, () -> ExportFingerprint.bind(FIRST, "invalid", revision));
    }

    @Test
    void rejectsProjectionLargerThanTransportLimitBeforeHashingIt() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ExportFingerprint.hash(
                        json, Map.of("oversized", "x".repeat(DocumentosHttpClient.MAX_REQUEST_BYTES))));
    }
}
