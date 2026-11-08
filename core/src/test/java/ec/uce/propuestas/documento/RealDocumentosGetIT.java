package ec.uce.propuestas.documento;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import javax.sql.DataSource;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Optional two-application integration check using a packaged real Documentos and Core's test database. */
@QuarkusTest
@TestProfile(RemoteRenderProfile.class)
@QuarkusTestResource(value = RealDocumentosResource.class, restrictToAnnotatedClass = true)
@EnabledIfEnvironmentVariable(named = "STAGE02_REAL_DOCUMENTOS", matches = "true")
class RealDocumentosGetIT {
    @Inject
    DataSource ds;

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Test
    void publicGetDownloadsFromTheRealDocumentosApplication() throws Exception {
        var fixture = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
        try (var c = ds.getConnection()) {
            PresupuestoExportResourceIT.sql(
                    c,
                    "update apu set especificacion_tecnica='Hormigón técnico' where presupuesto_id=?",
                    fixture.budget());
        }
        for (String document : new String[] {"presupuesto", "apus"}) {
            String path = fixture.path().replace("/presupuesto/", "/" + document + "/");
            for (String format : new String[] {"pdf", "xlsx"}) {
                var response = given().auth()
                        .oauth2(fixture.token())
                        .get(path + "?formato=" + format)
                        .then()
                        .statusCode(200)
                        .contentType(
                                "pdf".equals(format) ? ArchivoGenerado.PDF_MEDIA_TYPE : ArchivoGenerado.XLSX_MEDIA_TYPE)
                        .extract()
                        .response();
                var bytes = response.asByteArray();
                assertTrue(bytes.length > 4);
                assertEquals(
                        "pdf".equals(format) ? "%PDF" : "PK\u0003\u0004",
                        new String(bytes, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1));
                assertTrue(response.header("Content-Disposition")
                        .contains(fixture.id().toString()));
            }
        }
        assertEquals(0L, exportAuditCount(fixture.id()), "budget/APU downloads must not emit DOCX export audit");
        var docx = given().auth()
                .oauth2(fixture.token())
                .get("/api/v1/documentos/especificaciones-tecnicas/" + fixture.id() + "?formato=docx")
                .then()
                .statusCode(200)
                .contentType(ArchivoGenerado.DOCX_MEDIA_TYPE)
                .extract()
                .response();
        try (var document = new XWPFDocument(new ByteArrayInputStream(docx.asByteArray()))) {
            assertTrue(
                    document.getParagraphs().stream().anyMatch(p -> p.getText().contains("Hormigón técnico")));
        }
        assertEquals(1L, exportAuditCount(fixture.id()), "only the successful DOCX must emit an export event");
    }

    private long exportAuditCount(java.util.UUID presupuestoId) throws Exception {
        try (var c = ds.getConnection();
                var p = c.prepareStatement("select count(*) from log_actividad where evento='documento.exportado' "
                        + "and entidad_public_id=?")) {
            p.setObject(1, presupuestoId);
            try (var r = p.executeQuery()) {
                assertTrue(r.next());
                return r.getLong(1);
            }
        }
    }
}
