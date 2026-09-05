package ec.uce.propuestas.cronograma;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 028 (P-33) — ciclo de vida y configuración del cronograma.
 *
 * <p>Contrato bajo prueba (Plan 026 → 07-api-contract §7):
 * <ul>
 *   <li>{@code GET /api/v1/presupuestos/{presupuestoId}/cronograma} — 200 con el
 *       read model o 404 {@code no-encontrado} si aún no existe/es ajeno.</li>
 *   <li>{@code POST /api/v1/presupuestos/{presupuestoId}/cronograma} — 201, alta
 *       1:1 con autoimportación de una actividad por rubro; duplicado 409
 *       {@code cronograma-ya-existe}.</li>
 *   <li>{@code PUT /api/v1/cronogramas/{cronogramaId}/configuracion} — 200
 *       idempotente; reducción/cambio de unidad con datos exige
 *       {@code confirmarPerdida=true}, de otro modo 409
 *       {@code configuracion-cronograma-requiere-confirmacion} sin mutación.</li>
 * </ul>
 *
 * <p>Los identificadores públicos son UUIDv7; el {@code BIGINT} interno nunca
 * cruza HTTP/JSON. Los fixtures se siembran con SQL nativo para fijar
 * {@code precio_total} y {@code presupuesto.total} sin disparar el motor de
 * recálculo (Plan 028 no toca {@code motor/} ni {@code recalculo/}).</p>
 */
@QuarkusTest
class CronogramaResourceIT {

    private static final String UUID_V7 =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

    /** UUIDv4 (no v7) bien formado — frontera 400. */
    private static final String UUID_NO_V7 = "550e8400-e29b-41d4-a716-446655440000";

    /** UUIDv7 bien formado pero inexistente — 404. */
    private static final String UUID_INEXISTENTE_V7 = "0192f6c4-7c8a-7000-8000-000000000000";

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

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
    // Helpers de fixture
    // ──────────────────────────────────────────────────────────────────────

    private String crearProyecto(String token, String nombre, String codigo) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto",
                        nombre,
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

