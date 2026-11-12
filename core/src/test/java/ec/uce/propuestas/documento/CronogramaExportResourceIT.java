package ec.uce.propuestas.documento;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ec.uce.propuestas.documento.exportacion.RemoteExportUploadGateway;
import ec.uce.propuestas.documentos.contract.ExportUploadV1;
import ec.uce.propuestas.documentos.contract.StoredObjectV1;
import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.InjectMock;
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
 *   <li>{@code POST /documentos/cronograma/{presupuestoId}/exportaciones} → ticket de un solo uso
 *       (etapa 05: el GET de bytes se retiró; Documentos renderiza y sube a S3 privado, aquí simulado
 *       en {@link RemoteExportUploadGateway}).</li>
 *   <li>400 para UUID malformado/no-v7 o formato inválido.</li>
 *   <li>404 para presupuesto ajeno o inexistente.</li>
 *   <li>409 con código {@code export-bloqueado} para exportación bloqueada, sin subida ni ticket.</li>
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

    @InjectMock
    RemoteExportUploadGateway uploads;

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

    private static final java.util.concurrent.atomic.AtomicLong CLAVES =
            new java.util.concurrent.atomic.AtomicLong(0x5000);

    private static String nuevaClave() {
        return String.format("018f0000-0000-7000-8000-%012x", CLAVES.incrementAndGet());
    }

    private Response exportar(String token, String presupuestoId, String body) {
        return given().header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", nuevaClave())
                .contentType("application/json")
                .body(body)
                .post("/api/v1/documentos/cronograma/" + presupuestoId + "/exportaciones");
    }

    /** Documentos/S3 accept every upload; returns the requests Core sent across the boundary. */
    private java.util.List<ExportUploadV1> aceptarSubidas() {
        var enviadas = new java.util.concurrent.CopyOnWriteArrayList<ExportUploadV1>();
        when(uploads.upload(any(ExportUploadV1.class))).thenAnswer(inv -> {
            ExportUploadV1 request = inv.getArgument(0);
            enviadas.add(request);
            String mime = switch (request.cronograma().format()) {
                case "pdf" -> "application/pdf";
                case "mspdi" -> "application/xml";
                default -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            };
            return new StoredObjectV1(request.key(), 10, "A".repeat(43) + "=", mime, request.filename());
        });
        return enviadas;
    }

    /** A blocked export answers JSON only: no upload, no ticket, no file signature. */
    private static void sinArchivo(Response r) {
        assertTrue(r.getHeader("Content-Type").startsWith("application/json"), r.getHeader("Content-Type"));
        String body = r.asString();
        assertNull(r.jsonPath().getString("downloadUrl"));
        assertFalse(body.startsWith("PK"), "no debe filtrarse un XLSX (firma ZIP)");
        assertFalse(body.startsWith("<?xml"), "no debe filtrarse un XML del MSPDI");
        assertFalse(body.startsWith("%PDF"), "no debe filtrarse un PDF");
    }

    @Test
    void postConfirmsCapturedFormatsAndReissuesOneUseTicketWithoutReupload() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "post-formats@ex.com");
        String budgetId = armarEscenarioExportable(token);
        String route = "/api/v1/documentos/cronograma/" + budgetId + "/exportaciones";
        String checksum = "A".repeat(43) + "=";
        when(uploads.upload(any(ExportUploadV1.class))).thenAnswer(inv -> {
            ExportUploadV1 request = inv.getArgument(0);
            var projection = request.cronograma();
            assertNotNull(projection);
            String format = projection.format();
            String suffix = "mspdi".equals(format) ? ".xml" : "." + format;
            assertTrue(request.filename().endsWith(suffix), request.filename());
            assertEquals("pdf".equals(format) ? "a4" : null, projection.paper());
            String mime = switch (format) {
                case "pdf" -> "application/pdf";
                case "mspdi" -> "application/xml";
                default -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            };
            return new StoredObjectV1(request.key(), 10, checksum, mime, request.filename());
        });
        for (int i = 1; i <= 3; i++) {
            String format = new String[] {"xlsx", "pdf", "mspdi"}[i - 1];
            String key = "018f0000-0000-7000-8000-00000000000" + i;
            var first = given().header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", key)
                    .contentType("application/json")
                    .body("{\"formato\":\"" + format + "\"}")
                    .post(route);
            assertEquals(200, first.statusCode(), first.asString());
            assertTrue(first.jsonPath().getString("downloadUrl").startsWith("/api/v1/documentos/descargas/"));
            assertEquals(checksum, first.jsonPath().getString("sha256"));
            var replay = given().header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", key)
                    .contentType("application/json")
                    .body("{\"formato\":\"" + format + "\"}")
                    .post(route);
            assertEquals(200, replay.statusCode(), replay.asString());
            assertTrue(!first.jsonPath()
                    .getString("downloadUrl")
                    .equals(replay.jsonPath().getString("downloadUrl")));
        }
        verify(uploads, times(3)).upload(any(ExportUploadV1.class));
    }

    @Test
    void postRespetaOwnershipYBloqueosSinGetDeBytes() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "post-owner@ex.com");
        String intruso = AuthSupport.registrarConToken(mailbox, "post-other@ex.com");
        String presupuestoId = armarEscenarioExportable(token);
        String url = "/api/v1/documentos/cronograma/" + presupuestoId;
        String key = "018f0000-0000-7000-8000-000000000001";
        given().header("Authorization", "Bearer " + intruso)
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .body("{\"formato\":\"xlsx\"}")
                .post(url + "/exportaciones")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
        sembrarAvance(actividadDeRubro(presupuestoId, "1.1"), "{}");
        given().header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .body("{\"formato\":\"xlsx\"}")
                .post(url + "/exportaciones")
                .then()
                .statusCode(409)
                .body("codigo", equalTo("export-bloqueado"))
                .body("bloqueos.size()", org.hamcrest.Matchers.greaterThan(0));
        given().header("Authorization", "Bearer " + token)
                .get(url + "?formato=xlsx")
                .then()
                .statusCode(404);
        assertEquals(0L, contarDocumentoExportado("XLSX"));
        verifyNoInteractions(uploads);
    }

    /**
     * EXP-02 — el papel A4 por defecto o A3 viaja en la proyección capturada. La MediaBox del PDF
     * (842x595 / 1191x842, sin rotación) la verifica el renderer de Documentos desde este campo.
     */
    @Test
    void papel_pdf_a4_default_y_a3_horizontal() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "exp02-papel@ex.com");
        String presupuestoId = armarEscenarioExportable(token);
        var enviadas = aceptarSubidas();
        String[][] casos = {{"", "a4"}, {"&papel=a4", "a4"}, {"&papel=a3", "a3"}};
        for (String[] caso : casos) {
            given().header("Authorization", "Bearer " + token)
                    .get("/api/v1/documentos/cronograma/" + presupuestoId + "/preflight?formato=pdf" + caso[0])
                    .then()
                    .statusCode(200)
                    .body("exportable", equalTo(true));
            String papel = caso[0].isEmpty() ? "" : ",\"papel\":\"" + caso[1] + "\"";
            Response r = exportar(token, presupuestoId, "{\"formato\":\"pdf\"" + papel + "}");
            assertEquals(200, r.statusCode(), r.asString());
            assertEquals(caso[1], enviadas.getLast().cronograma().paper());
            assertEquals("pdf", enviadas.getLast().cronograma().format());
        }
    }

    /** Opciones ajenas o malformadas fallan antes de capturar o subir documentos. */
    @Test
    void papel_invalido_y_combinaciones_devuelven_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "exp02-invalid@ex.com");
        String presupuestoId = armarEscenarioExportable(token);
        String[][] casos = {
            {"formato=pdf&papel=", "{\"formato\":\"pdf\",\"papel\":\"\"}"},
            {"formato=pdf&papel=letter", "{\"formato\":\"pdf\",\"papel\":\"letter\"}"},
            {"formato=pdf&papel=A3", "{\"formato\":\"pdf\",\"papel\":\"A3\"}"},
            {"formato=pdf&papel=%20a3", "{\"formato\":\"pdf\",\"papel\":\" a3\"}"},
            {"formato=xlsx&papel=a4", "{\"formato\":\"xlsx\",\"papel\":\"a4\"}"},
            {"formato=mspdi&papel=a3", "{\"formato\":\"mspdi\",\"papel\":\"a3\"}"}
        };
        for (String[] caso : casos) {
            Response preflight = given().header("Authorization", "Bearer " + token)
                    .get("/api/v1/documentos/cronograma/" + presupuestoId + "/preflight?" + caso[0]);
            assertEquals(400, preflight.statusCode(), caso[0]);
            assertEquals("validacion", preflight.jsonPath().getString("codigo"));
            Response post = exportar(token, presupuestoId, caso[1]);
            assertEquals(400, post.statusCode(), caso[1]);
            assertEquals("validacion", post.jsonPath().getString("codigo"));
            assertNull(post.jsonPath().getString("downloadUrl"));
        }
        assertEquals(0L, contarDocumentoExportado("PDF"));
        verifyNoInteractions(uploads);
    }

    /** A3 conserva UUID estricto, ownership y bloqueo sin subida ni ticket. */
    @Test
    void papel_a3_conserva_gates() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "exp02-owner@ex.com");
        String intruso = AuthSupport.registrarConToken(mailbox, "exp02-other@ex.com");
        String presupuestoId = armarEscenarioExportable(token);
        String a3 = "{\"formato\":\"pdf\",\"papel\":\"a3\"}";
        given().header("Authorization", "Bearer " + intruso)
                .get("/api/v1/documentos/cronograma/" + presupuestoId + "/preflight?formato=pdf&papel=a3")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
        Response ajeno = exportar(intruso, presupuestoId, a3);
        assertEquals(404, ajeno.statusCode(), ajeno.asString());
        assertEquals("no-encontrado", ajeno.jsonPath().getString("codigo"));
        for (String id : new String[] {"incorrecto", UUID_NO_V7}) {
            given().header("Authorization", "Bearer " + token)
                    .get("/api/v1/documentos/cronograma/" + id + "/preflight?formato=pdf&papel=a3")
                    .then()
                    .statusCode(400)
                    .body("codigo", equalTo("validacion"));
            Response malformado = exportar(token, id, a3);
            assertEquals(400, malformado.statusCode(), id);
            assertEquals("validacion", malformado.jsonPath().getString("codigo"));
        }
        sembrarAvance(actividadDeRubro(presupuestoId, "1.1"), "{}");
        given().header("Authorization", "Bearer " + token)
                .get("/api/v1/documentos/cronograma/" + presupuestoId + "/preflight?formato=pdf&papel=a3")
                .then()
                .statusCode(200)
                .body("exportable", equalTo(false));
        Response r = exportar(token, presupuestoId, a3);
        assertEquals(409, r.statusCode());
        assertEquals("export-bloqueado", r.jsonPath().getString("codigo"));
        sinArchivo(r);
        assertEquals(0L, contarDocumentoExportado("PDF"));
        verifyNoInteractions(uploads);
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
        // Plan 021: la distribución por defecto completa el cronograma; se vacía el mapa → suma 0 → bloqueado.
        sembrarAvance(actividadDeRubro(presupuestoId, "1.1"), "{}");

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

    /** TC_P37_53/54/55 — XLSX, PDF y MSPDI: 200 con ticket, media type y nombre propios, y D-13 por formato. */
    @Test
    void TC_P37_53_55_exportacion_por_formato_media_type_filename_y_auditoria() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p31-dl@ex.com");
        String presupuestoId = armarEscenarioExportable(token);
        aceptarSubidas();
        String[][] casos = {
            {"xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx", "XLSX"},
            {"pdf", "application/pdf", ".pdf", "PDF"},
            {"mspdi", "application/xml", ".xml", "MSPDI"}
        };
        for (String[] caso : casos) {
            Response r = exportar(token, presupuestoId, "{\"formato\":\"" + caso[0] + "\"}");
            assertEquals(200, r.statusCode(), caso[0] + " " + r.asString());
            assertEquals(caso[1], r.jsonPath().getString("mediaType"));
            assertTrue(r.jsonPath().getString("filename").toLowerCase().endsWith(caso[2]), caso[0]);
            assertTrue(r.jsonPath().getString("downloadUrl").startsWith("/api/v1/documentos/descargas/"));
            assertEquals(1L, contarDocumentoExportado(caso[3]), caso[0]);
        }
    }

    /** TC_P37_56 — owner-to-404: cronograma ajeno responde 404 en preflight y exportación. */
    @Test
    void TC_P37_56_presupuesto_ajeno_devuelve_404_en_preflight_y_exportacion() throws Exception {
        String titular = AuthSupport.registrarConToken(mailbox, "p31-own-t@ex.com");
        String intruso = AuthSupport.registrarConToken(mailbox, "p31-own-i@ex.com");
        String presupuestoId = armarEscenarioExportable(titular);

        given().header("Authorization", "Bearer " + intruso)
                .when()
                .get("/api/v1/documentos/cronograma/" + presupuestoId + "/preflight?formato=xlsx")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
        Response r = exportar(intruso, presupuestoId, "{\"formato\":\"xlsx\"}");
        assertEquals(404, r.statusCode(), r.asString());
        assertEquals("no-encontrado", r.jsonPath().getString("codigo"));
        verifyNoInteractions(uploads);
    }

    /** TC_P37_57 — UUID malformado/no-v7 o formato inválido → 400 validacion. */
    @Test
    void TC_P37_57_uuid_malformado_y_formato_invalido_devuelven_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p31-uuid@ex.com");
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/" + UUID_NO_V7 + "/preflight?formato=xlsx")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
        Response noV7 = exportar(token, UUID_NO_V7, "{\"formato\":\"xlsx\"}");
        assertEquals(400, noV7.statusCode(), noV7.asString());
        assertEquals("validacion", noV7.jsonPath().getString("codigo"));
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/0192f6c4-7c8a-7000-8000-000000000000/preflight?formato=bogus")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
        Response bogus = exportar(token, "0192f6c4-7c8a-7000-8000-000000000000", "{\"formato\":\"bogus\"}");
        assertEquals(400, bogus.statusCode(), bogus.asString());
        assertEquals("validacion", bogus.jsonPath().getString("codigo"));
        verifyNoInteractions(uploads);
    }

    /** TC_P37_58 — exportación de un borrador → 409 export-bloqueado sin subida ni ticket. */
    @Test
    void TC_P37_58_exportacion_bloqueada_devuelve_409_export_bloqueado() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p31-blq@ex.com");
        String proyectoId = crearProyecto(token, "P31-BLQ");
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        sembrarRubro(pId, cap, "1.1", "R1", "10.000000");
        fijarTotalPresupuesto(presupuestoId, "10.000000");
        crearCronograma(token, presupuestoId, "SEMANA", 4);
        // Plan 021: se vacía el mapa por defecto para forzar el borrador.
        sembrarAvance(actividadDeRubro(presupuestoId, "1.1"), "{}");

        Response r = exportar(token, presupuestoId, "{\"formato\":\"xlsx\"}");
        assertEquals(409, r.statusCode(), r.asString());
        assertEquals("export-bloqueado", r.jsonPath().getString("codigo"));
        sinArchivo(r);
        verifyNoInteractions(uploads);
    }

    /**
     * TC_P37_59 — el estado stale viaja en {@code warnings} de la respuesta (el header
     * {@code X-Cronograma-Desactualizado} era exclusivo del GET de bytes retirado).
     */
    @Test
    void TC_P37_59_estado_stale_viaja_en_warnings() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p31-stl-h@ex.com");
        String presupuestoId = armarEscenarioExportable(token);
        aceptarSubidas();

        Response r = exportar(token, presupuestoId, "{\"formato\":\"xlsx\"}");
        assertEquals(200, r.statusCode(), r.asString());
        assertNotNull(r.jsonPath().getList("warnings"), "warnings obligatorio");
        assertEquals(null, r.getHeader("X-Cronograma-Desactualizado"));
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
     * TC_P37_61 — exportación bloqueada por P-32 devuelve 409 {@code export-bloqueado} JSON, sin subida a
     * Documentos ni bytes del archivo.
     */
    @Test
    void TC_P37_61_exportacion_bloqueada_por_p32_devuelve_409_sin_bytes() throws Exception {
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

        Response r = exportar(token, presupuestoId, "{\"formato\":\"xlsx\"}");
        assertEquals(409, r.statusCode(), "P-32 debe producir 409 export-bloqueado");
        assertTrue(r.asString().contains("export-bloqueado"), "body debe declarar export-bloqueado");
        assertTrue(r.jsonPath().getList("bloqueos.codigo").contains("presupuesto-pu-cero"));
        sinArchivo(r);
        verifyNoInteractions(uploads);
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
        var enviadas = aceptarSubidas();

        // (a) MSPDI bloqueado por mspdi-fecha-inicio-requerida.
        Response mspdi = exportar(token, presupuestoId, "{\"formato\":\"mspdi\"}");
        assertEquals(409, mspdi.statusCode(), mspdi.asString());
        assertEquals("export-bloqueado", mspdi.jsonPath().getString("codigo"));
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/cronograma/" + presupuestoId + "/preflight?formato=mspdi")
                .then()
                .statusCode(200)
                .body("exportable", equalTo(false))
                .body("bloqueos.codigo", hasItem("mspdi-fecha-inicio-requerida"));

        // (b) XLSX y PDF no se bloquean por esa causa.
        assertEquals(
                200, exportar(token, presupuestoId, "{\"formato\":\"xlsx\"}").statusCode());
        assertEquals(
                200, exportar(token, presupuestoId, "{\"formato\":\"pdf\"}").statusCode());
        assertEquals(
                java.util.List.of("xlsx", "pdf"),
                enviadas.stream().map(e -> e.cronograma().format()).toList());
    }

    // ───────────────────────────────────────────────────────────────────
    /**
     * TC_P37_12 — TOCTOU/concurrencia de la captura contra el lock pesimista
     * del presupuesto (etapa 04, «captura consistente»): una mutación
     * concurrente debe producir UNA proyección coherente subida (200 con
     * ticket) o UN bloqueo canónico (409 export-bloqueado sin subida), NUNCA
     * contenido mixto ni bytes. La subida a Documentos ocurre fuera del lock.
     *
     * <p>Reutiliza UNA conexión/prepared statement de polling y garantiza
     * rollback + reset de autocommit en cualquier path de fallo para no
     * colgar el {@code TRUNCATE} del siguiente {@code @BeforeEach}.</p>
     */
    @Test
    void TC_P37_12_toctou_lock_presupuesto_serializa_y_captura_coherente() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p31-toctou@ex.com");
        String presupuestoId = armarEscenarioExportable(token);
        Long presupuestoInterno = internalId("presupuesto", presupuestoId);
        var enviadas = aceptarSubidas();

        ExecutorService pool = Executors.newSingleThreadExecutor();
        Connection holder = null;
        Connection poller = null;
        PreparedStatement pollerPs = null;
        boolean committed = false;
        try {
            // (1) Tomar el lock pesimista del presupuesto en una conexión dedicada.
            holder = ds.getConnection();
            holder.setAutoCommit(false);
            try (PreparedStatement lock =
                    holder.prepareStatement("SELECT id FROM presupuesto WHERE id = ? FOR UPDATE")) {
                lock.setLong(1, presupuestoInterno);
                try (ResultSet rs = lock.executeQuery()) {
                    rs.next();
                }
            }

            // (2) Disparar la exportación concurrente — la captura debe quedar esperando el lock.
            Future<Response> exportacion = pool.submit(() -> exportar(token, presupuestoId, "{\"formato\":\"xlsx\"}"));

            // (3) Esperar a que aparezca el waiter (una conexión y un statement reutilizados).
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
                        + "la captura no esta serializando via lock pesimista");
            }

            // (4) Hacer la mutación incompatible (PU=0) bajo el lock.
            try (PreparedStatement ps =
                    holder.prepareStatement("UPDATE rubro SET precio_unitario = 0, precio_total = 0 "
                            + "WHERE item = '1.1' AND capitulo_id IN "
                            + "(SELECT id FROM capitulo WHERE presupuesto_id = ?)")) {
                ps.setLong(1, presupuestoInterno);
                ps.executeUpdate();
            }
            holder.commit();
            committed = true;

            // (5) 409 sin subida (la captura vio PU=0) o 200 con una proyección coherente.
            Response r = exportacion.get(15, TimeUnit.SECONDS);
            if (r.statusCode() == 409) {
                assertEquals("export-bloqueado", r.jsonPath().getString("codigo"), r.asString());
                sinArchivo(r);
                assertTrue(enviadas.isEmpty(), "TOCTOU: un bloqueo nunca sube a Documentos");
            } else {
                assertEquals(200, r.statusCode(), "alternativa coherente: 200 con proyección valida");
                assertEquals(1, enviadas.size());
                assertTrue(r.jsonPath().getString("downloadUrl").startsWith("/api/v1/documentos/descargas/"));
            }
        } catch (Throwable t) {
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

    /**
     * Plan 023-A — con doce rubros en un capitulo, el cronograma (GET) y la hoja capturada para el XLSX
     * deben listarlos en orden natural 1.1, 1.2, ..., 1.10, 1.11, 1.12 (no 1.1, 1.10, 1.11, 1.12, 1.2...).
     * El orden de filas del archivo lo fija esta proyección; Documentos solo la escribe.
     */
    @Test
    void TC_P23_A_cronograma_y_xlsx_ordenan_naturalmente_doce_rubros() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23-orden@ex.com");
        String proyectoId = crearProyecto(token, "P23-ORDEN");
        fijarFechaInicio(proyectoId, LocalDate.parse("2026-01-01"));
        String presupuestoId = vigenteDeProyecto(proyectoId);
        Long pId = internalId("presupuesto", presupuestoId);
        long cap = sembrarCapitulo(pId, null, "1", "Cap", (short) 1, "0");
        java.util.List<String> esperado = new java.util.ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            // pesos exactos (8 x10 + 10 x2 = 100) para no introducir desviacion por redondeo
            sembrarRubro(pId, cap, "1." + i, "R" + i, i <= 10 ? "8.000000" : "10.000000");
            esperado.add("1." + i);
        }
        fijarTotalPresupuesto(presupuestoId, "100.000000");
        crearCronograma(token, presupuestoId, "SEMANA", 4);
        // Plan 021: el cronograma nace con distribucion por defecto exportable.

        java.util.List<String> items = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/cronograma")
                .then()
                .statusCode(200)
                .extract()
                .path("actividades.item");
        assertEquals(esperado, items, "GET cronograma fuera de orden natural");

        var enviadas = aceptarSubidas();
        Response r = exportar(token, presupuestoId, "{\"formato\":\"xlsx\"}");
        assertEquals(200, r.statusCode(), r.asString());
        java.util.List<String> enHoja = enviadas.getFirst().cronograma().sheet().rows().stream()
                .map(row -> row.item())
                .toList();
        assertEquals(esperado, enHoja, "Hoja XLSX capturada fuera de orden natural");
    }
}
