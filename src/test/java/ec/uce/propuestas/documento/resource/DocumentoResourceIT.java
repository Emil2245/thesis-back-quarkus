package ec.uce.propuestas.documento.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
            st.execute("TRUNCATE TABLE actividad, cronograma, apu_detalle, apu_seccion, apu, rubro, capitulo, "
                    + "presupuesto, insumo, base_insumos, parametros_proyecto, firmante, proyecto, "
                    + "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    private String crearProyecto(String token) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto", "Proyecto Export",
                        "anio", (short) 2026,
                        "plazoEjecucion", (short) 6,
                        "plazoUnidad", "MES",
                        "direccionInstitucional", "Quito"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private Long internalId(String table, String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM " + table + " WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(publicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private Long insertarPresupuesto(String proyectoPublicId) throws Exception {
        Long proyectoId = internalId("proyecto", proyectoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO presupuesto (proyecto_id, version, es_vigente) VALUES (?, 1, TRUE) RETURNING id")) {
            ps.setLong(1, proyectoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private String presupuestoPublicId(Long presupuestoId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT public_id FROM presupuesto WHERE id = ?")) {
            ps.setLong(1, presupuestoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private String crearInsumo(
            String token, String proyectoPublicId, String codigo, String tipo, String desc, double precio) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "codigo", codigo,
                        "tipo", tipo,
                        "descripcion", desc,
                        "unidad", "u",
                        "precioUnitario", precio))
                .when()
                .post("/api/v1/proyectos/" + proyectoPublicId + "/insumos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String crearApu(String token, String presupuestoPublicId, String codigo, String desc) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("codigo", codigo, "descripcion", desc, "unidad", "u"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoPublicId + "/apus")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private void agregarDetalleApu(
            String token, String apuPublicId, String tipo, String insumoPublicId, double cantidad) {
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("seccionTipo", tipo, "insumoId", insumoPublicId, "cantidad", cantidad))
                .when()
                .post("/api/v1/apus/" + apuPublicId + "/detalles")
                .then()
                .statusCode(201);
    }

    private Long crearCapitulo(String token, Long presupuestoId, String desc) {
        return ((Number) given().contentType(JSON)
                        .header("Authorization", "Bearer " + token)
                        .body(Map.of("descripcion", desc))
                        .when()
                        .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("capitulos[0].id"))
                .longValue();
    }

    private void crearRubro(String token, Long presupuestoId, Long capituloId, Long apuInternalId, double cantidad) {
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuInternalId, "cantidad", cantidad))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capituloId + "/rubros")
                .then()
                .statusCode(201);
    }

    private record Scenario(
            String token,
            String proyectoPublicId,
            Long presupuestoId,
            String presupuestoPublicId,
            String apuPublicId,
            Long apuInternalId) {}

    private Scenario setupExportable() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "doc-export@uce.edu.ec");
        String proyectoPublicId = crearProyecto(token);
        Long presId = insertarPresupuesto(proyectoPublicId);
        String presPublicId = presupuestoPublicId(presId);
        String matId = crearInsumo(token, proyectoPublicId, "MAT-D1", "MATERIAL", "Cemento", 10.0);
        String apuPublicId = crearApu(token, presPublicId, "APU-D1", "Hormigón");
        agregarDetalleApu(token, apuPublicId, "MATERIAL", matId, 2.0);
        Long apuInternalId = internalId("apu", apuPublicId);
        Long capId = crearCapitulo(token, presId, "Obras");
        crearRubro(token, presId, capId, apuInternalId, 5.0);
        return new Scenario(token, proyectoPublicId, presId, presPublicId, apuPublicId, apuInternalId);
    }

    @Test
    void export_apu_xlsx_returns_valid_workbook() throws Exception {
        Scenario s = setupExportable();

        byte[] data = given().header("Authorization", "Bearer " + s.token)
                .when()
                .get("/api/v1/documentos/apu/" + s.apuInternalId)
                .then()
                .statusCode(200)
                .header("Content-Disposition", containsString("attachment"))
                .extract()
                .asByteArray();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            assertEquals(1, wb.getNumberOfSheets());
            assertTrue(wb.getSheetAt(0).getSheetName().startsWith("APU "));
        }
    }

    @Test
    void export_especificaciones_docx_returns_valid_document() throws Exception {
        Scenario s = setupExportable();

        given().contentType(JSON)
                .header("Authorization", "Bearer " + s.token)
                .body(Map.of("texto", "Requisitos de calidad para hormigón."))
                .when()
                .put("/api/v1/apus/" + s.apuPublicId + "/especificacion-tecnica")
                .then()
                .statusCode(200);

        byte[] data = given().header("Authorization", "Bearer " + s.token)
                .when()
                .get("/api/v1/documentos/especificaciones-tecnicas/" + s.presupuestoPublicId)
                .then()
                .statusCode(200)
                .header("Content-Disposition", containsString("attachment"))
                .extract()
                .asByteArray();

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(data))) {
            assertTrue(doc.getParagraphs().size() >= 3, "Should have title paragraphs + at least one APU section");
        }
    }

    @Test
    void export_presupuesto_xlsx_returns_valid_workbook() throws Exception {
        Scenario s = setupExportable();

        byte[] data = given().header("Authorization", "Bearer " + s.token)
                .when()
                .get("/api/v1/documentos/presupuesto/" + s.presupuestoId)
                .then()
                .statusCode(200)
                .header("Content-Disposition", containsString("Presupuesto"))
                .extract()
                .asByteArray();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(data))) {
            assertEquals("Presupuesto", wb.getSheetAt(0).getSheetName());
        }
    }

    @Test
    void display_config_returns_precision_values() {
        String token = AuthSupport.registrarConToken(mailbox, "config-user@uce.edu.ec");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/config/display")
                .then()
                .statusCode(200)
                .body("precisionDinero", is(2))
                .body("precisionPorcentaje", is(4));
    }
}
