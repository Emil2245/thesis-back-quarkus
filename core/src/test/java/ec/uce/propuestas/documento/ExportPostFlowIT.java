package ec.uce.propuestas.documento;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import ec.uce.propuestas.documento.exportacion.DocumentosExportClient.UploadUncertainException;
import ec.uce.propuestas.documento.exportacion.RemoteExportUploadGateway;
import ec.uce.propuestas.documentos.contract.ExportUploadV1;
import ec.uce.propuestas.documentos.contract.StoredObjectV1;
import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ExportPostFlowIT {
    private static final String KEY = "01900000-0000-7000-8000-000000000077";
    private static final String CHECKSUM = "A".repeat(43) + "=";

    @Inject
    DataSource ds;

    @Inject
    RecordingEnviadorCorreo mailbox;

    @InjectMock
    RemoteExportUploadGateway uploads;

    @Test
    void confirmedBudgetReissuesOneUseTicketWithoutReuploadAndRejectsChangedRequest() throws Exception {
        var f = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
        when(uploads.upload(any(ExportUploadV1.class))).thenAnswer(inv -> stored(inv.getArgument(0)));
        String path = f.path() + "/exportaciones";
        Response first = post(f.token(), path, KEY, "{\"formato\":\"pdf\"}");
        assertEquals(200, first.statusCode(), first.asString());
        assertEquals("no-store", first.header("Cache-Control"));
        assertEquals("no-referrer", first.header("Referrer-Policy"));
        UUID operation = UUID.fromString(first.jsonPath().getString("operationId"));
        assertEquals(7, operation.version());
        String firstUrl = first.jsonPath().getString("downloadUrl");
        assertTrue(firstUrl.startsWith("/api/v1/documentos/descargas/"));
        assertFalse(firstUrl.contains("s3"));
        assertEquals("application/pdf", first.jsonPath().getString("mediaType"));
        assertEquals(CHECKSUM, first.jsonPath().getString("sha256"));
        assertEquals(10, first.jsonPath().getInt("sizeBytes"));
        assertTrue(first.jsonPath().getString("filename").endsWith(".pdf"));
        assertNotNull(first.jsonPath().getList("warnings"));
        assertTrue(Instant.parse(first.jsonPath().getString("expiresAt"))
                .isBefore(Instant.parse(first.jsonPath().getString("objectExpiresAt"))));
        Response replay = post(f.token(), path, KEY, "{\"formato\":\"pdf\"}");
        assertEquals(200, replay.statusCode(), replay.asString());
        assertEquals(operation.toString(), replay.jsonPath().getString("operationId"));
        assertNotEquals(firstUrl, replay.jsonPath().getString("downloadUrl"));
        assertEquals(
                409,
                post(f.token(), path, KEY, "{\"formato\":\"pdf\",\"orientacion\":\"horizontal\"}")
                        .statusCode());
        verify(uploads, times(1)).upload(any(ExportUploadV1.class));
        verify(uploads, never()).reconcile(any(ExportUploadV1.class));
    }

    @Test
    void uncertainUploadMustReconcileBeforeTicket() throws Exception {
        var f = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
        when(uploads.upload(any(ExportUploadV1.class))).thenThrow(new UploadUncertainException("lost response"));
        when(uploads.reconcile(any(ExportUploadV1.class))).thenAnswer(inv -> stored(inv.getArgument(0)));
        Response recovered = post(f.token(), f.path() + "/exportaciones", KEY, "{\"formato\":\"pdf\"}");
        assertEquals(200, recovered.statusCode(), recovered.asString());
        assertTrue(recovered.jsonPath().getString("downloadUrl").startsWith("/api/v1/documentos/descargas/"));
        verify(uploads, times(1)).upload(any(ExportUploadV1.class));
        verify(uploads, times(1)).reconcile(any(ExportUploadV1.class));
    }

    @Test
    void apuXlsxExportsWithMatchingPrivateMetadata() throws Exception {
        var f = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
        when(uploads.upload(any(ExportUploadV1.class))).thenAnswer(inv -> {
            ExportUploadV1 request = inv.getArgument(0);
            assertEquals("apus", request.projection().documento());
            return new StoredObjectV1(
                    request.key(),
                    10,
                    CHECKSUM,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    request.filename());
        });
        Response result = post(
                f.token(),
                "/api/v1/documentos/apus/" + f.id() + "/exportaciones",
                KEY,
                "{\"formato\":\"xlsx\",\"layout\":\"pestanas\"}");
        assertEquals(200, result.statusCode(), result.asString());
        assertTrue(result.jsonPath().getString("filename").endsWith(".xlsx"));
        verify(uploads, times(1)).upload(any(ExportUploadV1.class));
    }

    @Test
    void specificationReissuesTicketAfterAuditWithoutRerender() throws Exception {
        var f = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
        try (var db = ds.getConnection()) {
            PresupuestoExportResourceIT.sql(
                    db,
                    "UPDATE apu SET especificacion_tecnica = 'Technical details' WHERE presupuesto_id = ?",
                    f.budget());
        }
        when(uploads.upload(any(ExportUploadV1.class))).thenAnswer(inv -> {
            ExportUploadV1 request = inv.getArgument(0);
            assertNotNull(request.specification());
            assertEquals("Technical title", request.specification().titulo1());
            return new StoredObjectV1(
                    request.key(),
                    10,
                    CHECKSUM,
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    request.filename());
        });
        String path = "/api/v1/documentos/especificaciones-tecnicas/" + f.id() + "/exportaciones";
        String body = "{\"formato\":\"docx\",\"titulo1\":\"Technical title\"}";
        Response first = post(f.token(), path, KEY, body);
        assertEquals(200, first.statusCode(), first.asString());
        assertTrue(first.jsonPath().getString("filename").endsWith(".docx"));
        Response replay = post(f.token(), path, KEY, body);
        assertEquals(200, replay.statusCode(), replay.asString());
        assertEquals(
                first.jsonPath().getString("operationId"), replay.jsonPath().getString("operationId"));
        assertNotEquals(
                first.jsonPath().getString("downloadUrl"), replay.jsonPath().getString("downloadUrl"));
        verify(uploads, times(1)).upload(any(ExportUploadV1.class));
    }

    @Test
    void blockedAndForeignBudgetsNeverUploadOrIssueTickets() throws Exception {
        var blocked = PresupuestoExportResourceIT.fixture(ds, mailbox, false, false);
        Response preflight = post(blocked.token(), blocked.path() + "/exportaciones", KEY, "{\"formato\":\"pdf\"}");
        assertEquals(409, preflight.statusCode(), preflight.asString());
        assertEquals("no-store", preflight.header("Cache-Control"));
        assertNotNull(preflight.jsonPath().getList("bloqueos"));
        assertNull(preflight.jsonPath().getString("downloadUrl"));
        var owned = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
        String otherToken = AuthSupport.registrarConToken(mailbox, "post-foreign-" + System.nanoTime() + "@test.ec");
        Response foreign = post(otherToken, owned.path() + "/exportaciones", KEY, "{\"formato\":\"pdf\"}");
        assertEquals(404, foreign.statusCode(), foreign.asString());
        assertNull(foreign.jsonPath().getString("downloadUrl"));
        verifyNoInteractions(uploads);
    }

    @Test
    void failedReconciliationNeverIssuesATicket() throws Exception {
        var f = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
        when(uploads.upload(any(ExportUploadV1.class))).thenThrow(new UploadUncertainException("lost response"));
        when(uploads.reconcile(any(ExportUploadV1.class))).thenThrow(new UploadUncertainException("head unavailable"));
        Response failed = post(f.token(), f.path() + "/exportaciones", KEY, "{\"formato\":\"pdf\"}");
        assertTrue(failed.statusCode() >= 500, failed.asString());
        assertFalse(failed.asString().contains("/documentos/descargas/"));
        assertEquals(
                409,
                post(f.token(), f.path() + "/exportaciones", KEY, "{\"formato\":\"pdf\"}")
                        .statusCode());
        verify(uploads, times(1)).upload(any(ExportUploadV1.class));
        verify(uploads, times(1)).reconcile(any(ExportUploadV1.class));
    }

    private static StoredObjectV1 stored(ExportUploadV1 request) {
        assertEquals("exports/" + request.operationId(), request.key());
        assertTrue(request.fingerprint().matches("[0-9a-f]{64}"));
        return new StoredObjectV1(request.key(), 10, CHECKSUM, "application/pdf", request.filename());
    }

    private static Response post(String token, String path, String key, String body) {
        return given().auth()
                .oauth2(token)
                .contentType("application/json")
                .header("Idempotency-Key", key)
                .body(body)
                .post(path);
    }
}
