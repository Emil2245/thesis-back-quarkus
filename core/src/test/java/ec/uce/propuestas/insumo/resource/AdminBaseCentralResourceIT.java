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
import java.sql.ResultSet;
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

        String invented = "0192f6c4-7c8a-7000-8000-000000000999";
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/admin/bases-centrales/" + invented)
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
                .body("items.nombre", hasItem("MOP 2026"))
                .body("items", hasSize(1))
                .body("total", equalTo(1))
                .body("page", equalTo(0))
                .body("size", equalTo(25))
                .body("totalPaginas", equalTo(1));
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
                .body("items", hasSize(1))
                .body("items[0].archivada", equalTo(true));

        // Y la oculta con el default incluirArchivadas=false.
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(200)
                .body("items", hasSize(0));
    }

    // ========================================================================
    // Borrado — solo después de archivar (D-12)
    // ========================================================================

    @Test
    void TC_ABC_REST_08_borrar_antes_de_archivar_devuelve_409() {
        String adminToken = registrarSuperAdmin("admin-bloq@ex.com");

        String id = given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("nombre", "A borrar sin archivar"))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .delete("/api/v1/admin/bases-centrales/" + id)
                .then()
                .statusCode(409)
                .body("codigo", equalTo("base-no-archivada"));

        // La base sigue existiendo.
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(200)
                .body("items", hasSize(1));
    }

    @Test
    void TC_ABC_REST_09_borrar_despues_de_archivar_devuelve_204() {
        String adminToken = registrarSuperAdmin("admin-ok-borra@ex.com");

        String id = given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("nombre", "A archivar y borrar"))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .post("/api/v1/admin/bases-centrales/" + id + "/archivar")
                .then()
                .statusCode(200);

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .delete("/api/v1/admin/bases-centrales/" + id)
                .then()
                .statusCode(204);

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/admin/bases-centrales?incluirArchivadas=true")
                .then()
                .statusCode(200)
                .body("items", hasSize(0));
    }

    @Test
    void TC_ABC_REST_10_borrar_id_inventado_devuelve_404() {
        String adminToken = registrarSuperAdmin("admin-404@ex.com");
        String invented = "0192f6c4-7c8a-7000-8000-000000000999";

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .delete("/api/v1/admin/bases-centrales/" + invented)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
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
                .body("items[0].totalInsumos", equalTo(1));
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
                .body("items[0].totalInsumos", equalTo(0));
    }

    // ========================================================================
    // Preservación de copias PROYECTO tras borrar la CENTRAL original
    // ========================================================================

    @Test
    void TC_ABC_REST_16_borrar_central_no_toca_copias_proyecto() throws Exception {
        String adminToken = registrarSuperAdmin("admin-cascade@ex.com");
        String userToken = AuthSupport.registrarConToken(mailbox, "user-cascade@ex.com");

        String proyectoId = given().contentType(JSON)
                .header("Authorization", "Bearer " + userToken)
                .body(Map.of(
                        "nombreProyecto", "Aislado",
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

        String idCentral = given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of("nombre", "Cascade Central"))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // El admin crea un insumo en la central.
        given().contentType(JSON)
                .header("Authorization", "Bearer " + adminToken)
                .body(Map.of(
                        "codigo", "MAT-CEN",
                        "tipo", "MATERIAL",
                        "descripcion", "Origen central",
                        "unidad", "kg",
                        "precioUnitario", 1.10))
                .when()
                .post("/api/v1/admin/bases-centrales/" + idCentral + "/insumos")
                .then()
                .statusCode(201);

        // El usuario lo copia a su base PROYECTO.
        Long centralIdInterno = lookupBaseInterna(idCentral);
        given().header("Authorization", "Bearer " + userToken)
                .contentType(JSON)
                .body(Map.of("fuenteTipo", "CENTRAL", "baseId", idCentral))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/insumos/copiar")
                .then()
                .statusCode(200)
                .body("copiados", equalTo(1));

        long insumosProyectoAntes = contarInsumosProyecto(internalProyectoId(proyectoId));
        // Archivar y borrar la central.
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .post("/api/v1/admin/bases-centrales/" + idCentral + "/archivar")
                .then()
                .statusCode(200);
        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .delete("/api/v1/admin/bases-centrales/" + idCentral)
                .then()
                .statusCode(204);

        long insumosProyectoDespues = contarInsumosProyecto(internalProyectoId(proyectoId));
        org.junit.jupiter.api.Assertions.assertEquals(
                insumosProyectoAntes,
                insumosProyectoDespues,
                "La copia PROYECTO persiste aunque la CENTRAL original se elimine");
    }

    @Test
    void TC_035_listado_admin_devuelve_page_con_metadata_y_defaults() {
        String adminToken = registrarSuperAdmin("admin-page-defaults@ex.com");
        crearBaseCentral(adminToken, "Default page");

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].nombre", equalTo("Default page"))
                .body("total", equalTo(1))
                .body("page", equalTo(0))
                .body("size", equalTo(25))
                .body("totalPaginas", equalTo(1));
    }

    @Test
    void TC_035_listado_admin_aplica_paginacion_y_orden_estable() {
        String adminToken = registrarSuperAdmin("admin-page@ex.com");
        for (String nombre : java.util.List.of("Zulu", "Alfa", "Beta")) {
            crearBaseCentral(adminToken, nombre);
        }

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/admin/bases-centrales?page=1&size=2")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].nombre", equalTo("Zulu"))
                .body("total", equalTo(3))
                .body("page", equalTo(1))
                .body("size", equalTo(2))
                .body("totalPaginas", equalTo(2));
    }

    @Test
    void TC_035_listado_admin_rechaza_page_negativa() {
        String adminToken = registrarSuperAdmin("admin-page-invalid@ex.com");

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .get("/api/v1/admin/bases-centrales?page=-1")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_035_listado_admin_rechaza_size_fuera_de_limites() {
        String adminToken = registrarSuperAdmin("admin-size-invalid@ex.com");

        for (int size : new int[] {0, 201}) {
            given().header("Authorization", "Bearer " + adminToken)
                    .when()
                    .get("/api/v1/admin/bases-centrales?size=" + size)
                    .then()
                    .statusCode(400)
                    .body("codigo", equalTo("tamano-pagina-invalido"));
        }
    }

    @Test
    void TC_035_borrar_insumo_central_referenciado_directamente_devuelve_409() throws Exception {
        String email = "admin-ref@ex.com";
        String adminToken = registrarSuperAdmin(email);
        String idBase = crearBaseCentral(adminToken, "Base defensiva");
        String idInsumo = crearInsumoCentral(adminToken, idBase, "REF-1");
        insertarReferenciaApu(email, idInsumo);

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .delete("/api/v1/admin/bases-centrales/" + idBase + "/insumos/" + idInsumo)
                .then()
                .statusCode(409)
                .body("codigo", equalTo("insumo-en-uso"));
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

    private Long lookupBaseInterna(String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM base_insumos WHERE public_id = ?::uuid")) {
            ps.setObject(1, java.util.UUID.fromString(publicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /** Resuelve el {@code BIGINT} interno del proyecto a partir de su {@code publicId} UUIDv7. */
    private Long internalProyectoId(String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM proyecto WHERE public_id = ?")) {
            ps.setObject(1, java.util.UUID.fromString(publicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long contarInsumosProyecto(Long proyectoId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT count(*) FROM insumo i "
                        + "JOIN base_insumos b ON b.id = i.base_id "
                        + "WHERE b.tipo = 'PROYECTO' AND b.proyecto_id = ?")) {
            ps.setLong(1, proyectoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private String crearBaseCentral(String token, String nombre) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("nombre", nombre))
                .when()
                .post("/api/v1/admin/bases-centrales")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String crearInsumoCentral(String token, String baseId, String codigo) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "codigo", codigo,
                        "tipo", "MATERIAL",
                        "descripcion", "Referencia defensiva",
                        "unidad", "kg",
                        "precioUnitario", 1.0))
                .when()
                .post("/api/v1/admin/bases-centrales/" + baseId + "/insumos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private void insertarReferenciaApu(String email, String insumoPublicId) throws Exception {
        try (Connection con = ds.getConnection()) {
            long usuarioId = scalar(con, "SELECT id FROM usuario WHERE email = ?", email);
            long insumoId = scalar(con, "SELECT id FROM insumo WHERE public_id = ?::uuid", insumoPublicId);
            long proyectoId = insertarRetornandoId(
                    con,
                    "INSERT INTO proyecto(usuario_id,nombre_proyecto,anio,direccion_institucional) "
                            + "VALUES (?, 'Fixture defensivo', 2026, 'UCE') RETURNING id",
                    usuarioId);
            long presupuestoId = insertarRetornandoId(
                    con,
                    "INSERT INTO presupuesto(proyecto_id,version,es_vigente) VALUES (?, 1, true) RETURNING id",
                    proyectoId);
            long apuId = insertarRetornandoId(
                    con,
                    "INSERT INTO apu(presupuesto_id,codigo,descripcion,unidad) "
                            + "VALUES (?, 'DEF-1', 'Defensivo', 'u') RETURNING id",
                    presupuestoId);
            long seccionId = insertarRetornandoId(
                    con, "INSERT INTO apu_seccion(apu_id,tipo,orden) VALUES (?, 'MATERIAL', 1) RETURNING id", apuId);
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO apu_detalle(seccion_id,insumo_id,descripcion,orden,cantidad,unidad) "
                            + "VALUES (?, ?, 'Defensivo', 1, 1, 'kg')")) {
                ps.setLong(1, seccionId);
                ps.setLong(2, insumoId);
                ps.executeUpdate();
            }
        }
    }

    private long scalar(Connection con, String sql, Object value) throws Exception {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long insertarRetornandoId(Connection con, String sql, long parentId) throws Exception {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, parentId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
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
    void TC_ABC_REST_19_eliminar_path_uuid_v4_no_v7_devuelve_400() {
        String adminToken = registrarSuperAdmin("admin-v4-del@ex.com");
        String uuidV4 = "550e8400-e29b-41d4-a716-446655440000";

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .delete("/api/v1/admin/bases-centrales/" + uuidV4)
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

        given().header("Authorization", "Bearer " + adminToken)
                .when()
                .delete("/api/v1/admin/bases-centrales/no-es-uuid")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }
}
