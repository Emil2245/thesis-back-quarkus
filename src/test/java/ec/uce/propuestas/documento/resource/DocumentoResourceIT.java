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
import java.sql.Statement;
import java.util.Map;
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

    private Long crearProyecto(String token) {
        return ((Number) given().contentType(JSON)
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
                        .path("id"))
                .longValue();
    }

    private Long obtenerPresupuestoVigente(String token, Long proyectoId) {
        return ((Number) given().header("Authorization", "Bearer " + token)
                        .when()
                        .get("/api/v1/proyectos/" + proyectoId + "/presupuestos")
                        .then()
                        .statusCode(200)
                        .body("size()", greaterThan(0))
                        .extract()
                        .path("[0].presupuestoId"))
                .longValue();
    }

    private Long crearInsumo(String token, Long proyectoId, String codigo, String tipo, String desc, double precio) {
        return ((Number) given().contentType(JSON)
                        .header("Authorization", "Bearer " + token)
                        .body(Map.of(
                                "codigo", codigo,
                                "tipo", tipo,
                                "descripcion", desc,
                                "unidad", "u",
                                "precioUnitario", precio))
                        .when()
                        .post("/api/v1/proyectos/" + proyectoId + "/insumos")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id"))
                .longValue();
    }

    private Long crearApu(String token, Long presupuestoId, String codigo, String desc) {
        return ((Number) given().contentType(JSON)
                        .header("Authorization", "Bearer " + token)
                        .body(Map.of("codigo", codigo, "descripcion", desc, "unidad", "u"))
                        .when()
                        .post("/api/v1/presupuestos/" + presupuestoId + "/apus")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id"))
                .longValue();
    }

    private void agregarDetalleApu(String token, Long apuId, String tipo, Long insumoId, double cantidad) {
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("seccionTipo", tipo, "insumoId", insumoId, "cantidad", cantidad))
                .when()
                .post("/api/v1/apus/" + apuId + "/detalles")
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

    private void crearRubro(String token, Long presupuestoId, Long capituloId, Long apuId, double cantidad) {
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId, "cantidad", cantidad))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capituloId + "/rubros")
                .then()
                .statusCode(201);
    }

    /** Setup: register user, create project with 1 APU+rubro so presupuesto is exportable. */
    private record Scenario(String token, Long proyectoId, Long presupuestoId, Long apuId) {}

    private Scenario setupExportable() {
        String token = AuthSupport.registrarConToken(mailbox, "doc-export@uce.edu.ec");
        Long proyectoId = crearProyecto(token);
        Long presId = obtenerPresupuestoVigente(token, proyectoId);
        Long matId = crearInsumo(token, proyectoId, "MAT-D1", "MATERIAL", "Cemento", 10.0);
        Long apuId = crearApu(token, presId, "APU-D1", "Hormigón");
        agregarDetalleApu(token, apuId, "MATERIAL", matId, 2.0);
        Long capId = crearCapitulo(token, presId, "Obras");
        crearRubro(token, presId, capId, apuId, 5.0);
        return new Scenario(token, proyectoId, presId, apuId);
    }

    @Test
    void export_apu_xlsx_returns_valid_workbook() throws Exception {
        Scenario s = setupExportable();

        byte[] data = given().header("Authorization", "Bearer " + s.token)
                .when()
                .get("/api/v1/documentos/apu/" + s.apuId)
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

        // Set especificacion tecnica on the APU
        given().contentType(JSON)
                .header("Authorization", "Bearer " + s.token)
                .body(Map.of("texto", "Requisitos de calidad para hormigón."))
                .when()
                .put("/api/v1/apus/" + s.apuId + "/especificacion-tecnica")
                .then()
                .statusCode(200);

        byte[] data = given().header("Authorization", "Bearer " + s.token)
                .when()
                .get("/api/v1/documentos/especificaciones-tecnicas/" + s.presupuestoId)
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
