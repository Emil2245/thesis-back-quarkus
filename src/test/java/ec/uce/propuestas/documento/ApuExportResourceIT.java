package ec.uce.propuestas.documento;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ApuExportResourceIT {
    @Inject
    DataSource ds;

    @Inject
    RecordingEnviadorCorreo mailbox;

    static String path(PresupuestoExportResourceIT.Fixture f) {
        return f.path().replace("/presupuesto/", "/apus/");
    }

    @Test
    void selectedHistoricalVersionHasRealPdfAndBothLayouts() throws Exception {
        var f = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
        try (var c = ds.getConnection()) {
            PresupuestoExportResourceIT.sql(
                    c,
                    "insert into apu(presupuesto_id,codigo,descripcion,unidad) values (?,'UNUSED','Catalogue secret','u')",
                    f.budget());
        }
        for (String query : new String[] {
            "formato=xlsx", "formato=xlsx&layout=pestanas", "formato=xlsx&layout=apilado", "formato=pdf"
        }) {
            boolean pdf = query.equals("formato=pdf");
            var response = given().auth()
                    .oauth2(f.token())
                    .get(path(f) + "?" + query)
                    .then()
                    .statusCode(200)
                    .contentType(pdf ? ArchivoGenerado.PDF_MEDIA_TYPE : ArchivoGenerado.XLSX_MEDIA_TYPE)
                    .extract()
                    .response();
            String header = response.header("Content-Disposition");
            assertTrue(header.contains(f.id() + "-v2."));
            assertFalse(header.contains("\r"));
            assertFalse(header.contains("\n"));
            String text;
            if (pdf) {
                try (var doc = Loader.loadPDF(response.asByteArray())) {
                    assertEquals(595, doc.getPage(0).getMediaBox().getWidth(), 1);
                    assertEquals(842, doc.getPage(0).getMediaBox().getHeight(), 1);
                    text = new PDFTextStripper().getText(doc);
                }
            } else {
                try (var wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(response.asByteArray()))) {
                    assertEquals(1, wb.getNumberOfSheets());
                    text = ApuDescargaConcurrencyIT.text(response.asByteArray());
                }
            }
            assertTrue(text.contains("BUD02"));
            assertTrue(text.contains("Responsable anterior"));
            assertTrue(text.contains("99"));
            assertFalse(text.contains("Catalogue secret"));
        }
    }

    @Test
    void strictOptionsAndAuthentication() throws Exception {
        var f = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
        for (String query : new String[] {
            "",
            "formato=",
            "formato=PDF",
            "formato=pdf&formato=pdf",
            "formato=pdf&layout=pestanas",
            "formato=pdf&orientacion=vertical",
            "formato=pdf&papel=a4",
            "formato=xlsx&layout=",
            "formato=xlsx&layout=other",
            "formato=xlsx&layout=pestanas&layout=pestanas",
            "formato=xlsx&unknown=1"
        }) {
            given().auth()
                    .oauth2(f.token())
                    .get(path(f) + (query.isEmpty() ? "" : "?" + query))
                    .then()
                    .statusCode(400);
        }
        given().get(path(f) + "?formato=pdf").then().statusCode(401);
        given().auth()
                .oauth2(f.token())
                .get(path(f).replace(f.id().toString(), "bad") + "?formato=pdf")
                .then()
                .statusCode(400);
        var other = PresupuestoExportResourceIT.fixture(ds, mailbox, false);
        String foreign = given().auth()
                .oauth2(other.token())
                .get(path(f) + "?formato=pdf")
                .then()
                .statusCode(404)
                .extract()
                .asString();
        String absent = given().auth()
                .oauth2(other.token())
                .get(path(f).replace(f.id().toString(), "01900000-0000-7000-8000-000000000000") + "?formato=pdf")
                .then()
                .statusCode(404)
                .extract()
                .asString();
        assertEquals(absent, foreign);
        try (var c = ds.getConnection()) {
            PresupuestoExportResourceIT.sql(c, "update usuario set rol='SUPER_ADMIN' where id=?", other.caller());
            PresupuestoExportResourceIT.sql(
                    c,
                    "update proyecto set nombre_proyecto=? where id=?",
                    "../\\\"\r\nInjected/" + "Z".repeat(100),
                    f.project());
        }
        String admin = given().contentType("application/json")
                .body(java.util.Map.of("email", other.email(), "password", "Pass1234", "recordarSesion", false))
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
        given().auth().oauth2(admin).get(path(f) + "?formato=pdf").then().statusCode(404);
        var response = given().auth()
                .oauth2(f.token())
                .header("Accept", ArchivoGenerado.PDF_MEDIA_TYPE)
                .get(path(f) + "?formato=pdf")
                .then()
                .statusCode(200)
                .extract()
                .response();
        assertTrue(response.header("Content-Disposition")
                .matches("attachment; filename=\"apus-[A-Za-z0-9_-]{0,48}-" + f.id() + "-v2\\.pdf\""));
    }

    @Test
    void foreignApuReferenceFailsClosedWithoutDisclosure() throws Exception {
        var selected = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
        var foreign = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
        try (var c = ds.getConnection()) {
            PresupuestoExportResourceIT.sql(
                    c,
                    "insert into apu(presupuesto_id,codigo,descripcion,unidad) values (?,'FOREIGN-SECRET','Foreign secret','u')",
                    foreign.budget());
            PresupuestoExportResourceIT.sql(
                    c,
                    "update rubro set apu_id=(select id from apu where presupuesto_id=? and codigo='FOREIGN-SECRET') where capitulo_id in (select id from capitulo where presupuesto_id=?)",
                    foreign.budget(),
                    selected.budget());
        }
        for (String format : new String[] {"xlsx", "pdf"}) {
            var response = given().auth()
                    .oauth2(selected.token())
                    .get(path(selected) + "?formato=" + format)
                    .then()
                    .statusCode(409)
                    .contentType("application/json")
                    .body("codigo", org.hamcrest.Matchers.equalTo("export-inconsistente"))
                    .extract()
                    .response();
            assertNull(response.header("Content-Disposition"));
            assertFalse(response.asString().contains(foreign.id().toString()));
        }
    }

    @Test
    void foreignInsumoFailsClosedWithoutDisclosure() throws Exception {
        var selected = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
        var foreign = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
        try (var c = ds.getConnection()) {
            PresupuestoExportResourceIT.sql(
                    c,
                    "with b as (insert into base_insumos(nombre,tipo,proyecto_id) values ('FOREIGN','PROYECTO',?) returning id) insert into insumo(base_id,codigo,tipo,descripcion,unidad,precio_unitario) select id,'SECRET','MATERIAL','Private input','u',10 from b",
                    foreign.project());
            PresupuestoExportResourceIT.sql(
                    c,
                    "insert into apu_seccion(apu_id,tipo,subtotal,orden) select id,'MATERIAL',10,3 from apu where presupuesto_id=?",
                    selected.budget());
            PresupuestoExportResourceIT.sql(
                    c,
                    "insert into apu_detalle(seccion_id,insumo_id,descripcion,orden,cantidad,unidad,costo) select s.id,i.id,'Private input',1,1,'u',10 from apu_seccion s join apu a on a.id=s.apu_id join insumo i on i.codigo='SECRET' join base_insumos b on b.id=i.base_id where a.presupuesto_id=? and b.proyecto_id=?",
                    selected.budget(),
                    foreign.project());
        }
        for (String format : new String[] {"xlsx", "pdf"}) {
            var response = given().auth()
                    .oauth2(selected.token())
                    .get(path(selected) + "?formato=" + format)
                    .then()
                    .statusCode(409)
                    .contentType("application/json")
                    .body("codigo", org.hamcrest.Matchers.equalTo("export-inconsistente"))
                    .extract()
                    .response();
            assertNull(response.header("Content-Disposition"));
            assertFalse(response.asString().contains("Private input"));
            assertFalse(response.asString().contains(foreign.id().toString()));
        }
    }

    static void blocked(PresupuestoExportResourceIT.Fixture f, String code) {
        for (String format : new String[] {"xlsx", "pdf"}) {
            var response = given().auth()
                    .oauth2(f.token())
                    .get(path(f) + "?formato=" + format)
                    .then()
                    .statusCode(409)
                    .contentType("application/json")
                    .body(
                            "exportable",
                            org.hamcrest.Matchers.equalTo(false),
                            "bloqueos.codigo",
                            org.hamcrest.Matchers.hasItem(code))
                    .extract()
                    .response();
            assertNull(response.header("Content-Disposition"));
            assertTrue(response.asString().startsWith("{"));
        }
    }

    @Test
    void emptyAndP32NeverReturnAttachment() throws Exception {
        blocked(PresupuestoExportResourceIT.fixture(ds, mailbox, false), "apus-vacios");
        blocked(PresupuestoExportResourceIT.fixture(ds, mailbox, true, false), "presupuesto-sin-actividad");
        var f = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
        try (var c = ds.getConnection()) {
            PresupuestoExportResourceIT.sql(
                    c,
                    "update rubro set cantidad=0,precio_unitario=0 where capitulo_id in (select id from capitulo where presupuesto_id=?)",
                    f.budget());
        }
        blocked(f, "presupuesto-pu-cero");
        blocked(f, "presupuesto-cantidad-cero");
    }
}
