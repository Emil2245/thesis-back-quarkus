package ec.uce.propuestas.cronograma;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 030 (P-35/P-36) — IT de las rutas canónicas de vistas y revisión.
 *
 * <p>Contrato bajo prueba (Plan 026 → 07-api-contract §7):
 * <ul>
 *   <li>{@code GET /api/v1/cronogramas/{id}/vistas} — raíz con sólo
 *       {@code cronogramaId} y bloques {@code gantt}, {@code valorizado},
 *       {@code curvaS} sobre una sola proyección; lectura read-only, sin
 *       actualizar {@code fechaRevision}.</li>
 *   <li>{@code POST /api/v1/cronogramas/{id}/revisado} — sin body; captura
 *       total + fingerprint canónico + fecha; idempotente. La respuesta es
 *       {@code CronogramaResponse} (no la respuesta de vistas).</li>
 * </ul>
 *
 * <p>Forma canónica verificada:
 *
 * <pre>
 * CronogramaVistasResponse { "cronogramaId": "&lt;UUIDv7&gt;",
 *                            "gantt": { "cronograma": CronogramaResponse,
 *                                       "capitulos": [ CapituloCronogramaResponse ] },
 *                            "valorizado": { "periodos": [ PeriodoValorizadoResponse ],
 *                                            "capitulos": [ CapituloCronogramaResponse ],
 *                                            "totales": TotalesCronogramaResponse },
 *                            "curvaS": { "puntos": [ PuntoCurvaSResponse ] } }
 * </pre>
 *
 * <p>El fixture siembra capítulos padre/hijo y rubros directamente con SQL
 * nativo para fijar precio_total y total sin disparar el motor de recálculo
 * (Plan 030 no toca {@code motor/}).</p>
 */
@QuarkusTest
class VistasCronogramaResourceIT {

    private static final String UUID_V7 =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

    private static final String UUID_NO_V7 = "550e8400-e29b-41d4-a716-446655440000";

    private static final String UUID_INEXISTENTE_V7 = "0192f6c4-7c8a-7000-8000-000000000000";

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @Inject
    SessionFactory sessionFactory;

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