    /**
     * Siembra un capítulo raíz y un rubro (con su APU) por cada precio total
     * indicado, y fija {@code presupuesto.total} a la suma. Devuelve los
     * {@code public_id} de los rubros en orden presupuestario ({@code item}
     * ascendente).
     */
    private List<String> sembrarRubros(String presupuestoPublicId, String... preciosTotales) throws Exception {
        Long presupuestoId = internalId("presupuesto", presupuestoPublicId);
        long capituloId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) "
                                + "VALUES (?, NULL, '1', 'Capitulo unico', 1, 0) RETURNING id")) {
            ps.setLong(1, presupuestoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                capituloId = rs.getLong(1);
            }
        }

        List<String> rubros = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < preciosTotales.length; i++) {
            BigDecimal precioTotal = new BigDecimal(preciosTotales[i]);
            total = total.add(precioTotal);
            long apuId;
            try (Connection con = ds.getConnection();
                    PreparedStatement ps =
                            con.prepareStatement("INSERT INTO apu (presupuesto_id, codigo, descripcion, unidad, "
                                    + "costo_directo, costo_indirecto, costo_total) "
                                    + "VALUES (?, ?, 'APU sembrado', 'u', ?, 0, ?) RETURNING id")) {
                ps.setLong(1, presupuestoId);
                ps.setString(2, "APU-" + (i + 1));
                ps.setBigDecimal(3, precioTotal);
                ps.setBigDecimal(4, precioTotal);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    apuId = rs.getLong(1);
                }
            }
            try (Connection con = ds.getConnection();
                    PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO rubro (capitulo_id, apu_id, item, codigo, descripcion, unidad, "
                                    + "cantidad, precio_unitario, precio_total) "
                                    + "VALUES (?, ?, ?, ?, ?, 'u', 1.000000, ?, ?) RETURNING public_id")) {
                ps.setLong(1, capituloId);
                ps.setLong(2, apuId);
                ps.setString(3, "1." + (i + 1));
                ps.setString(4, "R-" + (i + 1));
                ps.setString(5, "Rubro " + (i + 1));
                ps.setBigDecimal(6, precioTotal);
                ps.setBigDecimal(7, precioTotal);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    rubros.add(rs.getString(1));
                }
            }
        }

        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE presupuesto SET total = ? WHERE id = ?")) {
            ps.setBigDecimal(1, total);
            ps.setLong(2, presupuestoId);
            ps.executeUpdate();
        }
        return rubros;
    }

    private long contarCronogramas(String presupuestoPublicId) throws Exception {
        Long pId = internalId("presupuesto", presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT COUNT(*) FROM cronograma WHERE presupuesto_id = ?")) {
            ps.setLong(1, pId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
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

    private String avancePersistido(String actividadPublicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT avance_por_periodo::text FROM actividad WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(actividadPublicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    /** Escribe directamente el mapa JSONB de una actividad (fixture de avance). */
    private void sembrarAvance(String actividadPublicId, String jsonb) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE actividad SET avance_por_periodo = ?::jsonb WHERE public_id = ?")) {
            ps.setString(1, jsonb);
            ps.setObject(2, UUID.fromString(actividadPublicId));
            ps.executeUpdate();
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

    private String registrarSuperAdmin(String email) throws Exception {
        AuthSupport.registrarConToken(mailbox, email);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE usuario SET rol = 'SUPER_ADMIN' WHERE email = ?")) {
            ps.setString(1, email);
            ps.executeUpdate();
        }
        return given().contentType(JSON)
                .body(Map.of("email", email, "password", "Pass1234", "recordarSesion", false))
                .when()
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Alta (TC-P33-01, TC-P33-10)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * TC-P33-01 — alta con rubros {@code [1,1,4]}: 201, un cronograma, tres
     * actividades, mapas {@code {}}, identidades UUIDv7 y cierre residual del
     * peso a {@code 100.0000} (bases 100.0001 → residual −1 al mayor precio).
     */
    @Test
    void TC_P33_01_alta_con_rubros_autoimporta_una_actividad_por_rubro() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p28-alta@ex.com");
        String proyectoId = crearProyecto(token, "P28 alta", "P-2026-A1");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        List<String> rubros = sembrarRubros(presupuestoId, "1.000000", "1.000000", "4.000000");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 12))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(201)
                .body("id", matchesPattern(UUID_V7))
                .body("presupuestoId", equalTo(presupuestoId))
                .body("unidadTiempo", equalTo("SEMANA"))
                .body("numeroPeriodos", equalTo(12))
                .body("totalGeneral", equalTo("6.000000"))
                .body("totalGeneralRevisado", equalTo("6.000000"))
                .body("fechaRevision", matchesPattern("^\\d{4}-\\d{2}-\\d{2}T.*Z$"))
                .body("desactualizado", equalTo(false))
                .body("avanceFinal", equalTo("0.0000"))
                .body("avancePorPeriodo", hasSize(12))
                .body("avanceAcumulado", hasSize(12))
                .body("estadoDistribucion", equalTo("BORRADOR"))
                .body("actividades", hasSize(3))
                .body("actividades.rubroId", contains(rubros.get(0), rubros.get(1), rubros.get(2)))
                .body("actividades.item", contains("1.1", "1.2", "1.3"))
                .body("actividades.precioTotal", contains("1.000000", "1.000000", "4.000000"))
                .body("actividades.pesoPonderado", contains("16.6667", "16.6667", "66.6666"))
                .body("actividades[0].id", matchesPattern(UUID_V7))
                .body("actividades[0].avancePorPeriodo", equalTo(Map.of()))
                .body("actividades[0].segmentos", hasSize(0))
                .body("actividades[0].desviacion", equalTo("16.6667"));

        assertEquals(1, contarCronogramas(presupuestoId), "exactamente un cronograma");
        assertEquals(3, contarActividades(presupuestoId), "n rubros = n actividades");
    }

    /** Alta sobre presupuesto sin rubros: 201 con {@code actividades = []}. */
    @Test
    void alta_sin_rubros_crea_cronograma_vacio() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p28-vacio@ex.com");
        String proyectoId = crearProyecto(token, "P28 vacio", "P-2026-A2");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "MES", "numeroPeriodos", 6))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(201)
                .body("actividades", hasSize(0))
                .body("estadoDistribucion", equalTo("BORRADOR"));

        assertEquals(1, contarCronogramas(presupuestoId));
        assertEquals(0, contarActividades(presupuestoId));
    }

    /** TC-P33-10 — total cero: pesos {@code 0.0000} y estado {@code BORRADOR}. */
    @Test
    void TC_P33_10_total_cero_conserva_pesos_cero() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p28-cero@ex.com");
        String proyectoId = crearProyecto(token, "P28 cero", "P-2026-A3");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "0.000000", "0.000000");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 4))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(201)
                .body("totalGeneral", equalTo("0.000000"))
                .body("actividades.pesoPonderado", contains("0.0000", "0.0000"))
                .body("estadoDistribucion", equalTo("BORRADOR"));
    }

    /** TC-P33-02 — el segundo POST es 409 y no duplica filas. */
    @Test
    void TC_P33_02_post_duplicado_devuelve_409_sin_duplicar() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p28-dup@ex.com");
        String proyectoId = crearProyecto(token, "P28 dup", "P-2026-A4");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "10.000000");
        crearCronograma(token, presupuestoId, "SEMANA", 8);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "MES", "numeroPeriodos", 3))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(409)
                .body("codigo", equalTo("cronograma-ya-existe"));

        assertEquals(1, contarCronogramas(presupuestoId));
        assertEquals(1, contarActividades(presupuestoId));
    }

    /** TC-P33-08 — dos POST concurrentes: uno 201, otro 409; 1 cronograma / n actividades. */
    @Test
    void TC_P33_08_altas_concurrentes_serializan_en_201_y_409() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p28-conc@ex.com");
        String proyectoId = crearProyecto(token, "P28 concurrencia", "P-2026-A5");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "2.000000", "3.000000");

        CyclicBarrier barrera = new CyclicBarrier(2);
        Callable<Integer> alta = () -> {
            barrera.await();
            return given().contentType(JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 10))
                    .when()
                    .post("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                    .then()
                    .extract()
                    .statusCode();
        };

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> a = pool.submit(alta);
            Future<Integer> b = pool.submit(alta);
            List<Integer> estados = List.of(a.get(), b.get());
            assertTrue(estados.contains(201), "una transacción debe crear: " + estados);
            assertTrue(estados.contains(409), "la otra debe conflictuar: " + estados);
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, contarCronogramas(presupuestoId));
        assertEquals(2, contarActividades(presupuestoId));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Lectura
    // ──────────────────────────────────────────────────────────────────────

    /** GET sin cronograma → 404 {@code no-encontrado}; nunca 200 vacío. */
    @Test
    void get_sin_cronograma_devuelve_404() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p28-get404@ex.com");
        String proyectoId = crearProyecto(token, "P28 get 404", "P-2026-G1");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        assertEquals(0, contarCronogramas(presupuestoId));
    }

    /** GET configurado es read-only y repetible; el mapa conserva orden numérico. */
    @Test
    void get_configurado_devuelve_read_model_estable_y_no_muta() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p28-get200@ex.com");
        String proyectoId = crearProyecto(token, "P28 get 200", "P-2026-G2");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "5.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 12);

        String actividadId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .body("id", equalTo(cronogramaId))
                .extract()
                .path("actividades[0].id");

        // Mapa no consecutivo sembrado en desorden: la lectura lo devuelve por
        // orden numérico y no rellena la clave "2".
        sembrarAvance(actividadId, "{\"4\":\"1.2500\",\"3\":\"0.0000\",\"1\":\"2.5000\"}");

        String cuerpo = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .body("actividades[0].avancePorPeriodo.1", equalTo("2.5000"))
                .body("actividades[0].avancePorPeriodo.3", equalTo("0.0000"))
                .body("actividades[0].avancePorPeriodo.4", equalTo("1.2500"))
                .body("actividades[0].segmentos[0].inicio", equalTo(1))
                .body("actividades[0].segmentos[0].fin", equalTo(1))
                .body("actividades[0].segmentos[1].inicio", equalTo(3))
                .body("actividades[0].segmentos[1].fin", equalTo(4))
                .body("actividades[0].desviacion", equalTo("96.2500"))
                .body("avancePorPeriodo", hasSize(12))
                .body("avancePorPeriodo[0]", equalTo("2.5000"))
                .body("avancePorPeriodo[1]", equalTo("0.0000"))
                .body("avancePorPeriodo[2]", equalTo("0.0000"))
                .body("avancePorPeriodo[3]", equalTo("1.2500"))
                .body("avanceAcumulado[3]", equalTo("3.7500"))
                .body("avanceFinal", equalTo("3.7500"))
                .extract()
                .asString();
        assertTrue(
                cuerpo.indexOf("\"1\":\"2.5000\"") < cuerpo.indexOf("\"3\":\"0.0000\""),
                "el mapa se serializa por orden numérico: " + cuerpo);
        assertTrue(!cuerpo.contains("\"2\":"), "no se rellenan claves ausentes");

        assertEquals(1, contarCronogramas(presupuestoId));
        assertEquals(1, contarActividades(presupuestoId));
    }

    @Test
    void fingerprint_detecta_cambio_compensado_y_configurar_no_refresca_marcadores() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p28-stale@ex.com");
        String proyectoId = crearProyecto(token, "P28 stale", "P-2026-G3");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "4.000000", "6.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);

        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE rubro SET precio_total = CASE item "
                        + "WHEN '1.1' THEN 5.000000 WHEN '1.2' THEN 5.000000 END "
                        + "WHERE capitulo_id IN (SELECT id FROM capitulo WHERE presupuesto_id = ?)")) {
            ps.setLong(1, internalId("presupuesto", presupuestoId));
            ps.executeUpdate();
        }

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .body("totalGeneral", equalTo("10.000000"))
                .body("totalGeneralRevisado", equalTo("10.000000"))
                .body("desactualizado", equalTo(true));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 5))
                .when()
                .put("/api/v1/cronogramas/" + cronogramaId + "/configuracion")
                .then()
                .statusCode(200)
                .body("desactualizado", equalTo(true));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Validación de entrada (TC-P33-06, TC-P33-07)
    // ──────────────────────────────────────────────────────────────────────

    /** TC-P33-06 — límites canónicos: SEMANA 1..520, MES 1..120. */
    @Test
    void TC_P33_06_limites_de_periodos_por_unidad() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p28-limites@ex.com");
        String proyectoId = crearProyecto(token, "P28 limites", "P-2026-L1");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        for (Map<String, ?> invalido : List.of(
                Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 521),
                Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 0),
                Map.of("unidadTiempo", "MES", "numeroPeriodos", 121),
                Map.of("unidadTiempo", "MES", "numeroPeriodos", -3),
                Map.of("unidadTiempo", "DIA", "numeroPeriodos", 5),
                Map.of("unidadTiempo", "semana", "numeroPeriodos", 5),
                Map.of("numeroPeriodos", 5))) {
            given().contentType(JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(invalido)
                    .when()
                    .post("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                    .then()
                    .statusCode(400)
                    .body("codigo", equalTo("validacion"));
        }

        assertEquals(0, contarCronogramas(presupuestoId), "ninguna validación fallida deja filas");

        // Límites superiores válidos.
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 520))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(201)
                .body("numeroPeriodos", equalTo(520));
    }

    /** El body de alta no acepta actividades, pesos ni identidades del cliente. */
    @Test
    void alta_rechaza_propiedades_desconocidas() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p28-strict@ex.com");
        String proyectoId = crearProyecto(token, "P28 strict", "P-2026-L2");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "1.000000");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("unidadTiempo", "SEMANA");
        body.put("numeroPeriodos", 4);
        body.put("actividades", List.of(Map.of("rubroId", UUID_INEXISTENTE_V7, "pesoPonderado", "50.0000")));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(body)
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        assertEquals(0, contarCronogramas(presupuestoId));
    }

    /** TC-P33-07 — UUID no v7 → 400; inexistente/ajeno → 404 indistinguible. */
    @Test
    void TC_P33_07_uuid_invalido_400_y_ajeno_o_inexistente_404() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p28-owner@ex.com");
        String tokenAjeno = AuthSupport.registrarConToken(mailbox, "p28-ajeno@ex.com");
        String proyectoId = crearProyecto(token, "P28 owner", "P-2026-O1");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "1.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + UUID_NO_V7 + "/cronograma")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + UUID_INEXISTENTE_V7 + "/cronograma")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        given().header("Authorization", "Bearer " + tokenAjeno)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenAjeno)
                .body(Map.of("unidadTiempo", "MES", "numeroPeriodos", 3))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(404);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenAjeno)
                .body(Map.of("unidadTiempo", "MES", "numeroPeriodos", 3))
                .when()
                .put("/api/v1/cronogramas/" + cronogramaId + "/configuracion")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "MES", "numeroPeriodos", 3))
                .when()
                .put("/api/v1/cronogramas/" + UUID_NO_V7 + "/configuracion")
                .then()
                .statusCode(400);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "MES", "numeroPeriodos", 3))
                .when()
                .put("/api/v1/cronogramas/" + UUID_INEXISTENTE_V7 + "/configuracion")
                .then()
                .statusCode(404);

        // Anónimo no accede a ninguna de las tres rutas.
        given().when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(401);
        given().contentType(JSON)
                .body(Map.of("unidadTiempo", "MES", "numeroPeriodos", 3))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(401);
        given().contentType(JSON)
                .body(Map.of("unidadTiempo", "MES", "numeroPeriodos", 3))
                .when()
                .put("/api/v1/cronogramas/" + cronogramaId + "/configuracion")
                .then()
                .statusCode(401);

        assertEquals(1, contarCronogramas(presupuestoId), "ningún acceso ajeno mutó el agregado");
        assertEquals("SEMANA", unidadPersistida(cronogramaId));
    }

    @Test
    void super_admin_accede_a_get_post_put_solo_sobre_recurso_propio() throws Exception {
        String token = registrarSuperAdmin("p28-admin@ex.com");
        String proyectoId = crearProyecto(token, "P28 admin", "P-2026-SA");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "1.000000");

        String cronogramaId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 4))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .body("id", equalTo(cronogramaId));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 5))
                .when()
                .put("/api/v1/cronogramas/" + cronogramaId + "/configuracion")
                .then()
                .statusCode(200)
                .body("numeroPeriodos", equalTo(5));
    }

    private String unidadPersistida(String cronogramaPublicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT unidad_tiempo FROM cronograma WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(cronogramaPublicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private int periodosPersistidos(String cronogramaPublicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT numero_periodos FROM cronograma WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(cronogramaPublicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void esperarPutBloqueado() throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < limite) {
            try (Connection con = ds.getConnection();
                    PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM pg_stat_activity "
                            + "WHERE wait_event_type = 'Lock' "
                            + "AND query ILIKE '%select id from presupuesto where id%for update%'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        return;
                    }
                }
            }
            Thread.sleep(25);
        }
        throw new AssertionError("el PUT no llegó al lock del presupuesto");
    }

    // ──────────────────────────────────────────────────────────────────────
    // PUT configuración (TC-P33-03, TC-P33-04, TC-P33-05)
    // ──────────────────────────────────────────────────────────────────────

    /** El mismo body repetido es idempotente: misma respuesta, sin filas nuevas. */
    @Test
    void put_idempotente_con_el_mismo_body() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p28-idem@ex.com");
        String proyectoId = crearProyecto(token, "P28 idem", "P-2026-C1");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "1.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 12);

        for (int i = 0; i < 2; i++) {
            given().contentType(JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 12))
                    .when()
                    .put("/api/v1/cronogramas/" + cronogramaId + "/configuracion")
                    .then()
                    .statusCode(200)
                    .body("id", equalTo(cronogramaId))
                    .body("unidadTiempo", equalTo("SEMANA"))
                    .body("numeroPeriodos", equalTo(12));
        }

        assertEquals(1, contarCronogramas(presupuestoId));
        assertEquals(1, contarActividades(presupuestoId));
    }

    /** Ampliar períodos conserva el mapa y no fabrica claves nuevas. */
    @Test
    void put_ampliacion_conserva_mapa_existente() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p28-amplia@ex.com");
        String proyectoId = crearProyecto(token, "P28 amplia", "P-2026-C2");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "1.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 8);
        String actividadId = actividadDeCronograma(token, presupuestoId);
        sembrarAvance(actividadId, "{\"1\":\"10.0000\",\"3\":\"5.0000\"}");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 20))
                .when()
                .put("/api/v1/cronogramas/" + cronogramaId + "/configuracion")
                .then()
                .statusCode(200)
                .body("numeroPeriodos", equalTo(20))
                .body("actividades[0].avancePorPeriodo.1", equalTo("10.0000"))
                .body("actividades[0].avancePorPeriodo.3", equalTo("5.0000"))
                .body("actividades[0].avancePorPeriodo.size()", equalTo(2));
    }

    private String actividadDeCronograma(String token, String presupuestoId) {
        return given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades[0].id");
    }

    /** TC-P33-04 — reducción sin claves fuera del rango: 200 sin confirmación. */
    @Test
    void TC_P33_04_reduccion_segura_no_requiere_confirmacion() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p28-red-ok@ex.com");
        String proyectoId = crearProyecto(token, "P28 reduccion segura", "P-2026-C3");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "1.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 12);
        String actividadId = actividadDeCronograma(token, presupuestoId);
        sembrarAvance(actividadId, "{\"1\":\"10.0000\",\"5\":\"5.0000\"}");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 8))
                .when()
                .put("/api/v1/cronogramas/" + cronogramaId + "/configuracion")
                .then()
                .statusCode(200)
                .body("numeroPeriodos", equalTo(8))
                .body("actividades[0].avancePorPeriodo.1", equalTo("10.0000"))
                .body("actividades[0].avancePorPeriodo.5", equalTo("5.0000"));

        assertEquals(8, periodosPersistidos(cronogramaId));
    }

    /**
     * TC-P33-03 — reducción con claves fuera del nuevo rango: 409 determinista y
     * sin mutación; el reintento confirmado elimina solo las claves fuera de rango.
     */
    @Test
    void TC_P33_03_reduccion_peligrosa_exige_confirmacion_explicita() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p28-red-409@ex.com");
        String proyectoId = crearProyecto(token, "P28 reduccion peligrosa", "P-2026-C4");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "1.000000", "1.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 12);

        List<String> actividades = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades.id");
        sembrarAvance(actividades.get(0), "{\"1\":\"1.0000\",\"9\":\"2.0000\",\"12\":\"3.0000\"}");
        sembrarAvance(actividades.get(1), "{\"10\":\"4.0000\"}");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 8))
                .when()
                .put("/api/v1/cronogramas/" + cronogramaId + "/configuracion")
                .then()
                .statusCode(409)
                .body("codigo", equalTo("configuracion-cronograma-requiere-confirmacion"))
                .body("perdidas", hasSize(3))
                .body("perdidas.actividadId", contains(actividades.get(0), actividades.get(0), actividades.get(1)))
                .body("perdidas.periodo", contains(9, 12, 10))
                .body("perdidas.valor", contains("2.0000", "3.0000", "4.0000"));

        // Sin mutación: períodos y mapas intactos.
        assertEquals(12, periodosPersistidos(cronogramaId));
        assertTrue(avancePersistido(actividades.get(0)).contains("\"9\""), "el mapa del origen sigue completo");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 8, "confirmarPerdida", true))
                .when()
                .put("/api/v1/cronogramas/" + cronogramaId + "/configuracion")
                .then()
                .statusCode(200)
                .body("numeroPeriodos", equalTo(8))
                .body("actividades[0].avancePorPeriodo.1", equalTo("1.0000"))
                .body("actividades[0].avancePorPeriodo.size()", equalTo(1))
                .body("actividades[1].avancePorPeriodo.size()", equalTo(0));

        assertEquals(8, periodosPersistidos(cronogramaId));
    }

    /** TC-P33-05 — cambio de unidad con mapa no vacío exige la misma confirmación. */
    @Test
    void TC_P33_05_cambio_de_unidad_con_datos_exige_confirmacion() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p28-unidad@ex.com");
        String proyectoId = crearProyecto(token, "P28 unidad", "P-2026-C5");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "1.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 10);
        String actividadId = actividadDeCronograma(token, presupuestoId);
        sembrarAvance(actividadId, "{\"2\":\"7.0000\"}");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "MES", "numeroPeriodos", 10))
                .when()
                .put("/api/v1/cronogramas/" + cronogramaId + "/configuracion")
                .then()
                .statusCode(409)
                .body("codigo", equalTo("configuracion-cronograma-requiere-confirmacion"))
                .body("perdidas", hasSize(1))
                .body("perdidas[0].actividadId", equalTo(actividadId))
                .body("perdidas[0].periodo", equalTo(2))
                .body("perdidas[0].valor", equalTo("7.0000"));

        assertEquals("SEMANA", unidadPersistida(cronogramaId));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "MES", "numeroPeriodos", 10, "confirmarPerdida", true))
                .when()
                .put("/api/v1/cronogramas/" + cronogramaId + "/configuracion")
                .then()
                .statusCode(200)
                .body("unidadTiempo", equalTo("MES"))
                .body("actividades[0].avancePorPeriodo.2", equalTo("7.0000"));

        assertEquals("MES", unidadPersistida(cronogramaId));
    }

    @Test
    void cambio_de_unidad_y_reduccion_enumera_union_determinista_sin_duplicados() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p28-union@ex.com");
        String proyectoId = crearProyecto(token, "P28 union", "P-2026-C7");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "1.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 10);
        String actividadId = actividadDeCronograma(token, presupuestoId);
        sembrarAvance(actividadId, "{\"1\":\"0.0000\",\"4\":\"2.0000\",\"9\":\"3.0000\"}");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "MES", "numeroPeriodos", 4))
                .when()
                .put("/api/v1/cronogramas/" + cronogramaId + "/configuracion")
                .then()
                .statusCode(409)
                .body("perdidas", hasSize(3))
                .body("perdidas.periodo", contains(1, 4, 9))
                .body("perdidas.valor", contains("0.0000", "2.0000", "3.0000"));
    }

    @Test
    void put_que_espera_lock_refresca_cronograma_antes_de_validar() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p28-fresh@ex.com");
        String proyectoId = crearProyecto(token, "P28 freshness", "P-2026-C8");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "1.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 10);
        String actividadId = actividadDeCronograma(token, presupuestoId);
        sembrarAvance(actividadId, "{\"2\":\"7.0000\"}");

        Long presupuestoInterno = internalId("presupuesto", presupuestoId);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection bloqueador = ds.getConnection()) {
            bloqueador.setAutoCommit(false);
            try (PreparedStatement lock =
                    bloqueador.prepareStatement("SELECT id FROM presupuesto WHERE id = ? FOR UPDATE")) {
                lock.setLong(1, presupuestoInterno);
                lock.executeQuery().close();
            }

            Future<Response> esperando = pool.submit(() -> given().contentType(JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 10))
                    .when()
                    .put("/api/v1/cronogramas/" + cronogramaId + "/configuracion"));

            esperarPutBloqueado();
            try (PreparedStatement cambio =
                    bloqueador.prepareStatement("UPDATE cronograma SET unidad_tiempo = 'MES' WHERE public_id = ?")) {
                cambio.setObject(1, UUID.fromString(cronogramaId));
                cambio.executeUpdate();
            }
            bloqueador.commit();

            esperando
                    .get(10, TimeUnit.SECONDS)
                    .then()
                    .statusCode(409)
                    .body("codigo", equalTo("configuracion-cronograma-requiere-confirmacion"))
                    .body("perdidas", hasSize(1))
                    .body("perdidas[0].periodo", equalTo(2));
        } finally {
            pool.shutdownNow();
        }
        assertEquals("MES", unidadPersistida(cronogramaId));
    }

    /** La configuración inválida no muta el agregado (atomicidad de validación). */
    @Test
    void put_invalido_no_muta_configuracion() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p28-put400@ex.com");
        String proyectoId = crearProyecto(token, "P28 put 400", "P-2026-C6");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "1.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 12);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "MES", "numeroPeriodos", 121))
                .when()
                .put("/api/v1/cronogramas/" + cronogramaId + "/configuracion")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        assertEquals("SEMANA", unidadPersistida(cronogramaId));
        assertEquals(12, periodosPersistidos(cronogramaId));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Copia de versión (TC-P33-09)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * TC-P33-09 — copiar una versión con cronograma no vuelve a autoimportar:
     * la copia tiene el mismo número de actividades, identidades nuevas y
     * configuración/mapa propios; el origen no cambia al reconfigurar la copia.
     */
    @Test
    void TC_P33_09_copia_de_version_no_duplica_actividades() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p28-copy@ex.com");
        String proyectoId = crearProyecto(token, "P28 copy", "P-2026-K1");
        String origenId = vigenteDeProyecto(proyectoId);
        sembrarRubros(origenId, "2.000000", "3.000000");
        String cronogramaOrigen = crearCronograma(token, origenId, "SEMANA", 10);
        List<String> actividadesOrigen = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + origenId + "/cronograma")
                .then()
                .extract()
                .path("actividades.id");
        sembrarAvance(actividadesOrigen.get(0), "{\"1\":\"5.0000\",\"4\":\"5.0000\"}");

        String copiaId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("origenId", origenId))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/presupuestos")
                .then()
                .statusCode(201)
                .extract()
                .path("presupuestoId");

        assertEquals(1, contarCronogramas(copiaId), "la copia recibe exactamente un cronograma");
        assertEquals(2, contarActividades(copiaId), "la copia no duplica actividades (sin autoimport)");

        String cronogramaCopia = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + copiaId + "/cronograma")
                .then()
                .statusCode(200)
                .body("unidadTiempo", equalTo("SEMANA"))
                .body("numeroPeriodos", equalTo(10))
                .body("actividades", hasSize(2))
                .body("actividades[0].avancePorPeriodo.1", equalTo("5.0000"))
                .body("actividades[0].avancePorPeriodo.4", equalTo("5.0000"))
                .extract()
                .path("id");
        assertNotNull(cronogramaCopia);
        assertNotEquals(cronogramaOrigen, cronogramaCopia, "identidad pública fresca en la copia");

        // Reconfigurar la copia no altera el origen.
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 20))
                .when()
                .put("/api/v1/cronogramas/" + cronogramaCopia + "/configuracion")
                .then()
                .statusCode(200);

        assertEquals(10, periodosPersistidos(cronogramaOrigen), "el origen permanece intacto");
        assertEquals(20, periodosPersistidos(cronogramaCopia));
    }
}
