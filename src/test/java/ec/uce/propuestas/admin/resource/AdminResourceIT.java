package ec.uce.propuestas.admin.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AdminResourceIT {

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE log_actividad, actividad, cronograma, apu_detalle, apu_seccion, "
                    + "apu, rubro, capitulo, presupuesto, insumo, base_insumos, parametros_proyecto, "
                    + "firmante, proyecto, token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    private String registrarAdmin() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "admin@uce.edu.ec");
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("UPDATE usuario SET rol = 'SUPER_ADMIN' WHERE email = 'admin@uce.edu.ec'");
        }
        // Re-login to get token with SUPER_ADMIN group
        return AuthSupport.registrarConToken(mailbox, "admin-relogin@uce.edu.ec");
    }

    private String registrarSuperAdmin() throws Exception {
        // Register, then promote via SQL, then re-login for fresh JWT with SUPER_ADMIN group
        String email = "superadm@uce.edu.ec";
        AuthSupport.registrarConToken(mailbox, email);
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("UPDATE usuario SET rol = 'SUPER_ADMIN' WHERE email = '" + email + "'");
        }
        // Login again to get new JWT with updated role
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
    void admin_listar_editar_desactivar_usuarios() throws Exception {
        String adminToken = registrarSuperAdmin();
        String userToken = AuthSupport.registrarConToken(mailbox, "normaluser@uce.edu.ec");

        // List users (admin should see all)
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/admin/usuarios")
                .then()
                .statusCode(200)
                .body("items.size()", greaterThanOrEqualTo(2));

        // Get the normal user's id
        Number userId = given().header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/admin/usuarios")
                .then()
                .statusCode(200)
                .extract()
                .path("items.find { it.email == 'normaluser@uce.edu.ec' }.id");

        // Edit user name
        given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("nombre", "Editado Admin"))
                .when()
                .put("/api/v1/admin/usuarios/" + userId)
                .then()
                .statusCode(200)
                .body("nombre", equalTo("Editado Admin"));

        // Deactivate user
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .post("/api/v1/admin/usuarios/" + userId + "/desactivar")
                .then()
                .statusCode(200)
                .body("activo", is(false));

        // Reactivate user
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .post("/api/v1/admin/usuarios/" + userId + "/reactivar")
                .then()
                .statusCode(200)
                .body("activo", is(true));

        // Normal user cannot access admin endpoints
        given().header("Authorization", "Bearer " + userToken)
                .when()
                .get("/api/v1/admin/usuarios")
                .then()
                .statusCode(403);
    }

    @Test
    void admin_parametros_sistema() throws Exception {
        String adminToken = registrarSuperAdmin();

        // Get parametros
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/admin/parametros-sistema")
                .then()
                .statusCode(200)
                .body("iva", notNullValue());

        // Update parametros
        given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("iva", 0.15, "moneda", "EUR"))
                .when()
                .put("/api/v1/admin/parametros-sistema")
                .then()
                .statusCode(200)
                .body("moneda", equalTo("EUR"));
    }

    @Test
    void admin_bases_centrales_crud() throws Exception {
        String adminToken = registrarSuperAdmin();

        // Create central base
        Number baseId = given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("nombre", "Base Test IESS"))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(201)
                .body("nombre", equalTo("Base Test IESS"))
                .body("archivada", is(false))
                .extract()
                .path("id");

        // List (should include new base)
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(1));

        // Archive
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .post("/api/v1/admin/bases-centrales/" + baseId + "/archivar")
                .then()
                .statusCode(200)
                .body("archivada", is(true));

        // List without archived -> empty (only the one we created)
        // Unarchive
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .post("/api/v1/admin/bases-centrales/" + baseId + "/archivar")
                .then()
                .statusCode(200)
                .body("archivada", is(false));

        // D-12: central bases archived, never deleted — DELETE must not exist
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .delete("/api/v1/admin/bases-centrales/" + baseId)
                .then()
                .statusCode(405);
    }

    @Test
    void admin_logs_registrados() throws Exception {
        String adminToken = registrarSuperAdmin();

        // Create a base (triggers log)
        given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("nombre", "Base para log"))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(201);

        // Check logs
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/admin/logs")
                .then()
                .statusCode(200)
                .body("items.size()", greaterThanOrEqualTo(1))
                .body("items[0].evento", equalTo("base.creada"));
    }
}
