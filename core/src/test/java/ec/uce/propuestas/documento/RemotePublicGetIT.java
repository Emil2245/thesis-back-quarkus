package ec.uce.propuestas.documento;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(RemoteRenderProfile.class)
@QuarkusTestResource(value = RemoteRenderStub.class, restrictToAnnotatedClass = true)
class RemotePublicGetIT {
    @Inject
    DataSource ds;

    @Inject
    RecordingEnviadorCorreo mailbox;

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
