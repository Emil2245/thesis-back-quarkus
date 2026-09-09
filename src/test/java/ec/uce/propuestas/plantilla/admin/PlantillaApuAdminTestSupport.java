package ec.uce.propuestas.plantilla.admin;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.restassured.response.Response;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

final class PlantillaApuAdminTestSupport {

    static final String UUID_V7 =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";
    static final String UUID_V4 = "550e8400-e29b-41d4-a716-446655440000";
    static final String UUID_V7_INEXISTENTE = "0192f6c4-7c8a-7000-8000-000000000999";

    private PlantillaApuAdminTestSupport() {}

    static void reset(DataSource ds, RecordingEnviadorCorreo mailbox) throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE log_actividad, apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, plantilla_apu, "
                    + "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    static String registrarSuperAdmin(DataSource ds, RecordingEnviadorCorreo mailbox, String email) {
        AuthSupport.registrarConToken(mailbox, email);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE usuario SET rol = 'SUPER_ADMIN' WHERE email = ?")) {
            ps.setString(1, email);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        Response login = given().contentType(JSON)
                .body(Map.of("email", email, "password", "Pass1234", "recordarSesion", false))
                .when()
                .post("/api/v1/auth/login");
        login.then().statusCode(200);
        return login.path("accessToken");
    }

    static String crearApuDeUsuario(RecordingEnviadorCorreo mailbox, String email, String codigo) {
        String token = AuthSupport.registrarConToken(mailbox, email);
        String proyectoId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto",
                        "Origen " + codigo,
                        "anio",
                        (short) 2026,
                        "plazoEjecucion",
                        (short) 4,
                        "plazoUnidad",
                        "MES",
                        "direccionInstitucional",
                        "UCE"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201)
                .extract()
                .path("id");
        String presupuestoId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + proyectoId + "/presupuestos")
                .then()
                .statusCode(200)
                .extract()
                .path("[0].presupuestoId");
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("codigo", codigo, "descripcion", "APU origen", "unidad", "u"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/apus")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201)
                .extract()
                .path("id");
    }

    static String crearPlantilla(String adminToken, String apuId, String nombre) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of(
                        "desdeApuId", apuId, "nombre", nombre, "descripcionRubro", "Descripción sin límite artificial"))
                .when()
                .post("/api/v1/admin/plantillas-apu")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(201)
                .extract()
                .path("id");
    }

    static String snapshotCrudo(DataSource ds, String plantillaId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT snapshot_secciones::text FROM plantilla_apu WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(plantillaId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    static long contarLogs(DataSource ds, String plantillaId, String operacion) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT count(*) FROM log_actividad WHERE evento = 'admin.plantilla_editada' "
                                + "AND entidad_public_id = ?::uuid AND detalle->>'operacion' = ? "
                                + "AND detalle->>'tipo' = 'SISTEMA'")) {
            ps.setString(1, plantillaId);
            ps.setString(2, operacion);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