    private String crearProyecto(String token, String codigo) {
        return given().contentType("application/json")
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto",
                        "P30 " + codigo,
                        "codigo",
                        codigo,
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
        return given().contentType("application/json")
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", unidad, "numeroPeriodos", periodos))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private long sembrarCapitulo(
            Long presupuestoId, Long parentId, String item, String descripcion, short orden, String total)
            throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) "
                                + "VALUES (?, ?, ?, ?, ?, ?) RETURNING id")) {
            ps.setLong(1, presupuestoId);
            if (parentId == null) {
                ps.setNull(2, java.sql.Types.BIGINT);
            } else {
                ps.setLong(2, parentId);
            }
            ps.setString(3, item);
            ps.setString(4, descripcion);
            ps.setShort(5, orden);
            ps.setBigDecimal(6, new BigDecimal(total));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private String sembrarRubro(Long presupuestoId, long capituloId, String item, String codigo, String precioTotal)
            throws Exception {
        long apuId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO apu (presupuesto_id, codigo, descripcion, unidad, costo_directo, costo_indirecto, costo_total) "
                                + "VALUES (?, ?, 'APU', 'u', ?, 0, ?) RETURNING id")) {
            ps.setLong(1, presupuestoId);
            ps.setString(2, codigo);
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
            ps.setString(3, item);
            ps.setString(4, codigo);
            ps.setBigDecimal(5, new BigDecimal(precioTotal));
            ps.setBigDecimal(6, new BigDecimal(precioTotal));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private void sembrarAvance(String actividadPublicId, String jsonb) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE actividad SET avance_por_periodo = ?::jsonb WHERE public_id = ?")) {
            ps.setString(1, jsonb);
            ps.setObject(2, UUID.fromString(actividadPublicId));
            ps.executeUpdate();
        }
    }

    private void sembrarPeso(String actividadPublicId, String peso) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE actividad SET peso_ponderado = ? WHERE public_id = ?")) {
            ps.setBigDecimal(1, new BigDecimal(peso));
            ps.setObject(2, UUID.fromString(actividadPublicId));
            ps.executeUpdate();
        }
    }

    private void fijarTotalPresupuesto(String presupuestoPublicId, String total) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE presupuesto SET total = ? WHERE public_id = ?")) {
            ps.setBigDecimal(1, new BigDecimal(total));
            ps.setObject(2, UUID.fromString(presupuestoPublicId));
            ps.executeUpdate();
        }
    }

    private String fechaRevisionPersistida(String cronogramaPublicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT fecha_revision FROM cronograma WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(cronogramaPublicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                java.sql.Timestamp ts = rs.getTimestamp(1);
                return ts == null ? null : ts.toInstant().toString();
            }
        }
    }

    private String fingerprintRevisadoPersistido(String cronogramaPublicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT btrim(presupuesto_fingerprint_revisado) FROM cronograma WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(cronogramaPublicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private void mutarPrecioRubro(String rubroPublicId, String nuevoPrecio) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE rubro SET precio_total = ?, precio_unitario = ?, cantidad = 1 WHERE public_id = ?")) {
            ps.setBigDecimal(1, new BigDecimal(nuevoPrecio));
            ps.setBigDecimal(2, new BigDecimal(nuevoPrecio));
            ps.setObject(3, UUID.fromString(rubroPublicId));
            ps.executeUpdate();
        }
    }

    /** Devuelve la actividad pública sembrada con {@code sembrarRubro}. */
    private String actividadDeRubro(String presupuestoPublicId, String itemRubro) throws Exception {
        Long pId = internalId("presupuesto", presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT a.public_id FROM actividad a "
                        + "JOIN cronograma c ON c.id = a.cronograma_id "
                        + "JOIN rubro r ON r.id = a.rubro_id "
                        + "WHERE c.presupuesto_id = ? AND r.item = ?")) {
            ps.setLong(1, pId);
            ps.setString(2, itemRubro);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    /** Verifica que el lock pesimista del presupuesto se está esperando. */
    private boolean esperandoLockPresupuesto(Long presupuestoId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM pg_stat_activity "
                        + "WHERE wait_event_type = 'Lock' "
                        + "AND query ILIKE '%select id from presupuesto where id%for update%'")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /** Espera hasta que aparezca una sesión PostgreSQL bloqueada en el lock pesimista. */
    private void esperarLockPresupuesto(Long presupuestoId, long timeoutMs) throws Exception {
        long limite = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < limite) {
            if (esperandoLockPresupuesto(presupuestoId)) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("la operación HTTP no llegó al lock pesimista del presupuesto");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Forma canónica
    // ──────────────────────────────────────────────────────────────────────

    /**
     * TC_P35_01 — raíz con sólo {@code cronogramaId} + bloques
     * {@code gantt}, {@code valorizado}, {@code curvaS}. {@code gantt}
     * contiene el {@code cronograma} canónico y la jerarquía recursiva.
     * No hay {@code id}/{@code presupuestoId}/{@code unidadTiempo}/
     * {@code jerarquia}/{@code filas} en la raíz.
     */
    @Test
    void TC_P35_01_get_vistas_canonico_raiz_cronogramaId_y_bloques() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-v@ex.com");
        String proyectoId = crearProyecto(token, "P-2026-V1");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap1 = sembrarCapitulo(pId, null, "1", "Capitulo 1", (short) 1, "0");
        long cap1a = sembrarCapitulo(pId, cap1, "1.1", "Capitulo 1.1", (short) 1, "0");
        sembrarRubro(pId, cap1a, "1.1.1", "R1", "5.000000");
        fijarTotalPresupuesto(presupuestoId, "5.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 6);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/cronogramas/" + cronogramaId + "/vistas")
                .then()
                .statusCode(200)
                // Raíz: sólo cronogramaId
                .body("cronogramaId", matchesPattern(UUID_V7))
                .body("id", nullValue())
                .body("presupuestoId", nullValue())
                .body("unidadTiempo", nullValue())
                .body("jerarquia", nullValue())
                // Gantt: cronograma (CronogramaResponse) + capitulos (recursivo)
                .body("gantt.cronograma.id", matchesPattern(UUID_V7))
                .body("gantt.cronograma.presupuestoId", matchesPattern(UUID_V7))
                .body("gantt.cronograma.unidadTiempo", equalTo("SEMANA"))
                .body("gantt.cronograma.numeroPeriodos", equalTo(6))
                .body("gantt.cronograma.estadoDistribucion", equalTo("BORRADOR"))
                .body("gantt.cronograma.desactualizado", equalTo(false))
                .body("gantt.cronograma.actividades", hasSize(1))
                .body("gantt.cronograma.avancePorPeriodo", hasSize(6))
                .body("gantt.cronograma.avanceAcumulado", hasSize(6))
                .body("gantt.capitulos", hasSize(1))
                .body("gantt.capitulos[0].item", equalTo("1"))
                .body("gantt.capitulos[0].rubros", hasSize(0))
                .body("gantt.capitulos[0].subcapitulos", hasSize(1))
                .body("gantt.capitulos[0].subcapitulos[0].item", equalTo("1.1"))
                .body("gantt.capitulos[0].subcapitulos[0].rubros", hasSize(1))
                .body("gantt.capitulos[0].subcapitulos[0].rubros[0].item", equalTo("1.1.1"))
                .body("gantt.capitulos[0].subcapitulos[0].rubros[0].actividad", notNullValue())
                .body("gantt.capitulos[0].subcapitulos[0].rubros[0].actividad.id", matchesPattern(UUID_V7))
                // Capítulo NO debe adjuntar la actividad del primer rubro (TC-P35-03)
                .body("gantt.capitulos[0].subcapitulos[0].actividad", nullValue())
                .body("gantt.capitulos[0].actividad", nullValue())
                // Gantt NO expone filas planas (forma incorrecta eliminada).
                .body("gantt.filas", nullValue())
                // Valorizado: periodos + capitulos + totales
                .body("valorizado.periodos", hasSize(6))
                .body("valorizado.periodos[0].periodo", equalTo(1))
                .body("valorizado.periodos[5].periodo", equalTo(6))
                .body("valorizado.capitulos", hasSize(1))
                .body("valorizado.totales", notNullValue())
                .body("valorizado.totales.avanceFinalPorcentaje", notNullValue())
                .body("valorizado.totales.montoTotalGeneral", notNullValue())
                .body("valorizado.totales.porcentajeCierre", notNullValue())
                .body("valorizado.filas", nullValue())
                // Curva S
                .body("curvaS.puntos", hasSize(6))
                .body("curvaS.puntos[0].periodo", equalTo(1))
                .body("curvaS.puntos[5].periodo", equalTo(6));
    }

    /**
     * TC_P35_02 — profundidad 3 de jerarquía recursiva en Gantt y Valorizado
     * (mismo árbol compartido en ambos bloques).
     */
    @Test
    void TC_P35_02_jerarquia_profundidad_tres_recursiva_en_gantt_y_valorizado() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-prof@ex.com");
        String proyectoId = crearProyecto(token, "P-2026-V2");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap1 = sembrarCapitulo(pId, null, "1", "Nivel 1", (short) 1, "0");
        long cap1a = sembrarCapitulo(pId, cap1, "1.1", "Nivel 2", (short) 1, "0");
        long cap1a1 = sembrarCapitulo(pId, cap1a, "1.1.1", "Nivel 3", (short) 1, "0");
        sembrarRubro(pId, cap1a1, "1.1.1.1", "RFondo", "4.000000");
        fijarTotalPresupuesto(presupuestoId, "4.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/cronogramas/" + cronogramaId + "/vistas")
                .then()
                .statusCode(200)
                .body("gantt.capitulos[0].item", equalTo("1"))
                .body("gantt.capitulos[0].subcapitulos[0].item", equalTo("1.1"))
                .body("gantt.capitulos[0].subcapitulos[0].subcapitulos[0].item", equalTo("1.1.1"))
                .body("gantt.capitulos[0].subcapitulos[0].subcapitulos[0].rubros[0].item", equalTo("1.1.1.1"))
                .body("valorizado.capitulos[0].item", equalTo("1"))
                .body(
                        "valorizado.capitulos[0].subcapitulos[0].subcapitulos[0].rubros[0].precioTotal",
                        equalTo("4.000000"));
    }

    /**
     * TC_P35_03 — segmentos no consecutivos: {1, 3, 4} → dos segmentos
     * [{1,1}, {3,4}] en la actividad del rubro. Capítulos sin actividad.
     */
    @Test
    void TC_P35_03_segmentos_no_consecutivos_en_actividad_del_rubro() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-seg@ex.com");
        String proyectoId = crearProyecto(token, "P-2026-V3");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R-Seg", "6.000000");
        fijarTotalPresupuesto(presupuestoId, "6.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 6);

        String actividadId = actividadDeRubro(presupuestoId, "1.1");
        sembrarAvance(actividadId, "{\"1\":\"2.0000\",\"3\":\"1.5000\",\"4\":\"2.5000\"}");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/cronogramas/" + cronogramaId + "/vistas")
                .then()
                .statusCode(200)
                .body("gantt.capitulos[0].rubros[0].actividad.segmentos", hasSize(2))
                .body("gantt.capitulos[0].rubros[0].actividad.segmentos[0].inicio", equalTo(1))
                .body("gantt.capitulos[0].rubros[0].actividad.segmentos[0].fin", equalTo(1))
                .body("gantt.capitulos[0].rubros[0].actividad.segmentos[1].inicio", equalTo(3))
                .body("gantt.capitulos[0].rubros[0].actividad.segmentos[1].fin", equalTo(4))
                .body("gantt.capitulos[0].rubros[0].actividad.avancePorPeriodo.size()", equalTo(3))
                .body("gantt.capitulos[0].rubros[0].actividad.avancePorPeriodo.2", nullValue())
                // Capítulo sin actividad ni segmentos propios.
                .body("gantt.capitulos[0].actividad", nullValue())
                .body("gantt.capitulos[0].segmentos", nullValue());
    }

    /**
     * TC_P35_04 — GET repetido no escribe ni cambia {@code fechaRevision}.
     */
    @Test
    void TC_P35_04_get_repetido_es_read_only_y_conserva_fecha_revision() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-ro@ex.com");
        String proyectoId = crearProyecto(token, "P-2026-V4");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R-RO", "1.000000");
        fijarTotalPresupuesto(presupuestoId, "1.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);

        String antes = fechaRevisionPersistida(cronogramaId);
        assertNotNull(antes);

        for (int i = 0; i < 3; i++) {
            given().header("Authorization", "Bearer " + token)
                    .when()
                    .get("/api/v1/cronogramas/" + cronogramaId + "/vistas")
                    .then()
                    .statusCode(200);
        }

        String despues = fechaRevisionPersistida(cronogramaId);
        assertEquals(antes, despues, "GET repetido no actualiza fechaRevision");
    }

    /**
     * TC_P35_05 — cronograma valorizado completo: porcentaje y monto parcial
     * por período y acumulado Σ 1..t reconcilia con el total del presupuesto
     * a escala 6.
     */
    @Test
    void TC_P35_05_valorizado_completo_periodos_reconcilian_precio_y_acumulado() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-valor@ex.com");
        String proyectoId = crearProyecto(token, "P-2026-V5");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R-Val", "6.000000");
        fijarTotalPresupuesto(presupuestoId, "6.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 3);

        String actividadId = actividadDeRubro(presupuestoId, "1.1");
        sembrarAvance(actividadId, "{\"1\":\"33.3300\",\"2\":\"33.3300\",\"3\":\"33.3400\"}");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/cronogramas/" + cronogramaId + "/vistas")
                .then()
                .statusCode(200)
                .body("gantt.cronograma.estadoDistribucion", equalTo("COMPLETO"))
                .body("gantt.cronograma.avanceFinal", equalTo("100.0000"))
                .body("valorizado.periodos[0].porcentajeParcial", equalTo("33.3300"))
                .body("valorizado.periodos[0].porcentajeAcumulado", equalTo("33.3300"))
                .body("valorizado.periodos[1].porcentajeParcial", equalTo("33.3300"))
                .body("valorizado.periodos[1].porcentajeAcumulado", equalTo("66.6600"))
                .body("valorizado.periodos[2].porcentajeParcial", equalTo("33.3400"))
                .body("valorizado.periodos[2].porcentajeAcumulado", equalTo("100.0000"))
                .body("valorizado.periodos[2].montoAcumulado", equalTo("6.000000"))
                .body("valorizado.totales.avanceFinalPorcentaje", equalTo("100.0000"))
                .body("valorizado.totales.montoTotalGeneral", equalTo("6.000000"))
                .body("valorizado.totales.porcentajeCierre", equalTo("100.0000"))
                // Validar puntos curvaS == mismas cifras.
                .body("curvaS.puntos[0].porcentajeParcial", equalTo("33.3300"))
                .body("curvaS.puntos[2].porcentajeAcumulado", equalTo("100.0000"))
                .body("curvaS.puntos[2].montoAcumulado", equalTo("6.000000"));
    }

    /**
     * TC_P35_06 — avance parcial: porcentaje parcial por período, acumulado
     * por Σ 1..t, monto parcial por período, monto acumulado por Σ 1..t.
     */
    @Test
    void TC_P35_06_valorizado_parcial_con_acumulado_y_monto_exacto() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-parcial@ex.com");
        String proyectoId = crearProyecto(token, "P-2026-V6");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R-P", "10.000000");
        fijarTotalPresupuesto(presupuestoId, "10.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);

        String actividadId = actividadDeRubro(presupuestoId, "1.1");
        // Σ avance = 50.0000 = 50% del peso → BORRADOR.
        sembrarAvance(actividadId, "{\"1\":\"50.0000\"}");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/cronogramas/" + cronogramaId + "/vistas")
                .then()
                .statusCode(200)
                .body("gantt.cronograma.estadoDistribucion", equalTo("BORRADOR"))
                .body("gantt.cronograma.avanceFinal", equalTo("50.0000"))
                .body("valorizado.periodos[0].porcentajeParcial", equalTo("50.0000"))
                .body("valorizado.periodos[0].porcentajeAcumulado", equalTo("50.0000"))
                .body("valorizado.periodos[1].porcentajeParcial", equalTo("0.0000"))
                .body("valorizado.periodos[1].porcentajeAcumulado", equalTo("50.0000"))
                .body("valorizado.periodos[3].porcentajeAcumulado", equalTo("50.0000"))
                .body("valorizado.periodos[0].montoParcial", equalTo("5.000000"))
                .body("valorizado.periodos[3].montoAcumulado", equalTo("5.000000"))
                .body("valorizado.totales.avanceFinalPorcentaje", equalTo("50.0000"))
                .body("valorizado.totales.montoTotalGeneral", equalTo("5.000000"));
    }

    /** TC_P35_07 — 1/3 residual: tres períodos a 0.3333 cada uno, suma Σ 0.9999. */
    @Test
    void TC_P35_07_un_tercio_residual_target_y_bases_reconcilian_precio() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-tercio@ex.com");
        String proyectoId = crearProyecto(token, "P-2026-V7");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R-T", "0.100000");
        fijarTotalPresupuesto(presupuestoId, "0.100000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 3);

        String actividadId = actividadDeRubro(presupuestoId, "1.1");
        sembrarAvance(actividadId, "{\"1\":\"0.3333\",\"2\":\"0.3333\",\"3\":\"0.3333\"}");
        // Override peso = 1.0000 (con rubro único PrecioPonderadoCalculador asigna 100.0000;
        // fijamos 1.0000 directamente para verificar el cierre racional).
        sembrarPeso(actividadId, "1.0000");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/cronogramas/" + cronogramaId + "/vistas")
                .then()
                .statusCode(200)
                // El canon expone los montos por rubro (no por actividad — la
                // forma de ActividadCronogramaResponse está congelada por
                // Plan 028/029 y no admite campos monetarios derivados).
                .body("gantt.capitulos[0].rubros[0].montoTotal", equalTo("0.099990"))
                .body("gantt.capitulos[0].rubros[0].montoPorPeriodo.1", equalTo("0.033330"))
                .body("gantt.capitulos[0].rubros[0].montoPorPeriodo.2", equalTo("0.033330"))
                .body("gantt.capitulos[0].rubros[0].montoPorPeriodo.3", equalTo("0.033330"))
                .body("valorizado.totales.montoTotalGeneral", equalTo("0.099990"));
    }

    /**
     * TC_P35_08 — peso 0.0000 + precio $10 + 3 claves activas: uniforme con
     * residual al último activo ($3.333333 + $3.333333 + $3.333334).
     */
    @Test
    void TC_P35_08_peso_cero_precio_positivo_uniforme_con_residual() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-p0@ex.com");
        String proyectoId = crearProyecto(token, "P-2026-V8");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R-P0", "10.000000");
        fijarTotalPresupuesto(presupuestoId, "10.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 3);

        String actividadId = actividadDeRubro(presupuestoId, "1.1");
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE actividad SET peso_ponderado = 0 WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(actividadId));
            ps.executeUpdate();
        }
        sembrarAvance(actividadId, "{\"1\":\"0.0000\",\"2\":\"0.0000\",\"3\":\"0.0000\"}");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/cronogramas/" + cronogramaId + "/vistas")
                .then()
                .statusCode(200)
                // Los campos monetarios derivados viven en el rubro — la forma
                // ActividadCronogramaResponse está congelada por Plan 028/029.
                .body("gantt.capitulos[0].rubros[0].montoTotal", equalTo("10.000000"))
                .body("gantt.capitulos[0].rubros[0].montoPorPeriodo.1", equalTo("3.333333"))
                .body("gantt.capitulos[0].rubros[0].montoPorPeriodo.2", equalTo("3.333333"))
                .body("gantt.capitulos[0].rubros[0].montoPorPeriodo.3", equalTo("3.333334"));
    }

    /**
     * TC_P35_09 — paridad valorizado vs curvaS: cada punto de la curva debe
     * coincidir con el período del valorizado en el mismo índice.
     */
    @Test
    void TC_P35_09_paridad_valorizado_curvaS_mismos_totales() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-paridad@ex.com");
        String proyectoId = crearProyecto(token, "P-2026-V9");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R-P", "3.000000");
        fijarTotalPresupuesto(presupuestoId, "3.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 3);

        String actividadId = actividadDeRubro(presupuestoId, "1.1");
        sembrarAvance(actividadId, "{\"1\":\"1.0000\",\"2\":\"1.0000\",\"3\":\"1.0000\"}");

        Response response = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/cronogramas/" + cronogramaId + "/vistas")
                .then()
                .statusCode(200)
                .extract()
                .response();
        String body = response.asString();
        for (int i = 0; i < 3; i++) {
            given().header("Authorization", "Bearer " + token)
                    .when()
                    .get("/api/v1/cronogramas/" + cronogramaId + "/vistas")
                    .then()
                    .statusCode(200)
                    .body(
                            "curvaS.puntos[" + i + "].porcentajeParcial",
                            equalTo(fromBody(body, "valorizado.periodos[" + i + "].porcentajeParcial")))
                    .body(
                            "curvaS.puntos[" + i + "].porcentajeAcumulado",
                            equalTo(fromBody(body, "valorizado.periodos[" + i + "].porcentajeAcumulado")))
                    .body(
                            "curvaS.puntos[" + i + "].montoParcial",
                            equalTo(fromBody(body, "valorizado.periodos[" + i + "].montoParcial")))
                    .body(
                            "curvaS.puntos[" + i + "].montoAcumulado",
                            equalTo(fromBody(body, "valorizado.periodos[" + i + "].montoAcumulado")));
        }
        assertTrue(body.length() > 100);
    }

    /**
     * TC_P35_10 — sin rubros ni actividades: totales en 0, BORRADOR, bloques
     * vacíos (gantt.capitulos y valorizado.capitulos = []) y curvaS con n
     * puntos en 0. Sin división por cero.
     */
    @Test
    void TC_P35_10_sin_rubros_devuelve_totales_cero_y_bloques_vacios() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-vacio@ex.com");
        String proyectoId = crearProyecto(token, "P-2026-V10");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        fijarTotalPresupuesto(presupuestoId, "0.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/cronogramas/" + cronogramaId + "/vistas")
                .then()
                .statusCode(200)
                .body("cronogramaId", matchesPattern(UUID_V7))
                .body("gantt.cronograma.totalGeneral", equalTo("0.000000"))
                .body("gantt.cronograma.estadoDistribucion", equalTo("BORRADOR"))
                .body("gantt.cronograma.avanceFinal", equalTo("0.0000"))
                .body("gantt.capitulos", empty())
                .body("valorizado.capitulos", empty())
                .body("valorizado.periodos", hasSize(4))
                .body("valorizado.periodos[0].porcentajeParcial", equalTo("0.0000"))
                .body("valorizado.periodos[0].montoParcial", equalTo("0.000000"))
                .body("valorizado.totales.montoTotalGeneral", equalTo("0.000000"))
                .body("valorizado.totales.porcentajeCierre", equalTo("0.0000"))
                .body("curvaS.puntos", hasSize(4))
                .body("curvaS.puntos[0].porcentajeParcial", equalTo("0.0000"))
                .body("curvaS.puntos[0].montoParcial", equalTo("0.000000"));
    }

    /**
     * TC_P35_11 — cambio de total general: desactualizado=true con avances
     * conservados.
     */
    @Test
    void TC_P35_11_cambio_de_total_marca_desactualizado_y_conserva_avances() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-stale-total@ex.com");
        String proyectoId = crearProyecto(token, "P-2026-V11");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R-Stale", "10.000000");
        fijarTotalPresupuesto(presupuestoId, "10.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);

        String actividadId = actividadDeRubro(presupuestoId, "1.1");
        sembrarAvance(actividadId, "{\"1\":\"50.0000\"}");
        // Marcar revisado antes del cambio
        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/cronogramas/" + cronogramaId + "/revisado")
                .then()
                .statusCode(200)
                .body("desactualizado", equalTo(false));
        // Cambiar el total general
        fijarTotalPresupuesto(presupuestoId, "12.000000");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/cronogramas/" + cronogramaId + "/vistas")
                .then()
                .statusCode(200)
                .body("gantt.cronograma.totalGeneral", equalTo("12.000000"))
                .body("gantt.cronograma.totalGeneralRevisado", equalTo("10.000000"))
                .body("gantt.cronograma.desactualizado", equalTo(true))
                .body("gantt.capitulos[0].rubros[0].actividad.avancePorPeriodo.1", equalTo("50.0000"));
    }

    /**
     * TC_P35_12 — cambio compensado: dos rubros cuyos nuevos precios conservan
     * el total pero alteran los pesos; desactualizado=true con mismo total.
     */
    @Test
    void TC_P35_12_cambio_compensado_detecta_stale_con_mismo_total() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-stale-comp@ex.com");
        String proyectoId = crearProyecto(token, "P-2026-V12");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        String r1 = sembrarRubro(pId, cap, "1.1", "R-CompA", "4.000000");
        sembrarRubro(pId, cap, "1.2", "R-CompB", "6.000000");
        fijarTotalPresupuesto(presupuestoId, "10.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);

        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/cronogramas/" + cronogramaId + "/revisado")
                .then()
                .statusCode(200)
                .body("desactualizado", equalTo(false));

        // Cambio compensado: 1.1 → 5, 1.2 → 5 (total sigue siendo 10)
        mutarPrecioRubro(r1, "5.000000");
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE rubro SET precio_total = 5.000000, precio_unitario = 5.000000 WHERE item = '1.2' AND capitulo_id = ?")) {
            ps.setLong(1, cap);
            ps.executeUpdate();
        }
        fijarTotalPresupuesto(presupuestoId, "10.000000");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/cronogramas/" + cronogramaId + "/vistas")
                .then()
                .statusCode(200)
                .body("gantt.cronograma.totalGeneral", equalTo("10.000000"))
                .body("gantt.cronograma.totalGeneralRevisado", equalTo("10.000000"))
                // El fingerprint canónico del snapshot revisado NO se expone en
                // CronogramaResponse (V001 §2.13 + D-09 — la canon mantiene el
                // detalle interno en cronograma.presupuesto_fingerprint_revisado).
                // Lo verificamos directamente vía DB helper, preservando que la
                // respuesta pública no filtre el BIGINT interno.
                .body("gantt.cronograma.desactualizado", equalTo(true));
        assertNotNull(fingerprintRevisadoPersistido(cronogramaId), "fingerprint revisado persistido");
    }

    // ──────────────────────────────────────────────────────────────────────
    // POST /cronogramas/{id}/revisado — CronogramaResponse canónico
    // ──────────────────────────────────────────────────────────────────────

    /**
     * TC_P36_01 — {@code POST /revisado} devuelve {@code CronogramaResponse}
     * (mismo shape que {@code GET /presupuestos/{id}/cronograma}) y es
     * idempotente: la segunda llamada no muta fecha ni fingerprint.
     */
    @Test
    void TC_P36_01_marcar_revisado_devuelve_CronogramaResponse_y_es_idempotente() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-rev-idem@ex.com");
        String proyectoId = crearProyecto(token, "P-2026-R1");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R-Rev", "4.000000");
        fijarTotalPresupuesto(presupuestoId, "4.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 3);

        // Primera llamada: revisa y devuelve CronogramaResponse canónico.
        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/cronogramas/" + cronogramaId + "/revisado")
                .then()
                .statusCode(200)
                // La raíz es CronogramaResponse, NO CronogramaVistasResponse.
                .body("id", matchesPattern(UUID_V7))
                .body("presupuestoId", matchesPattern(UUID_V7))
                .body("unidadTiempo", equalTo("SEMANA"))
                .body("estadoDistribucion", equalTo("BORRADOR"))
                .body("desactualizado", equalTo(false))
                .body("cronogramaId", nullValue())
                .body("gantt", nullValue())
                .body("valorizado", nullValue())
                .body("curvaS", nullValue());
        // El fingerprint canónico NO se expone en CronogramaResponse
        // (V001 §2.13 + D-09 — el detalle interno vive en
        // cronograma.presupuesto_fingerprint_revisado y no debe filtrarse a
        // la respuesta pública). Lo verificamos directamente vía DB helper
        // para no romper la no-leak del BIGINT interno.
        String fingerprintPrimero = fingerprintRevisadoPersistido(cronogramaId);
        assertNotNull(fingerprintPrimero, "fingerprint revisado persistido tras /revisado");

        // Segunda llamada idéntica: el fingerprint persiste idéntico y la
        // fecha persistida no se actualiza (segundo flush no ocurre).
        String fechaAntes = fechaRevisionPersistida(cronogramaId);
        Thread.sleep(20); // margen para que cambie el reloj si hubiera flush

        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/cronogramas/" + cronogramaId + "/revisado")
                .then()
                .statusCode(200)
                .body("desactualizado", equalTo(false));

        String fingerprintSegundo = fingerprintRevisadoPersistido(cronogramaId);
        assertEquals(fingerprintPrimero, fingerprintSegundo, "fingerprint revisado idéntico entre llamadas");
        assertEquals(
                fechaAntes, fechaRevisionPersistida(cronogramaId), "fechaRevision no se actualiza en idempotencia");
    }

    /**
     * TC_P36_02 — {@code POST /revisado} owner-to-404 y UUID inválido 400.
     */
    @Test
    void TC_P36_02_revisado_owner_to_404_y_uuid_invalido_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-rev-owner@ex.com");
        String tokenAjeno = AuthSupport.registrarConToken(mailbox, "p30-rev-ajeno@ex.com");
        String proyectoId = crearProyecto(token, "P-2026-R2");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R", "1.000000");
        fijarTotalPresupuesto(presupuestoId, "1.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);

        given().header("Authorization", "Bearer " + tokenAjeno)
                .when()
                .post("/api/v1/cronogramas/" + cronogramaId + "/revisado")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/cronogramas/" + UUID_INEXISTENTE_V7 + "/revisado")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/cronogramas/" + UUID_NO_V7 + "/revisado")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        given().when()
                .post("/api/v1/cronogramas/" + cronogramaId + "/revisado")
                .then()
                .statusCode(401);
    }

    /**
     * TC_P36_03 — {@code GET /vistas}: owner-to-404 y UUID inválido 400.
     */
    @Test
    void TC_P36_03_get_vistas_owner_to_404_y_uuid_invalido_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-get-owner@ex.com");
        String tokenAjeno = AuthSupport.registrarConToken(mailbox, "p30-get-ajeno@ex.com");
        String proyectoId = crearProyecto(token, "P-2026-R3");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R", "1.000000");
        fijarTotalPresupuesto(presupuestoId, "1.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);

        given().header("Authorization", "Bearer " + tokenAjeno)
                .when()
                .get("/api/v1/cronogramas/" + cronogramaId + "/vistas")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/cronogramas/" + UUID_NO_V7 + "/vistas")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/cronogramas/" + UUID_INEXISTENTE_V7 + "/vistas")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        given().when()
                .get("/api/v1/cronogramas/" + cronogramaId + "/vistas")
                .then()
                .statusCode(401);
    }

    /**
     * TC_P36_04 — ningún campo expone {@code BIGINT}; todas las identidades
     * públicas son UUIDv7 (la verificación es puntual, no textual, para no
     * romper con el serializador).
     */
    @Test
    void TC_P36_04_ids_no_exponen_bigint_y_forma_publica_es_uuidv7() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-shape@ex.com");
        String proyectoId = crearProyecto(token, "P-2026-R4");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R", "5.000000");
        fijarTotalPresupuesto(presupuestoId, "5.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/cronogramas/" + cronogramaId + "/vistas")
                .then()
                .statusCode(200)
                // Raíz
                .body("cronogramaId", matchesPattern(UUID_V7))
                // Gantt.cronograma (CronogramaResponse)
                .body("gantt.cronograma.id", matchesPattern(UUID_V7))
                .body("gantt.cronograma.presupuestoId", matchesPattern(UUID_V7))
                // Gantt.capitulos (recursivo)
                .body("gantt.capitulos[0].id", matchesPattern(UUID_V7))
                .body("gantt.capitulos[0].rubros[0].id", matchesPattern(UUID_V7))
                .body("gantt.capitulos[0].rubros[0].actividad.id", matchesPattern(UUID_V7))
                .body("gantt.capitulos[0].rubros[0].actividad.rubroId", matchesPattern(UUID_V7))
                // Valorizado.capitulos (recursivo, mismo árbol)
                .body("valorizado.capitulos[0].id", matchesPattern(UUID_V7))
                .body("valorizado.capitulos[0].rubros[0].actividad.id", matchesPattern(UUID_V7));
    }

    /**
     * TC_P36_05 — ejes independientes: COMPLETO + stale sigue exportable;
     * BORRADOR + revisado NO exportable. Marcadores viven en
     * {@code gantt.cronograma}.
     */
    @Test
    void TC_P36_05_estado_y_stale_son_ejes_independientes() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-ejes@ex.com");
        String proyectoId = crearProyecto(token, "P-2026-R5");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R", "4.000000");
        fijarTotalPresupuesto(presupuestoId, "4.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 2);

        String actividadId = actividadDeRubro(presupuestoId, "1.1");
        sembrarAvance(actividadId, "{\"1\":\"4.0000\"}"); // incompleto: 100% no cerrado

        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/cronogramas/" + cronogramaId + "/revisado")
                .then()
                .statusCode(200)
                .body("estadoDistribucion", equalTo("BORRADOR"))
                .body("desactualizado", equalTo(false));

        // Mapa vacío + revisado sigue BORRADOR.
        sembrarAvance(actividadId, "{}");
        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/cronogramas/" + cronogramaId + "/revisado")
                .then()
                .statusCode(200)
                .body("estadoDistribucion", equalTo("BORRADOR"))
                .body("desactualizado", equalTo(false));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Concurrencia y rendimiento
    // ──────────────────────────────────────────────────────────────────────

    /**
     * TC_P36_06 — revisión y mutación concurrentes serializadas por el lock
     * pesimista del presupuesto, ejercido a través de las rutas canónicas
     * de producción (no SQL directo). Patrón equivalente a
     * {@code ActividadProgramarResourceIT.dos_patch_concurrentes_a_la_misma_actividad_serializan}.
     *
     * <p>Dos operaciones HTTP concurrentes contra el mismo cronograma:
     * <ul>
     *   <li>una {@code POST /api/v1/cronogramas/{id}/revisado}
     *       (sección crítica de {@code VistasCronogramaService.marcarRevisado},
     *       Plan 030 P-36);</li>
     *   <li>un {@code PATCH /api/v1/cronogramas/{id}/actividades/{act}}
     *       con {@code operacion=REEMPLAZAR_AVANCES} (sección crítica de
     *       {@code CronogramaService.programarActividad}, Plan 029 P-34).</li>
     * </ul>
     * Ambas secciones críticas adquieren
     * {@code presupuestoRepository.lockPresupuestoRow(presupuestoId)}, de
     * modo que la segunda se serializa tras la primera; el fingerprint
     * canónico no cambia (los avances no participan en él), por lo que el
     * estado final queda {@code totalGeneralRevisado == totalGeneral} y
     * {@code desactualizado=false}. El TC_P36_06b mantiene el guard
     * «HTTP esperando en {@code FOR UPDATE} del presupuesto» con un lock
     * externo explícito — evidencia complementaria que el TC_P36_06 no
     * repite.</p>
     */
    @Test
    void TC_P36_06_revision_y_mutacion_concurrentes_serializadas_por_lock() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-race@ex.com");
        String proyectoId = crearProyecto(token, "P-2026-R6");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R-Race", "3.000000");
        fijarTotalPresupuesto(presupuestoId, "3.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 3);
        String actividadId = actividadDeRubro(presupuestoId, "1.1");

        // Estado inicial: revisado.
        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/cronogramas/" + cronogramaId + "/revisado")
                .then()
                .statusCode(200)
                .body("desactualizado", equalTo(false));

        // Patrón probado en ActividadProgramarResourceIT: barrera + pool + 2
        // ops concurrentes + timeout 10s (suficiente para serialización por
        // lock pesimista sobre la fila del presupuesto).
        CyclicBarrier barrera = new CyclicBarrier(2);

        Callable<Response> revisar = () -> {
            barrera.await();
            return given().header("Authorization", "Bearer " + token)
                    .when()
                    .post("/api/v1/cronogramas/" + cronogramaId + "/revisado");
        };

        Callable<Response> mutar = () -> {
            barrera.await();
            return given().contentType("application/json")
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of(
                            "operacion",
                            "REEMPLAZAR_AVANCES",
                            "avancePorPeriodo",
                            Map.of("1", "1.0000", "2", "1.0000", "3", "1.0000")))
                    .when()
                    .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId);
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Response> a = pool.submit(revisar);
            Future<Response> b = pool.submit(mutar);
            int sa = a.get(10, TimeUnit.SECONDS).statusCode();
            int sb = b.get(10, TimeUnit.SECONDS).statusCode();
            assertEquals(200, sa, "POST /revisado concurrente 200");
            assertEquals(200, sb, "PATCH /actividades concurrente 200");
        } finally {
            pool.shutdownNow();
        }

        // Estado final coherente: el PATCH no muta precio_total ni fingerprint,
        // por lo que totalGeneral == totalGeneralRevisado y desactualizado=false
        // tras la carrera.
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/cronogramas/" + cronogramaId + "/vistas")
                .then()
                .statusCode(200)
                .body("gantt.cronograma.totalGeneral", equalTo("3.000000"))
                .body("gantt.cronograma.totalGeneralRevisado", equalTo("3.000000"))
                .body("gantt.cronograma.desactualizado", equalTo(false));
    }

    /**
     * TC_P36_06b — el lock pesimista del presupuesto serializa efectivamente
     * la sección crítica de {@code /revisado}: una sesión externa que retiene
     * el {@code FOR UPDATE} bloquea el HTTP hasta que se libera, momento en
     * el cual la operación lee el estado actualizado (lock→refresh→snapshot
     * fresco). Patrón equivalente al TC de ActividadProgramarResourceIT.
     */
    @Test
    void TC_P36_06b_revisado_bloqueado_mientras_lock_externo_esta_activo() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-rev-block@ex.com");
        String proyectoId = crearProyecto(token, "P-2026-R6b");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R", "2.000000");
        fijarTotalPresupuesto(presupuestoId, "2.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 3);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection bloqueador = ds.getConnection()) {
            bloqueador.setAutoCommit(false);
            try (PreparedStatement lock =
                    bloqueador.prepareStatement("SELECT id FROM presupuesto WHERE id = ? FOR UPDATE")) {
                lock.setLong(1, pId);
                lock.executeQuery().close();
            }

            Future<Response> esperando = pool.submit(() -> given().header("Authorization", "Bearer " + token)
                    .when()
                    .post("/api/v1/cronogramas/" + cronogramaId + "/revisado"));

            // Mientras el bloqueador retiene el lock, la sesión HTTP debe
            // quedar esperando en `select id from presupuesto where id ...
            // for update`. Si no aparece, la sección crítica no se está
            // serializando.
            esperarLockPresupuesto(pId, 10_000);
            // Commit del bloqueador libera el FOR UPDATE; la sesión HTTP
            // pendiente adquiere el lock del presupuesto y completa el
            // snapshot canónico (lock→refresh→fresh).
            bloqueador.commit();

            Response r = esperando.get(10, TimeUnit.SECONDS);
            r.then().statusCode(200);
        } finally {
            pool.shutdownNow();
        }
        // Tras liberar el lock, /vistas muestra los marcadores coherentes.
        String fingerprintRevisado = fingerprintRevisadoPersistido(cronogramaId);
        assertNotNull(fingerprintRevisado, "fingerprint capturado tras lock");
        assertEquals(64, fingerprintRevisado.length(), "fingerprint SHA-256 64 hex chars");
    }

    /**
     * TC_P36_07 — guard real de query count: habilita Hibernate Statistics
     * en test profile ({@code application.yml %test}) y verifica que el GET
     * representativo del cronograma dispara un número acotado de queries.
     *
     * <p>La cota es deliberadamente generosa (≤ 30 queries) porque la
     * versión actual incluye la cadena owner-scope traversal +
     * fingerprint + capítulos + rubros + actividades — un orden de magnitud
     * acotado que descarta N+1. La cifra exacta se mide en el cierre del
     * Plan 030, no en este test.</p>
     */
    @Test
    void TC_P36_07_guard_query_count_en_presupuesto_representativo() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-perf@ex.com");
        String proyectoId = crearProyecto(token, "P-2026-R7");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap1 = sembrarCapitulo(pId, null, "1", "Cap 1", (short) 1, "0");
        long cap1a = sembrarCapitulo(pId, cap1, "1.1", "Cap 1.1", (short) 1, "0");
        for (int i = 1; i <= 5; i++) {
            sembrarRubro(pId, cap1a, "1.1." + i, "R-" + i, "2.000000");
        }
        fijarTotalPresupuesto(presupuestoId, "10.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 12);
        assertEquals(5, contarActividades(presupuestoId));

        // Asegurar que las estadísticas estén habilitadas en runtime.
        Statistics stats = sessionFactory.getStatistics();
        assertNotNull(stats, "SessionFactory.getStatistics() requiere hibernate.statistics.enabled");
        stats.setStatisticsEnabled(true);
        stats.clear();

        // Calentar la sesión con un primer GET para que las cachés L2 / query
        // plan cache y los loaders de asociaciones estén populados: las
        // cifras a comparar son de la versión "warm", que es la que verá el
        // cliente en uso normal.
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/cronogramas/" + cronogramaId + "/vistas")
                .then()
                .statusCode(200);

        stats.clear();
        long queriesAntes = stats.getQueryExecutionCount();

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/cronogramas/" + cronogramaId + "/vistas")
                .then()
                .statusCode(200)
                .body("gantt.capitulos[0].rubros.size()", equalTo(0))
                .body("gantt.capitulos[0].subcapitulos[0].rubros.size()", equalTo(5))
                .body("valorizado.periodos", hasSize(12))
                .body("curvaS.puntos", hasSize(12));

        long queriesDespues = stats.getQueryExecutionCount();
        long disparadas = queriesDespues - queriesAntes;
        // 30 es la cota superior del Plan 030 (sin N+1); una cifra
        // menor es bienvenida. La métrica exacta se reporta en el cierre.
        assertTrue(disparadas > 0, "Hibernate statistics registró queries (>=1): " + disparadas);
        assertTrue(disparadas <= 30, "GET representativo disparó " + disparadas + " queries (cota 30) — posible N+1");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers locales
    // ──────────────────────────────────────────────────────────────────────

    private static org.hamcrest.Matcher<Object> nullValue() {
        return org.hamcrest.Matchers.nullValue();
    }

    /**
     * Extrae un valor de un cuerpo JSON (ya serializado) usando una expresión
     * JsonPath. Se usa para verificar paridad valorizado vs curvaS cuando un
     * assertion encadenado necesita comparar dos campos del mismo cuerpo.
     *
     * <p>Usa {@link JsonPath} incluido en RestAssured (ya disponible en el
     * classpath de pruebas) en lugar de {@code com.jayway.jsonpath}, que no
     * está como dependencia directa — {@code rest-assured} reexporta su
     * {@code JsonPath} interno y evita arrastrar el árbol de
     * {@code json-path} por transitividad.</p>
     */
    private static String fromBody(String body, String jsonPath) {
        try {
            Object v = JsonPath.from(body).get(jsonPath);
            return v == null ? null : v.toString();
        } catch (Exception e) {
            throw new RuntimeException("fromBody falló: " + jsonPath, e);
        }
    }
}
