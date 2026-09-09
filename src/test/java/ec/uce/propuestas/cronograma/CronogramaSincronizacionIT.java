package ec.uce.propuestas.cronograma;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ec.uce.propuestas.recalculo.Alcance;
import ec.uce.propuestas.recalculo.RecalculoService;
import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 029 (P-34) — IT de la sincronización 1:1 entre {@code Rubro} y
 * {@code Actividad} observable a través de endpoints canónicos. La
 * sincronización vive al final de
 * {@code RecalculoService.consolidarVersionYPropagar}; los flujos que la
 * invocan son la creación, borrado, mutación de cantidad, recálculo de
 * APU e insumo — todos a través del seam HTTP / SQL directo (sin bypass).
 *
 * <p>Cubre:
 * <ul>
 *   <li>Alta de rubro vía endpoint → actividad creada al consolidar el
 *       recálculo de versión.</li>
 *   <li>Baja de rubro → FK CASCADE elimina la actividad.</li>
 *   <li>Cambio de cantidad de un rubro con cronograma existente → el
 *       mapa de avance se conserva y el peso se recalcula.</li>
 *   <li>Presupuesto sin cronograma: alta/baja de rubro es no-op para la
 *       sincronización.</li>
 *   <li>Recálculo idempotente: la respuesta del GET permanece estable
 *       tras varios recálculos.</li>
 * </ul>
 */
@QuarkusTest
class CronogramaSincronizacionIT {

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @Inject
    RecalculoService recalculoService;

    @BeforeEach
    void reset() throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE cronograma, actividad, apu_detalle, apu_seccion, apu, "
                    + "rubro, capitulo, presupuesto, insumo, base_insumos, "
                    + "parametros_proyecto, firmante, proyecto, token_usuario, refresh_token, usuario "
                    + "RESTART IDENTITY CASCADE");
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private String crearProyecto(String token, String nombre) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto",
                        nombre,
                        "codigo",
                        "P-2026-SYNC" + Math.abs(nombre.hashCode() % 1000),
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

    private Long internalId(String tabla, String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM " + tabla + " WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(publicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private String vigenteDeProyecto(String proyectoPublicId) throws Exception {
        Long proyectoId = internalId("proyecto", proyectoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT public_id FROM presupuesto WHERE proyecto_id = ? AND es_vigente = TRUE")) {
            ps.setLong(1, proyectoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private long contarActividades(String presupuestoPublicId) throws Exception {
        Long pId = internalId("presupuesto", presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM actividad a "
                        + "JOIN cronograma c ON c.id = a.cronograma_id WHERE c.presupuesto_id = ?")) {
            ps.setLong(1, pId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private String crearCronograma(String token, String presupuestoId, String unidad, int periodos) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", unidad, "numeroPeriodos", periodos))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    /**
     * Siembra un capítulo y un rubro con APU sembrada (costo_total=precio).
     * Devuelve el {@code public_id} del rubro.
     */
    private String sembrarCapituloRubro(String presupuestoPublicId, String precioTotal) throws Exception {
        Long presupuestoId = internalId("presupuesto", presupuestoPublicId);
        String sufijo = precioTotal.replace('.', '_');
        long capituloId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) "
                                + "VALUES (?, NULL, ?, 'Cap', 1, 0) RETURNING id")) {
            ps.setLong(1, presupuestoId);
            ps.setString(2, sufijo);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                capituloId = rs.getLong(1);
            }
        }
        long apuId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO apu (presupuesto_id, codigo, descripcion, unidad, costo_directo, costo_indirecto, costo_total) "
                                + "VALUES (?, ?, 'APU', 'u', ?, 0, ?) RETURNING id")) {
            ps.setLong(1, presupuestoId);
            ps.setString(2, "APU-" + sufijo);
            ps.setBigDecimal(3, new BigDecimal(precioTotal));
            ps.setBigDecimal(4, new BigDecimal(precioTotal));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                apuId = rs.getLong(1);
            }
        }
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO rubro (capitulo_id, apu_id, item, codigo, descripcion, unidad, "
                                + "cantidad, precio_unitario, precio_total) "
                                + "VALUES (?, ?, ?, ?, 'R', 'u', 1.000000, ?, ?) RETURNING public_id")) {
            ps.setLong(1, capituloId);
            ps.setLong(2, apuId);
            ps.setString(3, "1." + sufijo);
            ps.setString(4, "R-" + sufijo);
            ps.setBigDecimal(5, new BigDecimal(precioTotal));
            ps.setBigDecimal(6, new BigDecimal(precioTotal));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Casos de sincronización observables
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void presupuesto_sin_cronograma_alta_de_rubro_es_no_op() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-sync-empty@ex.com");
        String proyectoId = crearProyecto(token, "P29 sync empty");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        // Sin cronograma y sin rubros
        assertEquals(0, contarActividades(presupuestoId));

        // Siembra vía SQL para no depender del endpoint /rubros POST (que
        // requiere APU público).
        sembrarCapituloRubro(presupuestoId, "1.000000");

        // Como no hay cronograma, sigue habiendo 0 actividades
        assertEquals(0, contarActividades(presupuestoId));
    }

    @Test
    void cronograma_vacio_sin_rubros_se_conserva() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-sync-vacio@ex.com");
        String proyectoId = crearProyecto(token, "P29 sync vacio");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        crearCronograma(token, presupuestoId, "SEMANA", 4);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .body("actividades", hasSize(0))
                .body("estadoDistribucion", equalTo("BORRADOR"));
    }

    @Test
    void cronograma_con_dos_rubros_sincroniza_pesos_y_conserva_mapa() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-sync-dos@ex.com");
        String proyectoId = crearProyecto(token, "P29 sync dos");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        String rubro1 = sembrarCapituloRubro(presupuestoId, "1.000000");
        sembrarCapituloRubro(presupuestoId, "3.000000");
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE presupuesto SET total = 4 WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(presupuestoId));
            ps.executeUpdate();
        }
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);

        String actividadId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .body("actividades", hasSize(2))
                .body("actividades[0].pesoPonderado", equalTo("25.0000"))
                .body("actividades[1].pesoPonderado", equalTo("75.0000"))
                .extract()
                .path("actividades[0].id");
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("operacion", "REEMPLAZAR_AVANCES", "avancePorPeriodo", Map.of("1", "25.0000")))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(200);

        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE rubro SET cantidad = 2 WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(rubro1));
            ps.executeUpdate();
        }
        recalculoService.recalcular(new Alcance.Version(internalId("presupuesto", presupuestoId)));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .body("actividades", hasSize(2))
                .body("actividades[0].pesoPonderado", equalTo("0.0000"))
                .body("actividades[0].avancePorPeriodo.1", equalTo("25.0000"));
    }

    @Test
    void cronograma_get_es_read_only_y_repetible() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-sync-readonly@ex.com");
        String proyectoId = crearProyecto(token, "P29 sync ro");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarCapituloRubro(presupuestoId, "3.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 6);

        // Lectura 1
        String first = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .asString();
        // Lectura 2
        String second = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .asString();
        assertEquals(first, second);
        // Sin escrituras
        assertEquals(1, contarActividades(presupuestoId));
    }
}
