package ec.uce.propuestas.plantilla.admin;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 044 (bugs-pendientes §6) — plantillas de proyecto SISTEMA: las crea
 * SUPER_ADMIN desde un proyecto, USUARIO las ve, lee y aplica, pero no las
 * borra (404, igual que {@code plantilla_apu}).
 */
@QuarkusTest
class PlantillaProyectoSistemaIT {

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        PlantillaApuAdminTestSupport.reset(ds, mailbox);
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE plantilla_proyecto RESTART IDENTITY CASCADE");
        }
    }

    private static String crearProyecto(String token) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto", "Origen",
                        "anio", 2026,
                        "plazoEjecucion", 4,
                        "plazoUnidad", "MES",
                        "direccionInstitucional", "UCE"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String crearSistema(String adminToken) {
        String proyecto = crearProyecto(adminToken);
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("desdeProyectoId", proyecto, "nombre", "Edificio tipo", "descripcion", "Base"))
                .when()
                .post("/api/v1/admin/plantillas-proyecto")
                .then()
                .statusCode(201)
                .body("tipo", equalTo("SISTEMA"))
                .extract()
                .path("id");
    }

    @Test
    void TC_PPS_01_usuario_ve_lee_y_aplica_sistema_pero_no_la_borra() {
        String admin = PlantillaApuAdminTestSupport.registrarSuperAdmin(ds, mailbox, "pps-admin@ex.com");
        String sistema = crearSistema(admin);
        String usuario = AuthSupport.registrarConToken(mailbox, "pps-user@ex.com");

        given().header("Authorization", "Bearer " + usuario)
                .when()
                .get("/api/v1/plantillas-proyecto")
                .then()
                .statusCode(200)
                .body("find { it.id == '" + sistema + "' }.tipo", equalTo("SISTEMA"));

        given().header("Authorization", "Bearer " + usuario)
                .when()
                .get("/api/v1/plantillas-proyecto/" + sistema)
                .then()
                .statusCode(200)
                .body("tipo", equalTo("SISTEMA"));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + usuario)
                .body(Map.of("nombre", "Mi obra"))
                .when()
                .post("/api/v1/proyectos/desde-plantilla/" + sistema)
                .then()
                .statusCode(201)
                .body("proyecto.nombreProyecto", equalTo("Mi obra"));

        given().header("Authorization", "Bearer " + usuario)
                .when()
                .delete("/api/v1/plantillas-proyecto/" + sistema)
                .then()
                .statusCode(404);
    }

    @Test
    void TC_PPS_02_filtro_tipo_separa_sistema_de_personales() {
        String admin = PlantillaApuAdminTestSupport.registrarSuperAdmin(ds, mailbox, "pps-admin2@ex.com");
        String sistema = crearSistema(admin);
        String usuario = AuthSupport.registrarConToken(mailbox, "pps-user2@ex.com");
        String proyecto = crearProyecto(usuario);
        String personal = given().contentType(JSON)
                .header("Authorization", "Bearer " + usuario)
                .body(Map.of("nombre", "Propia"))
                .when()
                .post("/api/v1/proyectos/" + proyecto + "/guardar-plantilla")
                .then()
                .statusCode(201)
                .body("tipo", equalTo("PERSONAL"))
                .extract()
                .path("id");

        List<String> soloSistema = given().header("Authorization", "Bearer " + usuario)
                .when()
                .get("/api/v1/plantillas-proyecto?tipo=SISTEMA")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("id");
        assertEquals(List.of(sistema), soloSistema);

        given().header("Authorization", "Bearer " + usuario)
                .when()
                .get("/api/v1/plantillas-proyecto?tipo=PERSONAL")
                .then()
                .statusCode(200)
                .body("id", hasItem(personal))
                .body("id", not(hasItem(sistema)));

        given().header("Authorization", "Bearer " + usuario)
                .when()
                .get("/api/v1/plantillas-proyecto?tipo=OTRO")
                .then()
                .statusCode(400);
    }

    @Test
    void TC_PPS_03_admin_edita_y_borra_solo_sistema_y_usuario_no_accede_al_admin() {
        String admin = PlantillaApuAdminTestSupport.registrarSuperAdmin(ds, mailbox, "pps-admin3@ex.com");
        String sistema = crearSistema(admin);
        String usuario = AuthSupport.registrarConToken(mailbox, "pps-user3@ex.com");
        String proyecto = crearProyecto(usuario);
        String personal = given().contentType(JSON)
                .header("Authorization", "Bearer " + usuario)
                .body(Map.of("nombre", "Propia"))
                .when()
                .post("/api/v1/proyectos/" + proyecto + "/guardar-plantilla")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given().header("Authorization", "Bearer " + usuario)
                .when()
                .get("/api/v1/admin/plantillas-proyecto")
                .then()
                .statusCode(403);

        given().header("Authorization", "Bearer " + admin)
                .when()
                .get("/api/v1/admin/plantillas-proyecto")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("items[0].id", equalTo(sistema));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + admin)
                .body(Map.of("nombre", "Edificio renombrado"))
                .when()
                .put("/api/v1/admin/plantillas-proyecto/" + sistema)
                .then()
                .statusCode(200)
                .body("nombre", equalTo("Edificio renombrado"))
                .body("descripcion", equalTo("Base"));

        // Una PERSONAL no es gestionable desde el admin.
        given().header("Authorization", "Bearer " + admin)
                .when()
                .delete("/api/v1/admin/plantillas-proyecto/" + personal)
                .then()
                .statusCode(404);

        given().header("Authorization", "Bearer " + admin)
                .when()
                .delete("/api/v1/admin/plantillas-proyecto/" + sistema)
                .then()
                .statusCode(204);
    }
}
