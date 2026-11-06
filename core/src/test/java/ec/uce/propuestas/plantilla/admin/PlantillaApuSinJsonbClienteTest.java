package ec.uce.propuestas.plantilla.admin;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PlantillaApuSinJsonbClienteTest {

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        PlantillaApuAdminTestSupport.reset(ds, mailbox);
    }

    @Test
    void cliente_no_puede_enviar_snapshot_ni_tipo_server_authored() throws Exception {
        String apuId = PlantillaApuAdminTestSupport.crearApuDeUsuario(mailbox, "owner-jsonb@ex.com", "P40-J");
        String adminToken = PlantillaApuAdminTestSupport.registrarSuperAdmin(ds, mailbox, "admin-jsonb@ex.com");
        long plantillasAntes = contarPlantillas();

        given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body("""
                        {
                          "desdeApuId": "%s",
                          "nombre": "Intento JSONB",
                          "tipo": "PERSONAL",
                          "snapshot_secciones": {"secciones": []}
                        }
                        """.formatted(apuId))
                .when()
                .post("/api/v1/admin/plantillas-apu")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        assertEquals(plantillasAntes, contarPlantillas());
        assertEquals(0, contarLogs());
    }

    @Test
    void editar_rechaza_campos_server_authored_sin_mutar() throws Exception {
        String apuId = PlantillaApuAdminTestSupport.crearApuDeUsuario(mailbox, "owner-put-extra@ex.com", "P40-E");
        String adminToken = PlantillaApuAdminTestSupport.registrarSuperAdmin(ds, mailbox, "admin-put-extra@ex.com");
        String plantillaId = PlantillaApuAdminTestSupport.crearPlantilla(adminToken, apuId, "Original");
        long logsAntes = contarLogs();

        given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body("""
                        {"nombre":"Alterada","tipo":"PERSONAL","snapshot_secciones":{}}
                        """)
                .when()
                .put("/api/v1/admin/plantillas-apu/" + plantillaId)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        assertEquals("Original", nombrePlantilla(plantillaId));
        assertEquals(logsAntes, contarLogs());
    }

    private String nombrePlantilla(String plantillaId) throws Exception {
        try (var con = ds.getConnection();
                var ps = con.prepareStatement("SELECT nombre FROM plantilla_apu WHERE public_id = ?::uuid")) {
            ps.setString(1, plantillaId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private long contarPlantillas() throws Exception {
        return contar("SELECT count(*) FROM plantilla_apu");
    }

    private long contarLogs() throws Exception {
        return contar("SELECT count(*) FROM log_actividad WHERE evento = 'admin.plantilla_editada'");
    }

    private long contar(String sql) throws Exception {
        try (var con = ds.getConnection();
                var ps = con.prepareStatement(sql);
                var rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
