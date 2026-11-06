package ec.uce.propuestas.cronograma;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Plan 029 (P-34) — IT de los cuatro comandos PATCH congelados por Plan 026:
 * <ul>
 *   <li>{@code REEMPLAZAR_AVANCES} — body con mapa completo, validado
 *       estrictamente (sin claves duplicadas, fuera de rango, etc.).</li>
 *   <li>{@code DISTRIBUIR_UNIFORME} — reparte el peso entre los períodos
 *       listados, escala 4, residual determinista.</li>
 *   <li>{@code MOVER_SEGMENTO} — desplaza un segmento máximo actual,
 *       conserva valores, 400 fuera de rango, 409 en solapamiento.</li>
 *   <li>{@code REDIMENSIONAR_SEGMENTO} — conserva la suma del segmento
 *       (no del peso total), redistribuye uniforme con residual.</li>
 * </ul>
 *
 * <p>Cubre además:
 * owner-to-404, UUID malformado, UUIDv4 (no v7), cross-cronograma,
 * concurrencia de dos PATCH con la misma actividad, rollback después del
 * lock, copia de versión preservando cronograma/actividades. Las
 * sincronizaciones centrales (alta/baja de rubro/cantidad/APU/insumo)
 * se prueban en {@code RubroResourceIT} y {@code RecalculoServiceIT}.</p>
 */
@QuarkusTest
class ActividadProgramarResourceIT {

    private static final String UUID_V7 =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

