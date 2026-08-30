package ec.uce.propuestas.proyecto.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Validación dinámica de los rangos configurables (N04 §A6) sobre
 * {@code PUT /proyectos/{id}/parametros} y el endpoint administrativo
 * {@code PUT /proyectos/parametros-sistema} (sólo SUPER_ADMIN).
 *
 * <p>Cubre: defaults del seed, widening de rango HM por admin, rechazo 400
 * fuera de rango, no-mutación al rechazar, y aislamiento por propietario.</p>
 *
 * <p>Plan 07 — los path params son UUIDv7 (identidad externa inmutable).</p>
 */
@QuarkusTest
class ParametrosRangoDinamicoTest {

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, "
                    + "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
        // Restaurar el singleton de parametros_sistema (V001 default + rango_hm_max default 0.2000).
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE parametros_sistema SET "
                        + "porcentaje_herramienta_menor = 0.0500, "
                        + "porcentaje_indirecto = NULL, "
                        + "iva = 0.1500, "
                        + "rango_hm_min = 0.0000, rango_hm_max = 0.2000, "
                        + "rango_ci_min = 0.0000, rango_ci_max = 1.0000, "
                        + "rango_descuento_min = 0.0000, rango_descuento_max = 0.5000, "
                        + "rango_iva_min = 0.0000, rango_iva_max = 0.3000, "
                        + "moneda = 'USD', "
                        + "updated_at = now() WHERE id = 1")) {
            ps.executeUpdate();
        }
    }

    private String crearProyecto(String token) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto", "Rangos dinamicos",
                        "codigo", "P-RANG-01",
                        "anio", (short) 2026,
                        "plazoEjecucion", (short) 6,
                        "plazoUnidad", "MES",
                        "direccionInstitucional", "GAD"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private void ascenderASuperAdmin(String email) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE usuario SET rol = 'SUPER_ADMIN' WHERE email = ?")) {
            ps.setString(1, email);
            ps.executeUpdate();
        }
    }

    private String registrar(String email) {
        return AuthSupport.registrarConToken(mailbox, email);
    }

    private String reLogin(String email) {
        return given().contentType(JSON)
                .body(Map.of("email", email, "password", "Pass1234", "recordarSesion", false))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
    }

    @Test
    void TC_WU06_defaults_reflejados_en_get_y_acepta_valor_dentro_del_rango() {
        String token = registrar("wu06-defaults@ex.com");
        String proyectoId = crearProyecto(token);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/parametros-sistema")
                .then()
                .statusCode(200)
                .body("rangoHmMax", equalTo(0.2000f))
                .body("rangoCiMax", equalTo(1.0000f))
                .body("rangoIvaMax", equalTo(0.3000f))
                .body("rangoDescuentoMax", equalTo(0.5000f));

        // %HM 0.0500 está dentro de [0.0000, 0.2000] — debe persistir.
        given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of(
                        "porcentajeHerramientaMenor", 0.0500,
                        "porcentajeIndirecto", 0.1800,
                        "iva", 0.1500,
                        "moneda", "USD"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/parametros")
                .then()
                .statusCode(200)
                .body("porcentajeHerramientaMenor", equalTo(0.0500f))
                .body("iva", equalTo(0.1500f));
    }

    @Test
    void TC_WU06_super_admin_puede_ampliar_rango_hm_y_usuario_puede_superar_default() throws Exception {
        String admin = "wu06-admin@ex.com";
        String adminToken = registrar(admin);
        ascenderASuperAdmin(admin);
        String adminFresh = reLogin(admin);

        // Widening: rango_hm_max 0.2000 → 0.3000 via SUPER_ADMIN.
        given().header("Authorization", "Bearer " + adminFresh)
                .contentType(JSON)
                .body(Map.ofEntries(
                        Map.entry("porcentajeHerramientaMenor", 0.0500),
                        Map.entry("iva", 0.1500),
                        Map.entry("rangoHmMin", 0.0000),
                        Map.entry("rangoHmMax", 0.3000),
                        Map.entry("rangoCiMin", 0.0000),
                        Map.entry("rangoCiMax", 1.0000),
                        Map.entry("rangoDescuentoMin", 0.0000),
                        Map.entry("rangoDescuentoMax", 0.5000),
                        Map.entry("rangoIvaMin", 0.0000),
                        Map.entry("rangoIvaMax", 0.3000),
                        Map.entry("moneda", "USD")))
                .when()
                .put("/api/v1/proyectos/parametros-sistema")
                .then()
                .statusCode(200)
                .body("rangoHmMax", equalTo(0.3000f));

        // Usuario normal ahora puede fijar 0.25 (>0.20 default, ≤0.30 nuevo rango).
        String userToken = registrar("wu06-user@ex.com");
        String proyectoId = crearProyecto(userToken);

        given().header("Authorization", "Bearer " + userToken)
                .contentType(JSON)
                .body(Map.of(
                        "porcentajeHerramientaMenor", 0.25,
                        "iva", 0.1500,
                        "moneda", "USD"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/parametros")
                .then()
                .statusCode(200)
                .body("porcentajeHerramientaMenor", equalTo(0.25f));
    }

    @Test
    void TC_WU06_fuera_de_rango_devuelve_400_y_no_persiste() {
        String token = registrar("wu06-rechazo@ex.com");
        String proyectoId = crearProyecto(token);

        // %HM 0.25 > default rango_hm_max 0.2000 → 400 validacion.
        given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of(
                        "porcentajeHerramientaMenor", 0.25,
                        "iva", 0.1500,
                        "moneda", "USD"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/parametros")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // %IVA 0.5 > default rango_iva_max 0.3000 → 400 validacion (mensaje incluye max).
        given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of(
                        "porcentajeHerramientaMenor", 0.0500,
                        "iva", 0.5000,
                        "moneda", "USD"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/parametros")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // %CI 1.5 > default rango_ci_max 1.0000 → 400 validacion.
        given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of(
                        "porcentajeHerramientaMenor", 0.0500,
                        "porcentajeIndirecto", 1.5,
                        "iva", 0.1500,
                        "moneda", "USD"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/parametros")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // Verificar que ningún intento persistió: el GET debe seguir mostrando defaults.
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + proyectoId + "/parametros")
                .then()
                .statusCode(200)
                .body("porcentajeHerramientaMenor", equalTo(0.0500f))
                .body("porcentajeIndirecto", equalTo(null))
                .body("iva", equalTo(0.1500f));
    }

    @Test
    void TC_WU06_usuario_normal_no_puede_editar_parametros_sistema() {
        String token = registrar("wu06-noadmin@ex.com");

        given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.ofEntries(
                        Map.entry("porcentajeHerramientaMenor", 0.0500),
                        Map.entry("iva", 0.1500),
                        Map.entry("rangoHmMin", 0.0000),
                        Map.entry("rangoHmMax", 0.3000),
                        Map.entry("rangoCiMin", 0.0000),
                        Map.entry("rangoCiMax", 1.0000),
                        Map.entry("rangoDescuentoMin", 0.0000),
                        Map.entry("rangoDescuentoMax", 0.5000),
                        Map.entry("rangoIvaMin", 0.0000),
                        Map.entry("rangoIvaMax", 0.3000)))
                .when()
                .put("/api/v1/proyectos/parametros-sistema")
                .then()
                .statusCode(403);
    }

    @Test
    void TC_WU06_otro_usuario_no_puede_editar_parametros_de_proyecto_ajeno_404() {
        String titular = registrar("wu06-dueño@ex.com");
        String proyectoId = crearProyecto(titular);

        String intruso = registrar("wu06-intruso@ex.com");

        given().header("Authorization", "Bearer " + intruso)
                .contentType(JSON)
                .body(Map.of(
                        "porcentajeHerramientaMenor", 0.0700,
                        "iva", 0.1500,
                        "moneda", "USD"))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/parametros")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        // El proyecto del titular permanece intacto (default 0.0500).
        given().header("Authorization", "Bearer " + titular)
                .when()
                .get("/api/v1/proyectos/" + proyectoId + "/parametros")
                .then()
                .statusCode(200)
                .body("porcentajeHerramientaMenor", equalTo(0.0500f));
    }
}
