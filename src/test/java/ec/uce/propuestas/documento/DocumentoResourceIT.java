package ec.uce.propuestas.documento;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * P-45 (N04 §ESP + N04-bis): parse-back focalizado del DOCX de
 * Especificaciones Técnicas generado por
 * {@code GET /documentos/especificaciones-tecnicas/{presupuestoId}}.
 *
 * <p>Plan 07 — el {@code presupuestoId} del path es la identidad externa
 * UUIDv7 (columna {@code presupuesto.public_id}). El {@code BIGINT} interno se
 * retiene debajo de los seeds SQL; los asserts de contrato y las URLs de los
 * resources usan el UUID público.</p>
 */
@QuarkusTest
class DocumentoResourceIT {

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE log_actividad, apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, token_usuario, "
                    + "refresh_token, usuario RESTART IDENTITY CASCADE");
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

    private String crearProyecto(String token, String nombre, Short anio) {
        return given().contentType("application/json")
                .header("Authorization", "Bearer " + token)
                .body(java.util.Map.of(
                        "nombreProyecto",
                        nombre,
                        "anio",
                        anio,
                        "plazoEjecucion",
                        (short) 4,
                        "plazoUnidad",
                        "MES",
                        "direccionInstitucional",
                        "Universidad Central del Ecuador"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String insertarPresupuesto(String proyectoId) throws Exception {
        // Plan 021 — el Presupuesto v1 vigente se crea automáticamente al
        // crear el proyecto (POST /proyectos → ProyectoService.crear). Este
        // helper ya no inserta otra fila: la lee para devolver el publicId
        // UUIDv7 que consume el path del endpoint de documentos.
        Long proyectoIdInterno = internalProyectoId(proyectoId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT public_id FROM presupuesto WHERE proyecto_id = ? AND version = 1")) {
            ps.setLong(1, proyectoIdInterno);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
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

    /** Resuelve el {@code BIGINT} interno del proyecto (necesario para UPDATE de titulo_et_*). */
    private Long internalProyectoIdForUpdate(String proyectoPublicId) throws Exception {
        return internalProyectoId(proyectoPublicId);
    }

    private String crearApu(String token, String presupuestoId, String codigo, String descripcion, String unidad) {
        return given().contentType("application/json")
                .header("Authorization", "Bearer " + token)
                .body(java.util.Map.of("codigo", codigo, "descripcion", descripcion, "unidad", unidad))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/apus")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private void ponerEt(String token, String apuId, String texto) {
        given().contentType("application/json")
                .header("Authorization", "Bearer " + token)
                .body(java.util.Map.of("texto", texto))
                .when()
                .put("/api/v1/apus/" + apuId + "/especificacion-tecnica")
                .then()
                .statusCode(200);
    }

    /**
     * Resuelve el {@code BIGINT} interno de un APU a partir de su UUID público. Se usa
     * exclusivamente para sembrar filas SQL (UPDATE/DELETE por id interno) que requieren el
     * {@code BIGINT}; los asserts de contrato y las URLs de los resources usan el UUID público.
     */
    private Long internalApuId(String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM apu WHERE public_id = ?")) {
            ps.setObject(1, java.util.UUID.fromString(publicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private static String leerTexto(XWPFDocument doc) {
        StringBuilder sb = new StringBuilder();
        for (XWPFParagraph p : doc.getParagraphs()) {
            sb.append(p.getText()).append('\n');
        }
        return sb.toString();
    }

    private static XWPFDocument parsearDocx(byte[] bytes) throws IOException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            return new XWPFDocument(in);
        }
    }

    private static byte[] descargarDocx(String token, String presupuestoId, String query) {
        String path = "/api/v1/documentos/especificaciones-tecnicas/" + presupuestoId;
        var request = given().header("Authorization", "Bearer " + token);
        if (query != null && !query.isBlank()) {
            // parseo tolerante: "?formato=docx&titulo1=...&titulo2=..."
            for (String part : query.replace("?", "").split("&")) {
                if (part.isBlank()) continue;
                int eq = part.indexOf('=');
                String key = eq < 0 ? part : part.substring(0, eq);
                String value = eq < 0 ? "" : part.substring(eq + 1);
                request = request.queryParam(key, value);
            }
        }
        Response r = request.when().get(path);
        if (r.statusCode() != 200) {
            return new byte[0];
        }
        return r.body().asByteArray();
    }

    @Test
    void TC_P45_05_docx_precedencia_titulos_override_gana_sobre_proyecto() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p45t1@ex.com");
        String proyectoId = crearProyecto(token, "Rehabilitación Tulcán", (short) 2026);
        // fijamos titulo_et_1 / titulo_et_2 desde SQL para garantizar que el proyecto
        // tiene valores preexistentes y la precedencia de los overrides es observable
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE proyecto SET titulo_et_1 = ?, titulo_et_2 = ? WHERE id = ?")) {
            ps.setString(1, "PLIEGO BASE TULCÁN");
            ps.setString(2, "ETs Tulcán desde proyecto");
            ps.setLong(3, internalProyectoIdForUpdate(proyectoId));
            ps.executeUpdate();
        }
        String presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(token, presupuestoId, "PZ-001", "Pozo", "u");
        ponerEt(token, apuId, "Excavación manual, entibado, nivelación.");

        byte[] bytes = descargarDocx(
                token, presupuestoId, "?formato=docx&titulo1=Override titulo 1&titulo2=Override titulo 2");

        assertTrue(bytes.length > 0, "el endpoint debe devolver bytes DOCX");

        try (XWPFDocument doc = parsearDocx(bytes)) {
            String texto = leerTexto(doc);
            assertTrue(
                    texto.contains("Override titulo 1"),
                    "el override titulo1 debe prevalecer sobre titulo_et_1 del proyecto. Texto:\n" + texto);
            assertTrue(
                    texto.contains("Override titulo 2"),
                    "el override titulo2 debe prevalecer sobre titulo_et_2 del proyecto. Texto:\n" + texto);
            assertFalse(
                    texto.contains("PLIEGO BASE TULCÁN"),
                    "el titulo_et_1 del proyecto no debe aparecer cuando hay override. Texto:\n" + texto);
            assertFalse(
                    texto.contains("ETs Tulcán desde proyecto"),
                    "el titulo_et_2 del proyecto no debe aparecer cuando hay override. Texto:\n" + texto);
        }
    }

    @Test
    void TC_P45_06_docx_titulos_proyecto_y_defaults_si_no_hay_override() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p45t2@ex.com");
        String proyectoId = crearProyecto(token, "Hospital Solanda", (short) 2027);
        String presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(token, presupuestoId, "HOS-001", "Losa", "m2");
        ponerEt(token, apuId, "Concreto fc=210 kg/cm2.");

        // escenario A: titulo_et_1/titulo_et_2 del proyecto definidos y sin overrides
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE proyecto SET titulo_et_1 = ?, titulo_et_2 = ? WHERE id = ?")) {
            ps.setString(1, "ESPECIFICACIONES HOSPITAL SOLANDA");
            ps.setString(2, "Subcabecera persistida");
            ps.setLong(3, internalProyectoIdForUpdate(proyectoId));
            ps.executeUpdate();
        }

        byte[] bytes = descargarDocx(token, presupuestoId, "?formato=docx");
        try (XWPFDocument doc = parsearDocx(bytes)) {
            String texto = leerTexto(doc);
            assertTrue(
                    texto.contains("ESPECIFICACIONES HOSPITAL SOLANDA"),
                    "titulo1 debe leerse de proyecto.titulo_et_1. Texto:\n" + texto);
            assertTrue(
                    texto.contains("Subcabecera persistida"),
                    "titulo2 debe leerse de proyecto.titulo_et_2. Texto:\n" + texto);
            assertFalse(
                    texto.contains("ESPECIFICACIONES TÉCNICAS"),
                    "no debe aparecer el default si titulo_et_1 está poblado. Texto:\n" + texto);
        }

        // escenario B: titulo_et_1/titulo_et_2 NULL en proyecto y sin overrides → defaults
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE proyecto SET titulo_et_1 = NULL, titulo_et_2 = NULL WHERE id = ?")) {
            ps.setLong(1, internalProyectoIdForUpdate(proyectoId));
            ps.executeUpdate();
        }

        byte[] bytesB = descargarDocx(token, presupuestoId, "?formato=docx");
        try (XWPFDocument doc = parsearDocx(bytesB)) {
            String texto = leerTexto(doc);
            assertTrue(
                    texto.contains("ESPECIFICACIONES TÉCNICAS"),
                    "titulo1 por default cuando titulo_et_1 es NULL. Texto:\n" + texto);
            assertTrue(
                    texto.contains("Hospital Solanda"),
                    "titulo2 por default = nombre_proyecto cuando titulo_et_2 es NULL. Texto:\n" + texto);
        }
    }

    @Test
    void TC_P45_07_docx_omite_apus_sin_et_y_rechaza_sin_contenido() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p45t3@ex.com");
        String proyectoId = crearProyecto(token, "Puente Norte", (short) 2026);
        String presupuestoId = insertarPresupuesto(proyectoId);

        String conEt = crearApu(token, presupuestoId, "PN-001", "Con ET", "u");
        String sinEt = crearApu(token, presupuestoId, "PN-002", "Sin ET", "u");
        String conBlanco = crearApu(token, presupuestoId, "PN-003", "Con ET blanco", "u");
        ponerEt(token, conEt, "Especificación real del primer APU");
        ponerEt(token, conBlanco, "   \n   "); // solo whitespace, debe tratarse como vacío

        byte[] bytes = descargarDocx(token, presupuestoId, "?formato=docx");

        try (XWPFDocument doc = parsearDocx(bytes)) {
            String texto = leerTexto(doc);
            assertTrue(texto.contains("PN-001"), "el APU con ET poblada debe aparecer. Texto:\n" + texto);
            assertFalse(texto.contains("PN-002"), "el APU sin ET debe omitirse. Texto:\n" + texto);
            assertFalse(
                    texto.contains("PN-003"), "el APU con ET en blanco debe omitirse (no nonblank). Texto:\n" + texto);
            assertTrue(
                    texto.contains("Especificación real del primer APU"),
                    "el cuerpo de la ET del APU poblado debe aparecer. Texto:\n" + texto);
        }

        // ahora borramos la única ET que quedaba → 400 validacion
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE apu SET especificacion_tecnica = NULL WHERE id = ?")) {
            ps.setLong(1, internalApuId(conEt));
            ps.executeUpdate();
        }
        // limpiamos también el "en blanco" para que el filtro de no-vacío los excluya
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("DELETE FROM apu WHERE id IN (?, ?)")) {
            ps.setLong(1, internalApuId(sinEt));
            ps.setLong(2, internalApuId(conBlanco));
            ps.executeUpdate();
        }
        // borramos la fila HM para no dejar seccion sin filas (no estorba al export, pero dejamos limpio)
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/especificaciones-tecnicas/" + presupuestoId + "?formato=docx")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P45_08_docx_no_contiene_campos_monetarios_y_respeta_mime() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p45t4@ex.com");
        String proyectoId = crearProyecto(token, "Mercado Sur", (short) 2026);
        String presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(token, presupuestoId, "MS-001", "Pavimento", "m2");
        // texto que deliberadamente contiene números que podrían confundirse con precios
        ponerEt(
                token,
                apuId,
                "Pavimento de hormigón H21, espesor 0.18 m, malla electrosoldada R-84, "
                        + "procedimiento constructivo según norma NEC-2015.");

        Response r = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/especificaciones-tecnicas/" + presupuestoId + "?formato=docx");

        assertEquals(200, r.statusCode(), "el endpoint debe responder 200 con un DOCX");
        String contentType = r.getHeader("Content-Type");
        assertTrue(
                contentType != null
                        && contentType.startsWith(
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                "Content-Type debe ser DOCX oficial. Recibido: " + contentType);
        String disposition = r.getHeader("Content-Disposition");
        assertTrue(
                disposition != null && disposition.startsWith("attachment") && disposition.contains(".docx\""),
                "Content-Disposition debe declarar attachment + nombre .docx. Recibido: " + disposition);

        byte[] bytes = r.body().asByteArray();
        assertEquals(1L, contarDocumentoExportado("DOCX"));
        try (XWPFDocument doc = parsearDocx(bytes)) {
            String texto = leerTexto(doc);
            // 1) los nombres de campos monetarios del modelo no deben aparecer
            assertFalse(
                    texto.contains("costo_directo")
                            || texto.contains("costoIndirecto")
                            || texto.contains("costoTotal")
                            || texto.contains("precioUnitario")
                            || texto.contains("precio_unitario")
                            || texto.contains("tarifaJornal")
                            || texto.contains("porcentajeIndirecto"),
                    "el cuerpo del DOCX no debe mencionar campos monetarios del modelo. Texto:\n" + texto);
            // 2) header de sección esperado
            assertTrue(texto.contains("MS-001"), "debe aparecer el código del APU en el header de sección");
            assertTrue(
                    texto.contains("1. MS-001 — Pavimento [m2]"),
                    "header de sección debe seguir el patrón 'n. código — descripción [unidad]'. Texto:\n" + texto);
            // 3) subcabecera con el nombre del proyecto
            assertTrue(
                    texto.contains("Proyecto: Mercado Sur"),
                    "la subcabecera debe mencionar el nombre del proyecto. Texto:\n" + texto);
        }

        // comparación naive sobre los bytes crudos: tampoco en el XML OOXML
        String utf8 = new String(bytes, StandardCharsets.UTF_8);
        assertFalse(utf8.contains("costo directo"), "el XML del DOCX no debe contener 'costo directo'");
        assertFalse(utf8.contains("COSTO DIRECTO"), "el XML del DOCX no debe contener 'COSTO DIRECTO'");
        assertFalse(utf8.contains("precioUnitario"), "el XML del DOCX no debe contener 'precioUnitario'");
    }

    @Test
    void TC_P45_09_docx_devuelve_404_si_presupuesto_ajeno() throws Exception {
        String titular = AuthSupport.registrarConToken(mailbox, "p45t5a@ex.com");
        String proyectoId = crearProyecto(titular, "Solo titular", (short) 2026);
        String presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(titular, presupuestoId, "PR-001", "Privado", "u");
        ponerEt(titular, apuId, "Contenido real, no debe exportarse a intruso");

        String intruso = AuthSupport.registrarConToken(mailbox, "p45t5b@ex.com");
        given().header("Authorization", "Bearer " + intruso)
                .when()
                .get("/api/v1/documentos/especificaciones-tecnicas/" + presupuestoId + "?formato=docx")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        // el titular sigue pudiendo exportar
        given().header("Authorization", "Bearer " + titular)
                .when()
                .get("/api/v1/documentos/especificaciones-tecnicas/" + presupuestoId + "?formato=docx")
                .then()
                .statusCode(200);
    }

    @Test
    void TC_P45_10_docx_rechaza_formato_distinto_a_docx() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p45t6@ex.com");
        String proyectoId = crearProyecto(token, "Solo docx", (short) 2026);
        String presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(token, presupuestoId, "FM-001", "Filtro formato", "u");
        ponerEt(token, apuId, "Texto de prueba");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/especificaciones-tecnicas/" + presupuestoId + "?formato=pdf")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/especificaciones-tecnicas/" + presupuestoId)
                .then()
                .statusCode(200);
    }

    @Test
    void TC_P45_11_docx_headers_estructura_basica_es_docx_valido() throws Exception {
        // sanity: los primeros bytes de un .docx son siempre la firma ZIP "PK\x03\x04"
        String token = AuthSupport.registrarConToken(mailbox, "p45t7@ex.com");
        String proyectoId = crearProyecto(token, "Sanidad ZIP", (short) 2026);
        String presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(token, presupuestoId, "ZIP-001", "Cabecera", "u");
        ponerEt(token, apuId, "ET que valida la firma PK del contenedor OOXML.");

        Response r = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/documentos/especificaciones-tecnicas/" + presupuestoId + "?formato=docx");
        assertEquals(200, r.statusCode());
        byte[] bytes = r.body().asByteArray();
        assertTrue(bytes.length >= 4, "el DOCX debe tener al menos la firma ZIP local");
        assertEquals((byte) 'P', bytes[0], "firma ZIP[0]");
        assertEquals((byte) 'K', bytes[1], "firma ZIP[1]");
        assertEquals(0x03, bytes[2], "firma ZIP[2]");
        assertEquals(0x04, bytes[3], "firma ZIP[3]");

        // además POI debe poder parsearlo sin lanzar excepción
        try (XWPFDocument doc = parsearDocx(bytes)) {
            assertTrue(
                    doc.getParagraphs().size() >= 4,
                    "el DOCX debe tener al menos los 4 párrafos de cabecera + sección + pie");
            assertFalse(doc.getParagraphs().isEmpty(), "XWPFDocument debe contener párrafos parseados");
        }
    }
}