    private static final String UUID_NO_V7 = "550e8400-e29b-41d4-a716-446655440000";
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
            st.execute("TRUNCATE TABLE log_actividad, cronograma, actividad, apu_detalle, apu_seccion, apu, "
                    + "rubro, capitulo, presupuesto, insumo, base_insumos, "
                    + "parametros_proyecto, firmante, proyecto, token_usuario, refresh_token, usuario "
                    + "RESTART IDENTITY CASCADE");
        }
    }

    private long contarEventoCronograma(String operacion) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT count(*) FROM log_actividad WHERE evento = 'cronograma.editado' "
                                + "AND detalle->>'operacion' = ?")) {
            ps.setString(1, operacion);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers de fixture (idénticos a los de CronogramaResourceIT)
    // ──────────────────────────────────────────────────────────────────────

    private String crearProyecto(String token, String nombre) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto",
                        nombre,
                        "codigo",
                        "P-2026-Z" + Math.abs(nombre.hashCode() % 10000),
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

    private void sembrarAvance(String actividadPublicId, String jsonb) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE actividad SET avance_por_periodo = ?::jsonb WHERE public_id = ?")) {
            ps.setString(1, jsonb);
            ps.setObject(2, UUID.fromString(actividadPublicId));
            ps.executeUpdate();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // REEMPLAZAR_AVANCES
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void reemplazar_avances_mapa_completo_se_persiste_y_se_devuelve_en_lectura() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-reemp@ex.com");
        String proyectoId = crearProyecto(token, "P29 reemp");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "6.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 8);
        String actividadId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades[0].id");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("operacion", "REEMPLAZAR_AVANCES");
        Map<String, String> mapa = new LinkedHashMap<>();
        mapa.put("1", "30.0000");
        mapa.put("3", "20.0000");
        mapa.put("5", "50.0000");
        body.put("avancePorPeriodo", mapa);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(body)
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(200)
                .body("actividades[0].avancePorPeriodo.1", equalTo("30.0000"))
                .body("actividades[0].avancePorPeriodo.3", equalTo("20.0000"))
                .body("actividades[0].avancePorPeriodo.5", equalTo("50.0000"));

        assertEquals(
                "{\"1\": \"30.0000\", \"3\": \"20.0000\", \"5\": \"50.0000\"}".replace(" ", ""),
                avancePersistido(actividadId).replace(" ", ""));
        assertEquals(1L, contarEventoCronograma("programar.reemplazar_avances"));
    }

    @Test
    void reemplazar_avances_mapa_vacio_es_borrador_valido() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-empty@ex.com");
        String proyectoId = crearProyecto(token, "P29 empty");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "6.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);
        String actividadId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades[0].id");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("operacion", "REEMPLAZAR_AVANCES", "avancePorPeriodo", Map.of()))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(200)
                .body("estadoDistribucion", equalTo("BORRADOR"))
                .body("actividades[0].avancePorPeriodo.size()", equalTo(0));
    }

    @Test
    void reemplazar_avances_rechaza_claves_duplicadas_y_fuera_de_rango_y_negativas() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-bad@ex.com");
        String proyectoId = crearProyecto(token, "P29 bad");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "6.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 6);
        String actividadId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades[0].id");

        String jsonAntes = avancePersistido(actividadId);
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("operacion", "REEMPLAZAR_AVANCES", "avancePorPeriodo", Map.of("01", "1.0", "1", "1.0")))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
        // Sin mutación
        assertEquals(jsonAntes, avancePersistido(actividadId));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("operacion", "REEMPLAZAR_AVANCES", "avancePorPeriodo", Map.of("7", "1.0")))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(400);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("operacion", "REEMPLAZAR_AVANCES", "avancePorPeriodo", Map.of("1", "-1.0")))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(400);
    }

    // ──────────────────────────────────────────────────────────────────────
    // DISTRIBUIR_UNIFORME
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void distribuir_uniforme_con_residuo_escala_4_exacto() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-uni@ex.com");
        String proyectoId = crearProyecto(token, "P29 uni");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "1.000000", "1.000000", "4.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "MES", 6);
        String actividadId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades[0].id");
        // La actividad 1.1 tiene peso 16.6667 (1/6 × 100). La base HALF_UP es
        // 5.5556 y el residual firmado -0.0001 se aplica al último período:
        // 5.5556,5.5556,5.5555. Σ exacta = 16.6667.
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("operacion", "DISTRIBUIR_UNIFORME", "periodos", List.of(1, 3, 5)))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(200)
                .body("actividades[0].avancePorPeriodo.size()", equalTo(3))
                .body("actividades[0].avancePorPeriodo.1", equalTo("5.5556"))
                .body("actividades[0].avancePorPeriodo.3", equalTo("5.5556"))
                .body("actividades[0].avancePorPeriodo.5", equalTo("5.5555"));
        assertEquals(1L, contarEventoCronograma("programar.distribuir_uniforme"));
    }

    // ──────────────────────────────────────────────────────────────────────
    // MOVER_SEGMENTO
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void mover_segmento_fuera_de_rango_devuelve_400_y_sin_mutacion() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-mov-400@ex.com");
        String proyectoId = crearProyecto(token, "P29 mov 400");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "6.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 8);
        String actividadId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades[0].id");
        sembrarAvance(actividadId, "{\"1\":\"6.0000\"}");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("operacion", "MOVER_SEGMENTO", "inicio", 1, "fin", 1, "delta", 9))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(400);
        // Sin mutación
        assertTrue(avancePersistido(actividadId).contains("\"1\":"));
    }

    @Test
    void mover_segmento_con_solapamiento_devuelve_409() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-mov-409@ex.com");
        String proyectoId = crearProyecto(token, "P29 mov 409");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "6.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 10);
        String actividadId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades[0].id");
        sembrarAvance(actividadId, "{\"1\":\"3.0000\",\"2\":\"3.0000\",\"6\":\"1.0000\"}");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("operacion", "MOVER_SEGMENTO", "inicio", 1, "fin", 2, "delta", 5))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(409)
                .body("codigo", equalTo("segmento-solapado"));
        // Sin mutación
        assertTrue(avancePersistido(actividadId).contains("\"6\":"));
    }

    @Test
    void mover_segmento_valido_conserva_valores_y_no_fabrica_claves() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-mov-ok@ex.com");
        String proyectoId = crearProyecto(token, "P29 mov ok");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "6.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 12);
        String actividadId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades[0].id");
        sembrarAvance(actividadId, "{\"3\":\"1.5000\",\"4\":\"2.5000\"}");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("operacion", "MOVER_SEGMENTO", "inicio", 3, "fin", 4, "delta", 4))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(200)
                .body("actividades[0].avancePorPeriodo.7", equalTo("1.5000"))
                .body("actividades[0].avancePorPeriodo.8", equalTo("2.5000"))
                .body("actividades[0].avancePorPeriodo.3", is((String) null))
                .body("actividades[0].avancePorPeriodo.4", is((String) null));
        assertEquals(1L, contarEventoCronograma("programar.mover_segmento"));
    }

    // ──────────────────────────────────────────────────────────────────────
    // REDIMENSIONAR_SEGMENTO
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void redimensionar_conserva_suma_del_segmento_no_peso_total() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-red@ex.com");
        String proyectoId = crearProyecto(token, "P29 red");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "10.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "MES", 6);
        String actividadId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades[0].id");
        sembrarAvance(actividadId, "{\"1\":\"1.0000\"}");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "operacion", "REDIMENSIONAR_SEGMENTO", "inicio", 1, "fin", 1, "nuevoInicio", 3, "nuevoFin", 5))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(200)
                .body("actividades[0].avancePorPeriodo.3", equalTo("0.3333"))
                .body("actividades[0].avancePorPeriodo.4", equalTo("0.3333"))
                .body("actividades[0].avancePorPeriodo.5", equalTo("0.3334"));
        assertEquals(1L, contarEventoCronograma("programar.redimensionar_segmento"));
    }

    @Test
    void redimensionar_solapamiento_devuelve_409() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-red-409@ex.com");
        String proyectoId = crearProyecto(token, "P29 red 409");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "6.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 12);
        String actividadId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades[0].id");
        sembrarAvance(actividadId, "{\"1\":\"1.0000\",\"8\":\"2.0000\"}");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "operacion", "REDIMENSIONAR_SEGMENTO", "inicio", 1, "fin", 1, "nuevoInicio", 7, "nuevoFin", 9))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(409)
                .body("codigo", equalTo("segmento-solapado"));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Owner-scope, UUID y concurrencia
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void patch_actividad_de_otro_cronograma_devuelve_404() throws Exception {
        String tokenA = AuthSupport.registrarConToken(mailbox, "p29-cronoA@ex.com");
        String tokenB = AuthSupport.registrarConToken(mailbox, "p29-cronoB@ex.com");

        String proyectoA = crearProyecto(tokenA, "P29 A");
        String presupuestoA = vigenteDeProyecto(proyectoA);
        sembrarRubros(presupuestoA, "1.000000", "2.000000");
        String cronogramaA = crearCronograma(tokenA, presupuestoA, "SEMANA", 4);

        // El cronograma B referencia a OTRO rubro; el PATCH usa el cronograma/actividad de A
        // con una actividad del cronograma B → 404.
        String proyectoB = crearProyecto(tokenB, "P29 B");
        String presupuestoB = vigenteDeProyecto(proyectoB);
        sembrarRubros(presupuestoB, "1.000000");
        String cronogramaB = crearCronograma(tokenB, presupuestoB, "SEMANA", 4);
        String actividadB = given().header("Authorization", "Bearer " + tokenB)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoB + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades[0].id");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenA)
                .body(Map.of("operacion", "REEMPLAZAR_AVANCES", "avancePorPeriodo", Map.of("1", "1.0000")))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaA + "/actividades/" + actividadB)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    @Test
    void patch_uuid_invalido_uuid_v4_y_ajeno() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-uuid@ex.com");
        String tokenAjeno = AuthSupport.registrarConToken(mailbox, "p29-uuid-otro@ex.com");
        String proyectoId = crearProyecto(token, "P29 uuid");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "1.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);
        String actividadId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades[0].id");

        // UUID del path malformado → 400
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("operacion", "REEMPLAZAR_AVANCES", "avancePorPeriodo", Map.of()))
                .when()
                .patch("/api/v1/cronogramas/not-a-uuid/actividades/" + actividadId)
                .then()
                .statusCode(400);

        // UUIDv4 (no v7) → 400
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("operacion", "REEMPLAZAR_AVANCES", "avancePorPeriodo", Map.of()))
                .when()
                .patch("/api/v1/cronogramas/" + UUID_NO_V7 + "/actividades/" + actividadId)
                .then()
                .statusCode(400);

        // UUIDv7 bien formado pero inexistente → 404
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("operacion", "REEMPLAZAR_AVANCES", "avancePorPeriodo", Map.of()))
                .when()
                .patch("/api/v1/cronogramas/" + UUID_INEXISTENTE_V7 + "/actividades/" + actividadId)
                .then()
                .statusCode(404);

        // Actividad ajena → 404
        given().contentType(JSON)
                .header("Authorization", "Bearer " + tokenAjeno)
                .body(Map.of("operacion", "REEMPLAZAR_AVANCES", "avancePorPeriodo", Map.of()))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(404);

        // Anónimo → 401
        given().contentType(JSON)
                .body(Map.of("operacion", "REEMPLAZAR_AVANCES", "avancePorPeriodo", Map.of()))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(401);
    }

    @Test
    void dos_patch_concurrentes_a_la_misma_actividad_serializan() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-conc@ex.com");
        String proyectoId = crearProyecto(token, "P29 conc");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "1.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);
        String actividadId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades[0].id");

        CyclicBarrier barrera = new CyclicBarrier(2);
        Callable<Response> reemplazar = () -> {
            barrera.await();
            return given().contentType(JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of(
                            "operacion",
                            "REEMPLAZAR_AVANCES",
                            "avancePorPeriodo",
                            Map.of("1", "50.0000", "2", "50.0000")))
                    .when()
                    .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId);
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Response> a = pool.submit(reemplazar);
            Future<Response> b = pool.submit(reemplazar);
            int sa = a.get(10, TimeUnit.SECONDS).statusCode();
            int sb = b.get(10, TimeUnit.SECONDS).statusCode();
            assertEquals(200, sa);
            assertEquals(200, sb);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void patch_que_espera_lock_revalida_reduccion_y_preserva_mapa() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-fresh@ex.com");
        String proyectoId = crearProyecto(token, "P29 fresh");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "1.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 6);
        String actividadId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades[0].id");
        sembrarAvance(actividadId, "{\"1\":\"1.0000\"}");

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
                    .body(Map.of("operacion", "REEMPLAZAR_AVANCES", "avancePorPeriodo", Map.of("6", "2.0000")))
                    .when()
                    .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId));

            esperarPutBloqueado();
            try (PreparedStatement cambio =
                    bloqueador.prepareStatement("UPDATE cronograma SET numero_periodos = 4 WHERE public_id = ?")) {
                cambio.setObject(1, UUID.fromString(cronogramaId));
                cambio.executeUpdate();
            }
            bloqueador.commit();

            esperando.get(10, TimeUnit.SECONDS).then().statusCode(400);
        } finally {
            pool.shutdownNow();
        }
        assertTrue(avancePersistido(actividadId).contains("\"1\":"));
        org.junit.jupiter.api.Assertions.assertFalse(
                avancePersistido(actividadId).contains("\"6\":"));
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
        throw new AssertionError("el PATCH no llegó al lock del presupuesto");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Rollback
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void patch_invalido_no_muta_la_actividad() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-rb@ex.com");
        String proyectoId = crearProyecto(token, "P29 rb");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "1.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);
        String actividadId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades[0].id");
        sembrarAvance(actividadId, "{\"1\":\"1.0000\"}");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("operacion", "MOVER_SEGMENTO", "inicio", 1, "fin", 1, "delta", 99))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(400);

        // El mapa original {"1":"1.0000"} sigue intacto
        assertTrue(avancePersistido(actividadId).contains("\"1\""));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Casos negativos de borde adicional: clave cero activa
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void clave_con_valor_cero_es_activa_y_participa_en_redimension() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-cero@ex.com");
        String proyectoId = crearProyecto(token, "P29 cero");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "10.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "MES", 6);
        String actividadId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades[0].id");
        sembrarAvance(actividadId, "{\"3\":\"0.0000\",\"4\":\"1.0000\"}");

        // Mover [3,4] (incluye el 0.0000 como activo) +1 → 4,5
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("operacion", "MOVER_SEGMENTO", "inicio", 3, "fin", 4, "delta", 1))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(200)
                .body("actividades[0].avancePorPeriodo.4", equalTo("0.0000"))
                .body("actividades[0].avancePorPeriodo.5", equalTo("1.0000"))
                .body("actividades[0].segmentos[0].inicio", equalTo(4))
                .body("actividades[0].segmentos[0].fin", equalTo(5));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Respuesta canónica: GET tras PATCH
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void get_tras_patch_devuelve_mapa_y_segmentos_actualizados() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-get@ex.com");
        String proyectoId = crearProyecto(token, "P29 get");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "6.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 8);
        String actividadId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades[0].id");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "operacion",
                        "REEMPLAZAR_AVANCES",
                        "avancePorPeriodo",
                        Map.of("2", "1.0", "4", "3.5", "5", "1.5")))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(200)
                .body("actividades[0].avancePorPeriodo.2", equalTo("1.0000"))
                .body("actividades[0].avancePorPeriodo.4", equalTo("3.5000"))
                .body("actividades[0].avancePorPeriodo.5", equalTo("1.5000"));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .body("actividades[0].avancePorPeriodo.size()", equalTo(3))
                .body("actividades[0].segmentos", hasSize(2));
    }

    @Test
    void patch_no_acepta_otros_roles_y_anonimo() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-roles@ex.com");
        String proyectoId = crearProyecto(token, "P29 roles");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "1.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);
        String actividadId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades[0].id");

        given().when()
                .header("Content-Type", "application/json")
                .body(Map.of("operacion", "REEMPLAZAR_AVANCES", "avancePorPeriodo", Map.of()))
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(401);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("operacion", "REEMPLAZAR_AVANCES", "avancePorPeriodo", Map.of("1", "1.0")))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(200);
    }

    @Test
    void patch_con_operacion_no_reconocida_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-nop@ex.com");
        String proyectoId = crearProyecto(token, "P29 nop");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "6.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);
        String actividadId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades[0].id");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("operacion", "BOGUS_OPERACION", "avancePorPeriodo", Map.of()))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void cronograma_y_actividad_publicas_son_uuidv7() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p29-v7@ex.com");
        String proyectoId = crearProyecto(token, "P29 v7");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        sembrarRubros(presupuestoId, "1.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);
        String actividadId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades[0].id");
        assertNotNull(cronogramaId);
        assertNotNull(actividadId);
        org.junit.jupiter.api.Assertions.assertTrue(
                cronogramaId.matches(UUID_V7), "cronogramaId debe ser UUIDv7: " + cronogramaId);
        org.junit.jupiter.api.Assertions.assertTrue(
                actividadId.matches(UUID_V7), "actividadId debe ser UUIDv7: " + actividadId);
    }
}
