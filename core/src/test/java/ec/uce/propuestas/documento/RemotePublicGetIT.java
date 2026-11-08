package ec.uce.propuestas.documento;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.time.LocalDate;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(RemoteRenderProfile.class)
@QuarkusTestResource(value = RemoteRenderStub.class, restrictToAnnotatedClass = true)
class RemotePublicGetIT {
    @Inject
    DataSource ds;

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    ObjectMapper json;

    @BeforeEach
    void resetSpecificationStub() {
        RemoteRenderStub.specificationCalls.set(0);
        RemoteRenderStub.specificationProjection.set(null);
        RemoteRenderStub.specificationAuthorization.set(null);
        RemoteRenderStub.auditDuringRender.set(null);
        RemoteRenderStub.auditCounter = () -> -1;
        RemoteRenderStub.specificationStatus = 200;
        RemoteRenderStub.specificationDelayMillis = 0;
    }

    private PresupuestoExportResourceIT.Fixture specificationFixture() throws Exception {
        var fixture = PresupuestoExportResourceIT.fixture(ds, mailbox, true, false);
        try (var c = ds.getConnection()) {
            PresupuestoExportResourceIT.sql(
                    c,
                    "update apu set especificacion_tecnica='Hormigón técnico' where presupuesto_id=?",
                    fixture.budget());
            PresupuestoExportResourceIT.sql(
                    c,
                    "update proyecto set titulo_et_1='Título proyecto 1', titulo_et_2='Título proyecto 2' where id=?",
                    fixture.project());
        }
        RemoteRenderStub.auditCounter = () -> auditCount(fixture);
        return fixture;
    }

