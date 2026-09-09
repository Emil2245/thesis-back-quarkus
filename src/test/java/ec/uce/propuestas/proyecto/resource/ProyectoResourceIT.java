package ec.uce.propuestas.proyecto.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
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
 * Plan 07 — los path params de {@code /proyectos/*} son UUIDv7 (identidad externa
 * inmutable). El id del JSON es el {@code publicId} UUIDv7, nunca el {@code BIGINT}
 * interno. UUID mal formado → 400 validacion. Recurso ajeno → 404 no-encontrado.
 */
@QuarkusTest
class ProyectoResourceIT {

    private static final String UUID_V7 =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

    /** UUIDv7 inexistente pero bien formado — usado para verificar 404 de la capa de owner. */
    private static final String UUID_INEXISTENTE_V7 = "0192f6c4-7c8a-7000-8000-000000000000";

    /** UUIDv4 (no v7) bien formado — usado para verificar la frontera de validación 400. */
    private static final String UUID_NO_V7 = "550e8400-e29b-41d4-a716-446655440000";

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

    private String crearProyecto(String token, String nombre) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto",
                        nombre,
                        "codigo",
                        "P-2026-01",
                        "anio",
                        (short) 2026,
                        "plazoEjecucion",
                        (short) 6,
                        "plazoUnidad",
                        "MES",
                        "direccionInstitucional",
                        "GAD Municipal"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    @Test
    void TC_P07_crear_devuelve_id_uuidv7_y_listar_propios() {
        String token = AuthSupport.registrarConToken(mailbox, "titular@ex.com");
        String id = crearProyecto(token, "Puente Tulcán");

        // El id del JSON es un UUIDv7 (no publicId, no BIGINT).
        org.junit.jupiter.api.Assertions.assertTrue(
                id.matches(UUID_V7), "El id del response debe ser un UUIDv7: " + id);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos")
                .then()
                .statusCode(200)
                .body("items[0].id", equalTo(id))
                .body("items[0].nombreProyecto", equalTo("Puente Tulcán"))
                .body("items[0].estado", equalTo("BORRADOR"))
                .body("items[0].id", matchesPattern(UUID_V7));
    }

    /**
     * Plan 021 — TC-P06-01 extendido: al crear un proyecto debe existir
     * exactamente una fila {@code presupuesto(version=1, es_vigente=TRUE)}
     * con {@code public_id} UUIDv7 (auto-create en la misma transacción).
     * El listado de versiones de ese proyecto expone la versión recién
     * creada con {@code esVigente=true} y {@code totalGeneral="0.000000"}.
     */
    @Test
    void TC_P21_auto_create_v1_vigente_en_crear_proyecto() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p21-auto@ex.com");
        String proyectoId = crearProyecto(token, "Auto-create P21");

        // El listado de versiones del proyecto debe devolver exactamente 1 vigente v1.
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + proyectoId + "/presupuestos")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].version", is(1))
                .body("[0].esVigente", is(true))
                .body("[0].totalGeneral", equalTo("0.000000"))
                .body("[0].presupuestoId", matchesPattern(UUID_V7));

        // Lectura directa de BD: la fila existe con los flags contract correctos.
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT version, es_vigente, origen_id, total FROM presupuesto "
                                + "WHERE proyecto_id = (SELECT id FROM proyecto WHERE public_id = ?)")) {
            ps.setObject(1, java.util.UUID.fromString(proyectoId));
            try (ResultSet rs = ps.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(rs.next(), "Debe existir la fila presupuesto");
                org.junit.jupiter.api.Assertions.assertEquals((short) 1, rs.getShort(1));
                org.junit.jupiter.api.Assertions.assertTrue(rs.getBoolean(2));
                rs.getLong(3); // origen_id (puede ser 0 si es NULL en BD)
                org.junit.jupiter.api.Assertions.assertEquals(
                        0, rs.getBigDecimal(4).compareTo(java.math.BigDecimal.ZERO));
                org.junit.jupiter.api.Assertions.assertFalse(rs.next(), "No debe existir una segunda fila presupuesto");
            }
        }
    }

    @Test
    void TC_P07_lista_no_expone_proyectos_ajenos() {
        String titulo = AuthSupport.registrarConToken(mailbox, "titular2@ex.com");
        crearProyecto(titulo, "Vulcano");

        String intruso = AuthSupport.registrarConToken(mailbox, "intruso@ex.com");
        given().header("Authorization", "Bearer " + intruso)
                .when()
                .get("/api/v1/proyectos")
                .then()
                .statusCode(200)
                .body("items.size()", is(0));
    }

    @Test
    void TC_P07_acceso_proyecto_ajeno_con_uuidv7_devuelve_404() {
        String titular = AuthSupport.registrarConToken(mailbox, "titular2@ex.com");
        String id = crearProyecto(titular, "SoloDueño");

        String intruso = AuthSupport.registrarConToken(mailbox, "intruso2@ex.com");
        given().header("Authorization", "Bearer " + intruso)
                .when()
                .get("/api/v1/proyectos/" + id)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    @Test
    void TC_P07_parametros_sistema_lectura() {
        String token = AuthSupport.registrarConToken(mailbox, "sistema@ex.com");
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/parametros-sistema")
                .then()
                .statusCode(200)
                .body("iva", equalTo(0.1500f));
    }

    @Test
    void TC_P07_path_uuid_v4_no_v7_devuelve_400_validacion() {
        String token = AuthSupport.registrarConToken(mailbox, "wu07v4@ex.com");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + UUID_NO_V7)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P07_path_uuid_malformado_devuelve_400_validacion() {
        String token = AuthSupport.registrarConToken(mailbox, "wu07mal@ex.com");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/esto-no-es-un-uuid")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        given().header("Authorization", "Bearer " + token)
                .contentType(JSON)
                .body(Map.of("nombreProyecto", "X", "anio", (short) 2026, "direccionInstitucional", "X"))
                .when()
                .put("/api/v1/proyectos/basura")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P07_path_uuid_v7_inexistente_devuelve_404_no_encontrado() {
        String token = AuthSupport.registrarConToken(mailbox, "wu07nf@ex.com");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + UUID_INEXISTENTE_V7)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }
}
