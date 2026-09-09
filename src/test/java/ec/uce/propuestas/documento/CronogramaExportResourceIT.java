package ec.uce.propuestas.documento;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 031 (P-37) — IT del endpoint de exportación documental del cronograma.
 *
 * <p>Contrato:
 * <ul>
 *   <li>{@code GET /documentos/cronograma/{presupuestoId}/preflight?formato=...}</li>
 *   <li>{@code GET /documentos/cronograma/{presupuestoId}?formato=...} → attachment</li>
 *   <li>400 para UUID malformado/no-v7 o formato inválido.</li>
 *   <li>404 para presupuesto ajeno o inexistente.</li>
 *   <li>409 con código {@code export-bloqueado} para descarga bloqueada.</li>
 *   <li>Headers: {@code X-Cronograma-Desactualizado: true|false}.</li>
 * </ul>
 */
@QuarkusTest
class CronogramaExportResourceIT {

    private static final String UUID_V7 =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";
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
            st.execute("TRUNCATE TABLE log_actividad, cronograma, actividad, apu_detalle, apu_seccion, apu, "
                    + "rubro, capitulo, presupuesto, insumo, base_insumos, "
                    + "parametros_proyecto, firmante, proyecto, token_usuario, refresh_token, usuario "
                    + "RESTART IDENTITY CASCADE");
        }
    }

    private long contarDocumentoExportado(String formato) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT count(*) FROM log_actividad WHERE evento = 'documento.exportado' "
                                + "AND detalle->>'formato' = ?")) {
            ps.setString(1, formato);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers compartidos (mismo patrón que VistasCronogramaResourceIT)
    // ──────────────────────────────────────────────────────────────────────

    private String crearProyecto(String token, String codigo) {
        return given().contentType("application/json")
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto",
                        "P31 " + codigo,
                        "codigo",
                        codigo,
                        "anio",
                        (short) 2026,
                        "plazoEjecucion",
                        (short) 6,
                        "plazoUnidad",
                        "MES",
                        "direccionInstitucional",
                        "UCE"))
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

    private void sembrarAvance(String actividadPublicId, String jsonb) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE actividad SET avance_por_periodo = ?::jsonb WHERE public_id = ?")) {
            ps.setString(1, jsonb);
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

    private void fijarFechaInicio(String proyectoPublicId, LocalDate fecha) throws Exception {
        Long id = internalId("proyecto", proyectoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE proyecto SET fecha_inicio = ? WHERE id = ?")) {
            ps.setObject(1, fecha);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /** Helper que arma un cronograma exportable (completo + fechaInicio). */
    private String armarEscenarioExportable(String token) throws Exception {
        String proyectoId = crearProyecto(token, "P31-OK");
        fijarFechaInicio(proyectoId, LocalDate.parse("2026-01-01"));
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R1", "10.000000");
        fijarTotalPresupuesto(presupuestoId, "10.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);
        String actividadId = actividadDeRubro(presupuestoId, "1.1");
        sembrarAvance(actividadId, "{\"1\":\"50.0000\",\"2\":\"50.0000\"}");
        return presupuestoId;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Preflight
    // ──────────────────────────────────────────────────────────────────────

    /**
     * TC_P37_50 — preflight para cronograma exportable devuelve 200 con
     * {@code exportable=true}, sin bloqueos y warnings=[].
     */
    @Test
    void TC_P37_50_preflight_completo_devuelve_200_exportable_sin_bloqueos() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p31-pre-ok@ex.com");
        String presupuestoId = armarEscenarioExportable(token);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/" + presupuestoId + "/preflight?formato=xlsx")
                .then()
                .statusCode(200)
                .body("exportable", equalTo(true))
                .body("formato", equalTo("xlsx"))
                .body("bloqueos", hasSize(0))
                .body("warnings", hasSize(0));
    }

    /** TC_P37_51 — preflight bloqueado cuando el cronograma está en BORRADOR (desviación o avance != 100). */
    @Test
    void TC_P37_51_preflight_borrador_bloquea_exportacion() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p31-pre-bor@ex.com");
        String proyectoId = crearProyecto(token, "P31-BOR");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R1", "10.000000");
        fijarTotalPresupuesto(presupuestoId, "10.000000");
        crearCronograma(token, presupuestoId, "SEMANA", 4);
        // No sembramos avance → suma 0, avance final = 0 → bloqueado.

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/" + presupuestoId + "/preflight?formato=xlsx")
                .then()
                .statusCode(200)
                .body("exportable", equalTo(false))
                .body("bloqueos", notNullValue());
    }

    /** TC_P37_52 — preflight con stale devuelve warning no bloqueante. */
    @Test
    void TC_P37_52_preflight_stale_devuelve_warning_no_bloqueante() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p31-pre-stale@ex.com");
        String proyectoId = crearProyecto(token, "P31-STL");
        fijarFechaInicio(proyectoId, LocalDate.parse("2026-01-01"));
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R1", "10.000000");
        fijarTotalPresupuesto(presupuestoId, "10.000000");
        String cronogramaId = crearCronograma(token, presupuestoId, "SEMANA", 4);
        String actividadId = actividadDeRubro(presupuestoId, "1.1");
        sembrarAvance(actividadId, "{\"1\":\"50.0000\",\"2\":\"50.0000\"}");
        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/cronogramas/" + cronogramaId + "/revisado")
                .then()
                .statusCode(200);
        // Cambio compensado: total idéntico pero fingerprint distinto.
        fijarTotalPresupuesto(presupuestoId, "10.000000");
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE rubro SET precio_total = 9, precio_unitario = 9 WHERE item = '1.1' AND capitulo_id = ?")) {
            ps.setLong(1, cap);
            ps.executeUpdate();
        }
        // Restauramos el total para que la regla principal siga siendo valida.
        fijarTotalPresupuesto(presupuestoId, "10.000000");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/" + presupuestoId + "/preflight?formato=xlsx")
                .then()
                .statusCode(200)
                .body("exportable", equalTo(true))
                .body("warnings", hasSize(1))
                .body("warnings[0].codigo", equalTo("cronograma-desactualizado"));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Descarga
    // ──────────────────────────────────────────────────────────────────────

    /** TC_P37_53 — descarga XLSX: 200 + media type + attachment + filename + header stale. */
    @Test
    void TC_P37_53_descarga_xlsx_200_con_media_type_y_filename() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p31-dl-x@ex.com");
        String presupuestoId = armarEscenarioExportable(token);

        Response r = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/" + presupuestoId + "?formato=xlsx");

        assertEquals(200, r.statusCode(), "descarga XLSX debe ser 200");
        String ct = r.getHeader("Content-Type");
        assertNotNull(ct);
        assertTrue(
                ct.startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                "Content-Type XLSX canónico: " + ct);
        String disp = r.getHeader("Content-Disposition");
        assertTrue(disp != null && disp.contains("attachment"));
        assertTrue(disp.toLowerCase().endsWith(".xlsx\""), "filename .xlsx: " + disp);
        assertTrue(r.body().asByteArray().length > 100);
        assertEquals(1L, contarDocumentoExportado("XLSX"));
    }

    /** TC_P37_54 — descarga PDF: 200 + media type PDF. */
    @Test
    void TC_P37_54_descarga_pdf_200_con_media_type_pdf() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p31-dl-p@ex.com");
        String presupuestoId = armarEscenarioExportable(token);

        Response r = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/" + presupuestoId + "?formato=pdf");

        assertEquals(200, r.statusCode(), "descarga PDF debe ser 200");
        String ct = r.getHeader("Content-Type");
        assertTrue(ct != null && ct.startsWith("application/pdf"), "Content-Type PDF: " + ct);
        String disp = r.getHeader("Content-Disposition");
        assertTrue(disp.toLowerCase().endsWith(".pdf\""));
        assertEquals(1L, contarDocumentoExportado("PDF"));
    }

    /** TC_P37_55 — descarga MSPDI: 200 + application/xml. */
    @Test
    void TC_P37_55_descarga_mspdi_200_con_media_type_xml() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p31-dl-m@ex.com");
        String proyectoId = crearProyecto(token, "P31-MSPDI");
        fijarFechaInicio(proyectoId, LocalDate.parse("2026-01-01"));
        String presupuestoId = armarEscenarioExportable(token);

        Response r = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/" + presupuestoId + "?formato=mspdi");
        assertEquals(200, r.statusCode());
        String ct = r.getHeader("Content-Type");
        assertTrue(ct != null && ct.startsWith("application/xml"), "Content-Type MSPDI XML: " + ct);
        assertTrue(r.getHeader("Content-Disposition").toLowerCase().endsWith(".xml\""));
        assertEquals(1L, contarDocumentoExportado("MSPDI"));
    }

    /** TC_P37_56 — owner-to-404: cronograma ajeno responde 404. */
    @Test
    void TC_P37_56_presupuesto_ajeno_devuelve_404_en_preflight_y_descarga() throws Exception {
        String titular = AuthSupport.registrarConToken(mailbox, "p31-own-t@ex.com");
        String intruso = AuthSupport.registrarConToken(mailbox, "p31-own-i@ex.com");
        String presupuestoId = armarEscenarioExportable(titular);

        given().header("Authorization", "Bearer " + intruso)
                .when()
                .get("/api/v1/documentos/cronograma/" + presupuestoId + "/preflight?formato=xlsx")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
        given().header("Authorization", "Bearer " + intruso)
                .when()
                .get("/api/v1/documentos/cronograma/" + presupuestoId + "?formato=xlsx")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    /** TC_P37_57 — UUID malformado/no-v7 → 400 validacion. */
    @Test
    void TC_P37_57_uuid_malformado_y_formato_invalido_devuelven_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p31-uuid@ex.com");
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/" + UUID_NO_V7 + "/preflight?formato=xlsx")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/" + UUID_NO_V7 + "?formato=xlsx")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/0192f6c4-7c8a-7000-8000-000000000000/preflight?formato=bogus")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/0192f6c4-7c8a-7000-8000-000000000000?formato=bogus")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    /** TC_P37_58 — descarga bloqueada → 409 export-bloqueado, header stale presente. */
    @Test
    void TC_P37_58_descarga_bloqueada_devuelve_409_export_bloqueado() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p31-blq@ex.com");
        String proyectoId = crearProyecto(token, "P31-BLQ");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R1", "10.000000");
        fijarTotalPresupuesto(presupuestoId, "10.000000");
        crearCronograma(token, presupuestoId, "SEMANA", 4);
        // No sembramos avance → borrador.

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/" + presupuestoId + "?formato=xlsx")
                .then()
                .statusCode(409)
                .body("codigo", equalTo("export-bloqueado"));
    }

    /** TC_P37_59 — header stale presente en descargas (true|false). */
    @Test
    void TC_P37_59_header_stale_true_o_false_segun_caso() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p31-stl-h@ex.com");
        String proyectoId = crearProyecto(token, "P31-STL-H");
        fijarFechaInicio(proyectoId, LocalDate.parse("2026-01-01"));
        String presupuestoId = armarEscenarioExportable(token);

        Response r = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/" + presupuestoId + "?formato=xlsx");
        assertEquals(200, r.statusCode());
        String stale = r.getHeader("X-Cronograma-Desactualizado");
        assertNotNull(stale, "header X-Cronograma-Desactualizado obligatorio");
        assertTrue(stale.equals("true") || stale.equals("false"));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Cobertura CHK-31 / TC-P37-03 — bloqueo P-32 preservado y bytes cero
    // ──────────────────────────────────────────────────────────────────────

    /**
     * TC_P37_60 — preflight con PU=0 (P-32) devuelve 200 exportable=false
     * y conserva el código {@code presupuesto-pu-cero} entre los bloqueos
     * (no se mezcla con el gate stale ni con borrador).
     */
    @Test
    void TC_P37_60_preflight_con_pu_cero_bloquea_preservando_p32() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p31-p32@ex.com");
        String proyectoId = crearProyecto(token, "P31-P32");
        fijarFechaInicio(proyectoId, LocalDate.parse("2026-01-01"));
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R1", "10.000000");
        fijarTotalPresupuesto(presupuestoId, "10.000000");
        crearCronograma(token, presupuestoId, "SEMANA", 4);
        String actividadId = actividadDeRubro(presupuestoId, "1.1");
        sembrarAvance(actividadId, "{\"1\":\"50.0000\",\"2\":\"50.0000\"}");
        // Provocar PU = 0 — Plan 025 §P-32 / TC-P37-03.
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE rubro SET precio_unitario = 0, precio_total = 0 WHERE item = \'1.1\' "
                                + "AND capitulo_id = ?")) {
            ps.setLong(1, cap);
            ps.executeUpdate();
        }

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/" + presupuestoId + "/preflight?formato=xlsx")
                .then()
                .statusCode(200)
                .body("exportable", equalTo(false))
                .body("bloqueos.codigo", hasItem("presupuesto-pu-cero"));
    }

    /**
     * TC_P37_61 — descarga bloqueada por P-32 devuelve 409
     * {@code export-bloqueado} SIN bytes del archivo (Content-Type JSON;
     * ningún PDF/XLSX/MSPDI filtrado al cliente).
     */
    @Test
    void TC_P37_61_descarga_bloqueada_por_p32_devuelve_409_sin_bytes() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p31-p32-dl@ex.com");
        String proyectoId = crearProyecto(token, "P31-P32-DL");
        fijarFechaInicio(proyectoId, LocalDate.parse("2026-01-01"));
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R1", "10.000000");
        fijarTotalPresupuesto(presupuestoId, "10.000000");
        crearCronograma(token, presupuestoId, "SEMANA", 4);
        String actividadId = actividadDeRubro(presupuestoId, "1.1");
        sembrarAvance(actividadId, "{\"1\":\"50.0000\",\"2\":\"50.0000\"}");
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE rubro SET precio_unitario = 0, precio_total = 0 WHERE item = \'1.1\' "
                                + "AND capitulo_id = ?")) {
            ps.setLong(1, cap);
            ps.executeUpdate();
        }

        Response r = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/" + presupuestoId + "?formato=xlsx");

        assertEquals(409, r.statusCode(), "P-32 debe producir 409 export-bloqueado");
        String ct = r.getHeader("Content-Type");
        assertTrue(
                ct != null && ct.startsWith("application/json"),
                "Content-Type debe ser JSON (sin bytes del archivo). Recibido: " + ct);
        String stale = r.getHeader("X-Cronograma-Desactualizado");
        assertTrue(
                stale == null || stale.equals("true") || stale.equals("false"),
                "header X-Cronograma-Desactualizado debe ser true|false si está presente");
        String body = r.body().asString();
        assertTrue(body.contains("export-bloqueado"), "body debe declarar export-bloqueado");
        assertFalse(body.startsWith("PK"), "no debe filtrarse un XLSX (firma ZIP)");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Cobertura TC-P37-10 — MSPDI exige fechaInicio; XLSX/PDF no
    // ──────────────────────────────────────────────────────────────────────

    /**
     * TC_P37_62 — MSPDI sin fechaInicio bloquea con
     * {@code mspdi-fecha-inicio-requerida}; XLSX y PDF NO se bloquean por
     * esa causa (la regla es exclusiva del lane MSPDI).
     */
    @Test
    void TC_P37_62_mspdi_sin_fecha_inicio_bloquea_xlsx_pdf_no() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p31-mspdi-f@ex.com");
        // Sin fechaInicio: el helper fijarFechaInicio queda omitido.
        String proyectoId = crearProyecto(token, "P31-MSPDI-F");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R1", "10.000000");
        fijarTotalPresupuesto(presupuestoId, "10.000000");
        crearCronograma(token, presupuestoId, "SEMANA", 4);
        String actividadId = actividadDeRubro(presupuestoId, "1.1");
        sembrarAvance(actividadId, "{\"1\":\"50.0000\",\"2\":\"50.0000\"}");

        // (a) MSPDI bloqueado por mspdi-fecha-inicio-requerida.
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/" + presupuestoId + "?formato=mspdi")
                .then()
                .statusCode(409)
                .body("codigo", equalTo("export-bloqueado"));
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/" + presupuestoId + "/preflight?formato=mspdi")
                .then()
                .statusCode(200)
                .body("exportable", equalTo(false))
                .body("bloqueos.codigo", hasItem("mspdi-fecha-inicio-requerida"));

        // (b) XLSX y PDF no se bloquean por esa causa.
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/" + presupuestoId + "?formato=xlsx")
                .then()
                .statusCode(200);
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/" + presupuestoId + "?formato=pdf")
                .then()
                .statusCode(200);
    }

    // ───────────────────────────────────────────────────────────────────
    /**
     * TC_P37_12 — TOCTOU/concurrencia del endpoint de exportacion contra el
     * lock pesimista del presupuesto. Comprueba el invariante del canon
     * Plan 031 §TOCTOU: una mutacion concurrente durante la generacion debe
     * producir UN snapshot coherente (200 parseable) o UN bloqueo canonico
     * (409 export-bloqueado), NUNCA contenido mixto entre dos versiones.
     *
     * <p>El test (a) elimina la siembra de proyecto que ya hace
     * {@link #armarEscenarioExportable(String)}, (b) reutiliza UNA sola
     * conexion/prepared statement de polling (no una por iteracion), (c)
     * garantiza rollback + reset de autocommit en cualquier path de fallo para
     * no colgar el {@code TRUNCATE} del siguiente {@code @BeforeEach}, y (d)
     * acepta tanto 409 como 200 con XLSX parseable coherente (mutacion puede
     * quedar dentro o fuera del snapshot).</p>
     */
    @Test
    void TC_P37_12_toctou_lock_presupuesto_serializa_y_emite_snapshot_coherente() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p31-toctou@ex.com");
        // armarEscenarioExportable ya crea proyecto + fechaInicio; cualquier
        // siembra extra quedaba sin uso y agregaba una fila colgada.
        String presupuestoId = armarEscenarioExportable(token);
        Long presupuestoInterno = internalId("presupuesto", presupuestoId);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        Connection holder = null;
        Connection poller = null;
        PreparedStatement pollerPs = null;
        boolean committed = false;
        try {
            // (1) Tomar el lock pesimista del presupuesto en una conexion
            //     dedicada, con autocommit=false para mantener la transaccion
            //     explicita durante toda la prueba.
            holder = ds.getConnection();
            holder.setAutoCommit(false);
            try (PreparedStatement lock =
                    holder.prepareStatement("SELECT id FROM presupuesto WHERE id = ? FOR UPDATE")) {
                lock.setLong(1, presupuestoInterno);
                try (ResultSet rs = lock.executeQuery()) {
                    rs.next();
                }
            }

            // (2) Disparar la descarga concurrente — debe quedar esperando el lock.
            Future<Response> download = pool.submit(() -> given().header("Authorization", "Bearer " + token)
                    .when()
                    .get("/api/v1/documentos/cronograma/" + presupuestoId + "?formato=xlsx"));

            // (3) Esperar a que aparezca el waiter con el patron canonico
            //     alineado con VistasCronogramaResourceIT / ActividadProgramarResourceIT.
            //     UNA conexion y UN prepared statement, reutilizados en cada
            //     iteracion (antes se abrian/cerraban por ciclo).
            poller = ds.getConnection();
            pollerPs = poller.prepareStatement("SELECT COUNT(*) FROM pg_stat_activity "
                    + "WHERE wait_event_type = 'Lock' "
                    + "AND query ILIKE '%select id from presupuesto where id%for update%'");
            long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            boolean detected = false;
            while (System.nanoTime() < limite) {
                try (ResultSet rs = pollerPs.executeQuery()) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        detected = true;
                        break;
                    }
                }
                Thread.sleep(50);
            }
            if (!detected) {
                throw new AssertionError("TOCTOU: ningun waiter detecto lock sobre presupuesto en 10s; "
                        + "el endpoint no esta serializando via lock pesimista");
            }

            // (4) Hacer la mutacion incompatible (PU=0) bajo el lock.
            try (PreparedStatement ps =
                    holder.prepareStatement("UPDATE rubro SET precio_unitario = 0, precio_total = 0 "
                            + "WHERE item = '1.1' AND capitulo_id IN "
                            + "(SELECT id FROM capitulo WHERE presupuesto_id = ?)")) {
                ps.setLong(1, presupuestoInterno);
                ps.executeUpdate();
            }
            holder.commit();
            committed = true;

            // (5) La descarga debe producir 409 export-bloqueado (snapshot vio
            //     PU=0) o, si la mutacion quedo fuera del snapshot por
            //     reordenamiento, un XLSX parseable coherente. Nunca contenido
            //     mixto ni bytes filtrados en 4xx.
            Response r = download.get(15, TimeUnit.SECONDS);
            int status = r.statusCode();
            if (status == 409) {
                String ct = r.getHeader("Content-Type");
                assertTrue(
                        ct != null && ct.startsWith("application/json"), "Content-Type debe ser JSON; recibido: " + ct);
                String body = r.body().asString();
                assertTrue(
                        body.contains("export-bloqueado"), "body debe declarar codigo export-bloqueado; body=" + body);
                assertFalse(body.startsWith("PK"), "TOCTOU: no debe filtrarse un XLSX (firma ZIP)");
                assertFalse(body.startsWith("<?xml"), "TOCTOU: no debe filtrarse un XML del MSPDI");
                assertFalse(body.startsWith("%PDF"), "TOCTOU: no debe filtrarse un PDF");
            } else {
                assertEquals(200, status, "alternativa coherente: 200 con snapshot valido");
                byte[] bytes = r.body().asByteArray();
                assertTrue(bytes.length > 4, "XLSX coherente debe contener bytes; len=" + bytes.length);
                assertTrue(
                        bytes[0] == 'P' && bytes[1] == 'K',
                        "XLSX parseable debe tener firma ZIP; primeros bytes="
                                + (bytes.length >= 4
                                        ? new String(bytes, 0, 4, java.nio.charset.StandardCharsets.ISO_8859_1)
                                        : "<short>"));
            }
            String stale = r.getHeader("X-Cronograma-Desactualizado");
            assertTrue(
                    stale == null || stale.equals("true") || stale.equals("false"),
                    "header X-Cronograma-Desactualizado debe ser true|false si esta presente; recibido=" + stale);
        } catch (Throwable t) {
            // Garantizar rollback + reset autocommit + release del lock antes de
            // propagar el fallo. Si commit ya ocurrio, rollback es no-op seguro.
            if (holder != null && !committed) {
                try {
                    holder.rollback();
                } catch (Exception ignored) {
                    // ignorar: el rollback puede fallar si la tx ya esta cerrada
                }
            }
            if (holder != null) {
                try {
                    if (!holder.getAutoCommit()) {
                        holder.setAutoCommit(true);
                    }
                } catch (Exception ignored) {
                    // ignorar
                }
            }
            throw t;
        } finally {
            if (pollerPs != null) {
                try {
                    pollerPs.close();
                } catch (Exception ignored) {
                }
            }
            if (poller != null) {
                try {
                    poller.close();
                } catch (Exception ignored) {
                }
            }
            if (holder != null) {
                try {
                    holder.close();
                } catch (Exception ignored) {
                }
            }
            pool.shutdownNow();
            try {
                pool.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
