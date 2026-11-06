package ec.uce.propuestas.plantilla.admin;

import static ec.uce.propuestas.plantilla.admin.PlantillaApuAdminTestSupport.UUID_V4;
import static ec.uce.propuestas.plantilla.admin.PlantillaApuAdminTestSupport.UUID_V7;
import static ec.uce.propuestas.plantilla.admin.PlantillaApuAdminTestSupport.UUID_V7_INEXISTENTE;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PlantillaApuAdminResourceIT {

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        PlantillaApuAdminTestSupport.reset(ds, mailbox);
    }

    @Test
    void TC_P40_01_admin_crea_sistema_cross_owner_y_usuario_la_ve() throws Exception {
        String userToken = AuthSupport.registrarConToken(mailbox, "visible-p40@ex.com");
        String apuId = PlantillaApuAdminTestSupport.crearApuDeUsuario(mailbox, "owner-p40@ex.com", "P40-01");
        String adminToken = PlantillaApuAdminTestSupport.registrarSuperAdmin(ds, mailbox, "admin-p40@ex.com");

        Response creada = given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of(
                        "desdeApuId", apuId,
                        "nombre", "Plantilla sistema P40",
                        "descripcionRubro", "Descripción reutilizable"))
                .when()
                .post("/api/v1/admin/plantillas-apu")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201)
                .body("id", matchesPattern(UUID_V7))
                .body("nombre", equalTo("Plantilla sistema P40"))
                .body("tipo", equalTo("SISTEMA"))
                .body("usuarioId", equalTo(null))
                .extract()
                .response();

        String plantillaId = creada.path("id");
        assertEquals(1, PlantillaApuAdminTestSupport.contarLogs(ds, plantillaId, "crear"));

        given().header("Authorization", "Bearer " + userToken)
                .when()
                .get("/api/v1/plantillas-apu")
                .then()
                .statusCode(200)
                .body("id", hasItem(plantillaId))
                .body("find { it.id == '" + plantillaId + "' }.tipo", equalTo("SISTEMA"))
                .body("find { it.id == '" + plantillaId + "' }.usuarioId", equalTo(null));
    }

    @Test
    void admin_lista_con_page_q_y_tipo_default_sistema() {
        String apuId = PlantillaApuAdminTestSupport.crearApuDeUsuario(mailbox, "owner-list@ex.com", "P40-L");
        String adminToken = PlantillaApuAdminTestSupport.registrarSuperAdmin(ds, mailbox, "admin-list@ex.com");
        PlantillaApuAdminTestSupport.crearPlantilla(adminToken, apuId, "Alfa sistema");
        PlantillaApuAdminTestSupport.crearPlantilla(adminToken, apuId, "Beta sistema");

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/admin/plantillas-apu?q=beta&page=0&size=1")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].nombre", equalTo("Beta sistema"))
                .body("items[0].tipo", equalTo("SISTEMA"))
                .body("total", equalTo(1))
                .body("page", equalTo(0))
                .body("size", equalTo(1))
                .body("totalPaginas", equalTo(1));
    }

    @Test
    void admin_edita_y_borra_sistema_con_logs_exitosos() throws Exception {
        String apuId = PlantillaApuAdminTestSupport.crearApuDeUsuario(mailbox, "owner-mut@ex.com", "P40-M");
        String adminToken = PlantillaApuAdminTestSupport.registrarSuperAdmin(ds, mailbox, "admin-mut@ex.com");
        String plantillaId = PlantillaApuAdminTestSupport.crearPlantilla(adminToken, apuId, "Antes");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("nombre", "Después", "descripcionRubro", "Texto largo permitido"))
                .when()
                .put("/api/v1/admin/plantillas-apu/" + plantillaId)
                .then()
                .statusCode(200)
                .body("nombre", equalTo("Después"))
                .body("tipo", equalTo("SISTEMA"));
        assertEquals(1, PlantillaApuAdminTestSupport.contarLogs(ds, plantillaId, "editar"));

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .delete("/api/v1/admin/plantillas-apu/" + plantillaId)
                .then()
                .statusCode(204);
        assertEquals(1, PlantillaApuAdminTestSupport.contarLogs(ds, plantillaId, "borrar"));
        assertTrue(existeApu(apuId), "Borrar la plantilla no elimina el APU origen");
    }

    @Test
    void descripcion_text_supera_500_caracteres_sin_tope_artificial() {
        String apuId = PlantillaApuAdminTestSupport.crearApuDeUsuario(mailbox, "owner-text@ex.com", "P40-T");
        String adminToken = PlantillaApuAdminTestSupport.registrarSuperAdmin(ds, mailbox, "admin-text@ex.com");
        String descripcion = "x".repeat(800);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("desdeApuId", apuId, "nombre", "Descripción TEXT", "descripcionRubro", descripcion))
                .when()
                .post("/api/v1/admin/plantillas-apu")
                .then()
                .statusCode(201)
                .body("descripcionRubro", equalTo(descripcion));
    }

    @Test
    void filtro_tipo_no_expone_personales() throws Exception {
        String adminToken = PlantillaApuAdminTestSupport.registrarSuperAdmin(ds, mailbox, "admin-type@ex.com");

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/admin/plantillas-apu?tipo=PERSONAL")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void rechazos_no_emiten_y_usan_errores_canonicos() throws Exception {
        String adminToken = PlantillaApuAdminTestSupport.registrarSuperAdmin(ds, mailbox, "admin-errors@ex.com");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("desdeApuId", UUID_V4, "nombre", "Inválida"))
                .when()
                .post("/api/v1/admin/plantillas-apu")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("desdeApuId", UUID_V7_INEXISTENTE, "nombre", "Inexistente"))
                .when()
                .post("/api/v1/admin/plantillas-apu")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("nombre", "X"))
                .when()
                .put("/api/v1/admin/plantillas-apu/" + UUID_V4)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .delete("/api/v1/admin/plantillas-apu/" + UUID_V7_INEXISTENTE)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        assertEquals(0, contarLogsTotales());
    }

    @Test
    void usuario_regular_recibe_403_y_paginacion_invalida_400() {
        String userToken = AuthSupport.registrarConToken(mailbox, "user-forbidden-p40@ex.com");
        String adminToken = PlantillaApuAdminTestSupport.registrarSuperAdmin(ds, mailbox, "admin-page-errors@ex.com");

        given().header("Authorization", "Bearer " + userToken)
                .when()
                .get("/api/v1/admin/plantillas-apu")
                .then()
                .statusCode(403);

        for (int size : new int[] {0, 201}) {
            given().header("Authorization", "Bearer " + adminToken)
                    .when()
                    .get("/api/v1/admin/plantillas-apu?size=" + size)
                    .then()
                    .statusCode(400)
                    .body("codigo", equalTo("tamano-pagina-invalido"));
        }
    }

    private boolean existeApu(String apuId) throws Exception {
        try (var con = ds.getConnection();
                var ps = con.prepareStatement("SELECT EXISTS(SELECT 1 FROM apu WHERE public_id = ?::uuid)")) {
            ps.setString(1, apuId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private long contarLogsTotales() throws Exception {
        try (var con = ds.getConnection();
                var ps = con.prepareStatement(
                        "SELECT count(*) FROM log_actividad WHERE evento = 'admin.plantilla_editada'");
                var rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
