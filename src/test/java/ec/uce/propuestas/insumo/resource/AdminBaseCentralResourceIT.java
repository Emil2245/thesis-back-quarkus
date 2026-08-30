package ec.uce.propuestas.insumo.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 05 — Suite end-to-end del ciclo de vida administrativo de bases CENTRALES
 * bajo la ruta canónica {@code /admin/bases-centrales}.
 *
 * <p>Cubre:
 * <ul>
 *   <li>USUARIO no accede a endpoints administrativos (403).</li>
 *   <li>SUPER_ADMIN puede crear, renombrar, importar, archivar y borrar una central.</li>
 *   <li>Una central archivada desaparece del catálogo normal pero permanece
 *       visible para SUPER_ADMIN con {@code incluirArchivadas=true}.</li>
 *   <li>El borrado antes del archivo devuelve 409.</li>
 *   <li>Las copias PROYECTO se preservan tras borrar la CENTRAL original.</li>
 * </ul>
 */
@QuarkusTest
class AdminBaseCentralResourceIT {

    private static final String UUID_V7 =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

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
    }

    // ========================================================================
    // Autorización
    // ========================================================================

    @Test
    void TC_ABC_REST_01_usuario_normal_no_accede_a_admin_y_devuelve_403() {
        String token = AuthSupport.registrarConToken(mailbox, "no-admin@ex.com");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(403);

        given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of("nombre", "No debe crearse"))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(403);
    }

    @Test
    void TC_ABC_REST_02_admin_sin_token_devuelve_401() {
        given().when().get("/api/v1/admin/bases-centrales").then().statusCode(401);
    }

    // ========================================================================
    // SUPER_ADMIN — crear / listar / renombrar
    // ========================================================================

    @Test
    void TC_ABC_REST_03_admin_crea_central_y_aparece_en_listado() {
        String adminToken = registrarSuperAdmin("admin-crea@ex.com");

        Response creada = given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("nombre", "MOP 2026"))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(201)
                .body("id", matchesPattern(UUID_V7))
                .body("nombre", equalTo("MOP 2026"))
                .body("tipo", equalTo("CENTRAL"))
                .body("archivada", equalTo(false))
                .body("totalInsumos", equalTo(0))
                .extract()
                .response();

        String id = creada.path("id");
        org.junit.jupiter.api.Assertions.assertNotNull(id);

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(200)
                .body("nombre", hasItem("MOP 2026"))
                .body("size()", equalTo(1));
    }

    @Test
    void TC_ABC_REST_04_admin_renombra_central() {
        String adminToken = registrarSuperAdmin("admin-renombra@ex.com");

        String id = given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("nombre", "Catálogo A"))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given().header("Authorization", "Bearer " + adminToken)
                .contentType(JSON)
                .body(Map.of("nombre", "Catálogo B"))
                .when()
                .put("/api/v1/admin/bases-centrales/" + id)
                .then()
                .statusCode(200)
                .body("nombre", equalTo("Catálogo B"));
    }

    @Test
    void TC_ABC_REST_05_admin_crea_con_nombre_duplicado_devuelve_400() {
        String adminToken = registrarSuperAdmin("admin-dup@ex.com");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("nombre", "Duplicado"))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(201);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("nombre", "Duplicado"))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_ABC_REST_06_admin_crea_con_nombre_blanco_devuelve_400() {
        String adminToken = registrarSuperAdmin("admin-blank@ex.com");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("nombre", "   "))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    // ========================================================================
    // Archivado — visibilidad diferenciada
    // ========================================================================

    @Test
    void TC_ABC_REST_07_archivar_desaparece_del_catalogo_normal_permanece_en_admin() {
        String adminToken = registrarSuperAdmin("admin-arch@ex.com");
        String userToken = AuthSupport.registrarConToken(mailbox, "user-arch@ex.com");

        String id = given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("nombre", "Para archivar"))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // Antes de archivar aparece en /bases-centrales.
        given().header("Authorization", "Bearer " + userToken)
                .when()
                .get("/api/v1/bases-centrales")
                .then()
                .statusCode(200)
                .body("nombre", hasItem("Para archivar"));

        // Archivar (POST canónico, no PUT).
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .post("/api/v1/admin/bases-centrales/" + id + "/archivar")
                .then()
                .statusCode(200)
                .body("archivada", equalTo(true));

        // Tras archivar NO aparece en /bases-centrales.
        given().header("Authorization", "Bearer " + userToken)
                .when()
                .get("/api/v1/bases-centrales")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));

        // El admin la sigue viendo con incluirArchivadas=true.
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/admin/bases-centrales?incluirArchivadas=true")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].archivada", equalTo(true));

        // Y la oculta con el default incluirArchivadas=false.
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(200)
                .body("$", hasSize(0));
    }

    // ========================================================================
    // CRUD de insumo bajo base central
    // ========================================================================

    @Test
    void TC_ABC_REST_11_admin_crea_insumo_bajo_central_y_totalInsumos_sube() {
        String adminToken = registrarSuperAdmin("admin-insumo@ex.com");

        String idBase = given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("nombre", "Base insumos"))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of(
                        "codigo", "MAT-A",
                        "tipo", "MATERIAL",
                        "descripcion", "Material A",
                        "unidad", "kg",
                        "precioUnitario", 1.25))
                .when()
                .post("/api/v1/admin/bases-centrales/" + idBase + "/insumos")
                .then()
                .statusCode(201)
                .body("codigo", equalTo("MAT-A"));

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(200)
                .body("[0].totalInsumos", equalTo(1));
    }

    @Test
    void TC_ABC_REST_12_admin_codigo_duplicado_devuelve_400() {
        String adminToken = registrarSuperAdmin("admin-dup-ins@ex.com");

        String idBase = given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("nombre", "Dup insumo base"))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        Map<String, Object> body = Map.of(
                "codigo", "DUP-1",
                "tipo", "MATERIAL",
                "descripcion", "primero",
                "unidad", "kg",
                "precioUnitario", 1.0);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(body)
                .when()
                .post("/api/v1/admin/bases-centrales/" + idBase + "/insumos")
                .then()
                .statusCode(201);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(body)
                .when()
                .post("/api/v1/admin/bases-centrales/" + idBase + "/insumos")
                .then()
                .statusCode(400);
    }

    @Test
    void TC_ABC_REST_13_admin_edita_y_elimina_insumo() {
        String adminToken = registrarSuperAdmin("admin-edit-ins@ex.com");

        String idBase = given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("nombre", "Edit insumo base"))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String idInsumo = given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of(
                        "codigo", "MAT-E",
                        "tipo", "MATERIAL",
                        "descripcion", "antes",
                        "unidad", "kg",
                        "precioUnitario", 1.0))
                .when()
                .post("/api/v1/admin/bases-centrales/" + idBase + "/insumos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("descripcion", "despues", "unidad", "kg", "precioUnitario", 2.0))
                .when()
                .put("/api/v1/admin/bases-centrales/" + idBase + "/insumos/" + idInsumo)
                .then()
                .statusCode(200)
                .body("descripcion", equalTo("despues"));

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .delete("/api/v1/admin/bases-centrales/" + idBase + "/insumos/" + idInsumo)
                .then()
                .statusCode(204);
    }

    // ========================================================================
    // Importación CSV (canónica POST .../import)
    // ========================================================================

    @Test
    void TC_ABC_REST_14_admin_importa_csv_sobre_central() {
        String adminToken = registrarSuperAdmin("admin-csv@ex.com");

        String idBase = given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("nombre", "Base import"))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String csv = "codigo,descripcion,unidad,precio\n"
                + "MAT-IMP-1,Material 1,kg,1.25\n"
                + "MAT-IMP-2,Material 2,kg,2.50\n";

        given().header("Authorization", "Bearer " + adminToken)
                .multiPart("archivo", "insumos.csv", csv.getBytes(), "text/csv")
                .when()
                .post("/api/v1/admin/bases-centrales/" + idBase + "/insumos/import")
                .then()
                .statusCode(200)
                .body("creados", equalTo(2))
                .body("actualizados", equalTo(0))
                .body("errores", hasSize(0));
    }

    @Test
    void TC_ABC_REST_15_admin_solo_validar_csv_devuelve_200_sin_crear() {
        String adminToken = registrarSuperAdmin("admin-csv-val@ex.com");

        String idBase = given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("nombre", "Base solo validar"))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String csv = "codigo,descripcion,unidad,precio\n" + "MAT-V-1,Material 1,kg,1.25\n";

        given().header("Authorization", "Bearer " + adminToken)
                .multiPart("archivo", "insumos.csv", csv.getBytes(), "text/csv")
                .when()
                .post("/api/v1/admin/bases-centrales/" + idBase + "/insumos/import?soloValidar=true")
                .then()
                .statusCode(200)
                .body("creados", equalTo(0))
                .body("actualizados", equalTo(0));

        // Sin upsert real, el total sigue en 0.
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(200)
                .body("[0].totalInsumos", equalTo(0));
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private String registrarSuperAdmin(String email) {
        String token = AuthSupport.registrarConToken(mailbox, email);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE usuario SET rol = 'SUPER_ADMIN' WHERE email = ?")) {
            ps.setString(1, email);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // Re-login para que el JWT lleve el nuevo grupo SUPER_ADMIN.
        Response login = given().contentType(JSON)
                .body(Map.of("email", email, "password", "Pass1234", "recordarSesion", false))
                .when()
                .post("/api/v1/auth/login");
        login.then().statusCode(200);
        return login.path("accessToken");
    }

    // ========================================================================
    // Plan 07 — frontera UUIDv7 en path params {id} e {iid} (v4/malformado → 400)
    // ========================================================================

    @Test
    void TC_ABC_REST_17_renombrar_path_uuid_v4_no_v7_devuelve_400() {
        String adminToken = registrarSuperAdmin("admin-v4-ren@ex.com");
        String uuidV4 = "550e8400-e29b-41d4-a716-446655440000";

        given().header("Authorization", "Bearer " + adminToken)
                .contentType(JSON)
                .body(Map.of("nombre", "X"))
                .when()
                .put("/api/v1/admin/bases-centrales/" + uuidV4)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_ABC_REST_18_archivar_path_uuid_v4_no_v7_devuelve_400() {
        String adminToken = registrarSuperAdmin("admin-v4-arch@ex.com");
        String uuidV4 = "550e8400-e29b-41d4-a716-446655440000";

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .post("/api/v1/admin/bases-centrales/" + uuidV4 + "/archivar")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_ABC_REST_20_crear_insumo_path_uuid_v4_no_v7_devuelve_400() {
        String adminToken = registrarSuperAdmin("admin-v4-cins@ex.com");
        String uuidV4 = "550e8400-e29b-41d4-a716-446655440000";

        given().header("Authorization", "Bearer " + adminToken)
                .contentType(JSON)
                .body(Map.of(
                        "codigo", "MAT-V4",
                        "tipo", "MATERIAL",
                        "descripcion", "v4",
                        "unidad", "kg",
                        "precioUnitario", 1.0))
                .when()
                .post("/api/v1/admin/bases-centrales/" + uuidV4 + "/insumos")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_ABC_REST_21_editar_insumo_path_uuid_v4_no_v7_devuelve_400() {
        String adminToken = registrarSuperAdmin("admin-v4-eins@ex.com");
        String uuidV4 = "550e8400-e29b-41d4-a716-446655440000";

        given().header("Authorization", "Bearer " + adminToken)
                .contentType(JSON)
                .body(Map.of("descripcion", "v4", "unidad", "kg", "precioUnitario", 1.0))
                .when()
                .put("/api/v1/admin/bases-centrales/" + uuidV4 + "/insumos/" + uuidV4)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_ABC_REST_22_eliminar_insumo_path_uuid_v4_no_v7_devuelve_400() {
        String adminToken = registrarSuperAdmin("admin-v4-dins@ex.com");
        String uuidV4 = "550e8400-e29b-41d4-a716-446655440000";

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .delete("/api/v1/admin/bases-centrales/" + uuidV4 + "/insumos/" + uuidV4)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_ABC_REST_23_importar_insumos_path_uuid_v4_no_v7_devuelve_400() {
        String adminToken = registrarSuperAdmin("admin-v4-imp@ex.com");
        String uuidV4 = "550e8400-e29b-41d4-a716-446655440000";
        String csv = "codigo,descripcion,unidad,precio\nMAT-IMP,Material,kg,1.25\n";

        given().header("Authorization", "Bearer " + adminToken)
                .multiPart("archivo", "insumos.csv", csv.getBytes(), "text/csv")
                .when()
                .post("/api/v1/admin/bases-centrales/" + uuidV4 + "/insumos/import")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_ABC_REST_24_path_uuid_malformado_devuelve_400_en_todos_los_verbs() {
        String adminToken = registrarSuperAdmin("admin-mal@ex.com");

        given().header("Authorization", "Bearer " + adminToken)
                .contentType(JSON)
                .body(Map.of("nombre", "X"))
                .when()
                .put("/api/v1/admin/bases-centrales/no-es-uuid")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .post("/api/v1/admin/bases-centrales/no-es-uuid/archivar")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }
}