    private long auditCount(PresupuestoExportResourceIT.Fixture fixture) {
        try (var c = ds.getConnection();
                var p = c.prepareStatement(
                        "select count(*) from log_actividad where evento='documento.exportado' and entidad_public_id=?")) {
            p.setObject(1, fixture.id());
            try (var r = p.executeQuery()) {
                r.next();
                return r.getLong(1);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String specificationPath(PresupuestoExportResourceIT.Fixture fixture) {
        return "/api/v1/documentos/especificaciones-tecnicas/" + fixture.id();
    }

    @Test
    void specificationsRemoteSuccessAuditsExactlyOnceAfterBytes() throws Exception {
        var fixture = specificationFixture();
        var response = given().auth()
                .oauth2(fixture.token())
                .queryParam("formato", "docx")
                .queryParam("titulo1", "  Título elegido  ")
                .get(specificationPath(fixture))
                .then()
                .statusCode(200)
                .contentType(ArchivoGenerado.DOCX_MEDIA_TYPE)
                .extract()
                .response();

        assertEquals(1, RemoteRenderStub.specificationCalls.get(), "must call production HTTP transport");
        assertEquals(0L, RemoteRenderStub.auditDuringRender.get().longValue());
        assertEquals(1L, auditCount(fixture));
        assertArrayEquals(new byte[] {'P', 'K', 3, 4}, response.asByteArray());
        assertEquals(
                "attachment; filename=\"especificaciones-tecnicas-" + fixture.project() + "-" + LocalDate.now()
                        + ".docx\"",
                response.header("Content-Disposition"));
        assertTrue(RemoteRenderStub.specificationAuthorization.get().startsWith("Bearer ey"));
        var projection = json.readTree(RemoteRenderStub.specificationProjection.get());
        assertEquals("Título elegido", projection.path("titulo1").asText());
        assertEquals("Título proyecto 2", projection.path("titulo2").asText());
        assertEquals(
                "Hormigón técnico",
                projection
                        .path("especificaciones")
                        .get(0)
                        .path("especificacionTecnica")
                        .asText());
        try (var c = ds.getConnection();
                var p = c.prepareStatement(
                        "select usuario_id, entidad, detalle->>'formato', detalle->>'bytes', "
                                + "detalle->>'stale' from log_actividad where evento='documento.exportado' and entidad_public_id=?")) {
            p.setObject(1, fixture.id());
            try (var r = p.executeQuery()) {
                assertTrue(r.next());
                assertEquals(fixture.caller(), r.getLong(1));
                assertEquals("presupuesto", r.getString(2));
                assertEquals("DOCX", r.getString(3));
                assertEquals("4", r.getString(4));
                assertEquals("false", r.getString(5));
                assertFalse(r.next());
            }
        }
    }

    @Test
    void specificationsRemote503DoesNotAuditOrFallBack() throws Exception {
        var fixture = specificationFixture();
        RemoteRenderStub.specificationStatus = 503;
        int status = given().auth()
                .oauth2(fixture.token())
                .get(specificationPath(fixture))
                .statusCode();
        assertEquals(1, RemoteRenderStub.specificationCalls.get(), "must attempt remote render");
        assertTrue(status >= 500, "remote failure must not return a locally generated document");
        assertEquals(0L, RemoteRenderStub.auditDuringRender.get().longValue());
        assertEquals(0L, auditCount(fixture));
    }

    @Test
    void specificationsRemoteTimeoutDoesNotAuditOrFallBack() throws Exception {
        var fixture = specificationFixture();
        RemoteRenderStub.specificationDelayMillis = 3500;
        int status = given().auth()
                .oauth2(fixture.token())
                .get(specificationPath(fixture))
                .statusCode();
        assertEquals(1, RemoteRenderStub.specificationCalls.get(), "must attempt remote render");
        assertEquals(0L, RemoteRenderStub.auditDuringRender.get().longValue());
        assertTrue(status >= 500, "timeout must not return a locally generated document");
        assertEquals(0L, auditCount(fixture));
    }

    @Test
    void specificationsPreflightRetains400And404WithoutRemoteCallOrAudit() throws Exception {
        var fixture = specificationFixture();
        var other = PresupuestoExportResourceIT.fixture(ds, mailbox, false);
        given().auth()
                .oauth2(fixture.token())
                .get(specificationPath(fixture) + "?formato=pdf")
                .then()
                .statusCode(400);
        given().auth()
                .oauth2(fixture.token())
                .get("/api/v1/documentos/especificaciones-tecnicas/bad")
                .then()
                .statusCode(400);
        given().auth()
                .oauth2(other.token())
                .get(specificationPath(fixture))
                .then()
                .statusCode(404);
        try (var c = ds.getConnection()) {
            PresupuestoExportResourceIT.sql(
                    c, "update apu set especificacion_tecnica=null where presupuesto_id=?", fixture.budget());
        }
        given().auth()
                .oauth2(fixture.token())
                .get(specificationPath(fixture))
                .then()
                .statusCode(400);
        assertEquals(0, RemoteRenderStub.specificationCalls.get());
        assertEquals(0L, auditCount(fixture));
    }

    @Test
    void specificationsRemoteTitlesUseProjectAndThenDefaults() throws Exception {
        var fixture = specificationFixture();
        given().auth()
                .oauth2(fixture.token())
                .get(specificationPath(fixture))
                .then()
                .statusCode(200);
        assertEquals(1, RemoteRenderStub.specificationCalls.get());
        var projectTitles = json.readTree(RemoteRenderStub.specificationProjection.get());
        assertEquals("Título proyecto 1", projectTitles.path("titulo1").asText());
        assertEquals("Título proyecto 2", projectTitles.path("titulo2").asText());
        try (var c = ds.getConnection()) {
            PresupuestoExportResourceIT.sql(
                    c, "update proyecto set titulo_et_1=null, titulo_et_2=null where id=?", fixture.project());
        }
        given().auth()
                .oauth2(fixture.token())
                .queryParam("titulo1", " ")
                .get(specificationPath(fixture))
                .then()
                .statusCode(200);
        assertEquals(2, RemoteRenderStub.specificationCalls.get());
        var defaultTitles = json.readTree(RemoteRenderStub.specificationProjection.get());
        assertEquals("ESPECIFICACIONES TÉCNICAS", defaultTitles.path("titulo1").asText());
        assertEquals("Proyecto anterior", defaultTitles.path("titulo2").asText());
        assertEquals(2L, auditCount(fixture));
    }

    @Test
    void publicGetUsesRemoteOnlyForExportableBudgetAndApu() throws Exception {
        var fixture = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
        int before = RemoteRenderStub.calls.get();
        var blocked = PresupuestoExportResourceIT.fixture(ds, mailbox, false);
        given().auth()
                .oauth2(blocked.token())
                .get(blocked.path() + "?formato=xlsx")
                .then()
                .statusCode(409);
        assertEquals(before, RemoteRenderStub.calls.get());

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
                assertTrue(response.header("Content-Disposition")
                        .contains(fixture.id().toString()));
                assertTrue(response.header("Content-Disposition").contains("-v2." + format));
                assertArrayEquals(
                        "pdf".equals(format) ? new byte[] {'%', 'P', 'D', 'F'} : new byte[] {'P', 'K'},
                        response.asByteArray());
                assertTrue(RemoteRenderStub.lastProjection.get().contains("\"documento\":\"" + document + "\""));
                assertTrue(RemoteRenderStub.lastAuthorization.get().startsWith("Bearer ey"));
            }
        }
        assertEquals(before + 4, RemoteRenderStub.calls.get());
    }
}
