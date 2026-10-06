package ec.uce.propuestas.documento;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PresupuestoExportResourceIT {
    @Inject
    DataSource ds;

    @Inject
    RecordingEnviadorCorreo mailbox;

    record Fixture(String token, String email, UUID id, long budget, long project, long caller) {
        String path() {
            return "/api/v1/documentos/presupuesto/" + id;
        }
    }

    static void sql(Connection c, String text, Object... args) throws Exception {
        try (var p = c.prepareStatement(text)) {
            for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
            p.executeUpdate();
        }
    }

    static Fixture fixture(DataSource ds, RecordingEnviadorCorreo mailbox, boolean populated) throws Exception {
        return fixture(ds, mailbox, populated, populated);
    }

    static Fixture fixture(DataSource ds, RecordingEnviadorCorreo mailbox, boolean populated, boolean schedule)
            throws Exception {
        String email = "bud02-" + System.nanoTime() + "@test.ec";
        String token = AuthSupport.registrarConToken(mailbox, email);
        String project = given().contentType("application/json")
                .auth()
                .oauth2(token)
                .body(Map.of(
                        "nombreProyecto",
                        "Proyecto anterior",
                        "codigo",
                        "B" + System.nanoTime(),
                        "anio",
                        2026,
                        "plazoEjecucion",
                        6,
                        "plazoUnidad",
                        "MES",
                        "direccionInstitucional",
                        "UCE"))
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        Fixture f;
        try (var c = ds.getConnection()) {
            c.setAutoCommit(false);
            // Selected historical version: the automatically created current version stays empty.
            try (var p = c.prepareStatement(
                    "insert into presupuesto(proyecto_id,version,es_vigente,total) select id,2,false,15.241470 from proyecto where public_id=? returning id,public_id,proyecto_id")) {
                p.setObject(1, UUID.fromString(project));
                try (var r = p.executeQuery()) {
                    assertTrue(r.next());
                    long caller;
                    try (var u = c.prepareStatement("select id from usuario where email=?")) {
                        u.setString(1, email);
                        try (var row = u.executeQuery()) {
                            assertTrue(row.next());
                            caller = row.getLong(1);
                        }
                    }
                    f = new Fixture(token, email, r.getObject(2, UUID.class), r.getLong(1), r.getLong(3), caller);
                }
            }
            sql(
                    c,
                    "insert into firmante(proyecto_id,nombre,cargo,rol,orden) values (?,'Responsable anterior','Ingeniero','APROBADO',1)",
                    f.project());
            if (populated) {
                sql(
                        c,
                        "with cap as (insert into capitulo(presupuesto_id,item,descripcion,orden,total) values (?,'1','Capitulo',1,15.241470) returning id), a as (insert into apu(presupuesto_id,codigo,descripcion,unidad,costo_directo,costo_total) values (?,'BUD02','APU','u',99,99) returning id) insert into rubro(capitulo_id,apu_id,item,codigo,descripcion,unidad,cantidad,precio_unitario,precio_total) select cap.id,a.id,'1.1','BUD02','Rubro anterior','u',1.2345,12.34,15.241470 from cap,a",
                        f.budget(),
                        f.budget());
            }
            c.commit();
        }
        if (schedule)
            given().contentType("application/json")
                    .auth()
                    .oauth2(token)
                    .body(Map.of("unidadTiempo", "MES", "numeroPeriodos", 6))
                    .post("/api/v1/presupuestos/" + f.id() + "/cronograma")
                    .then()
                    .statusCode(201);
        return f;
    }

    static byte[] download(Fixture f, String query, String mime) {
        var response = given().auth()
                .oauth2(f.token())
                .get(f.path() + "?" + query)
                .then()
                .statusCode(200)
                .contentType(mime)
                .extract()
                .response();
        String header = response.header("Content-Disposition");
        assertTrue(header.startsWith("attachment; filename=\""));
        assertTrue(header.contains(f.id().toString()));
        assertTrue(header.contains("-v2."));
        assertFalse(header.contains("\r"));
        assertFalse(header.contains("\n"));
        return response.asByteArray();
    }

    @Test
    void strictOptionsOwnershipAndSafeFilename() throws Exception {
        var f = fixture(ds, mailbox, true);
        for (String query : new String[] {
            "",
            "formato=",
            "formato=pdf&formato=pdf",
            "formato=unknown",
            "formato=PDF",
            "formato=%20pdf",
            "formato=pdf&orientacion=",
            "formato=pdf&orientacion=diagonal",
            "formato=pdf&orientacion=vertical&orientacion=vertical",
            "formato=xlsx&orientacion=vertical",
            "formato=pdf&layout=pestanas",
            "formato=xlsx&layout=apilado",
            "formato=pdf&papel=a4",
            "formato=xlsx&unknown=1"
        }) {
            given().auth()
                    .oauth2(f.token())
                    .get(f.path() + (query.isEmpty() ? "" : "?" + query))
                    .then()
                    .statusCode(400)
                    .contentType("application/json");
        }
        given().get(f.path() + "?formato=pdf").then().statusCode(401);
        given().auth()
                .oauth2(f.token())
                .get("/api/v1/documentos/presupuesto/bad?formato=pdf")
                .then()
                .statusCode(400)
                .body("codigo", org.hamcrest.Matchers.equalTo("validacion"));
        var other = fixture(ds, mailbox, false);
        given().auth()
                .oauth2(other.token())
                .get(f.path() + "?formato=pdf")
                .then()
                .statusCode(404);
        try (var c = ds.getConnection()) {
            sql(c, "update usuario set rol='SUPER_ADMIN' where id=?", other.caller());
            sql(
                    c,
                    "update proyecto set nombre_proyecto=? where id=?",
                    "CON../\\\"\r\nInjected: value\\evil",
                    f.project());
        }
        String admin = given().contentType("application/json")
                .body(Map.of("email", other.email(), "password", "Pass1234", "recordarSesion", false))
                .post("/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
        given().auth().oauth2(admin).get(f.path() + "?formato=pdf").then().statusCode(404);
        download(f, "formato=xlsx", ArchivoGenerado.XLSX_MEDIA_TYPE);
        download(f, "formato=pdf", ArchivoGenerado.PDF_MEDIA_TYPE);
        given().auth()
                .oauth2(f.token())
                .get(f.path().replace("/presupuesto/", "/apus/") + "?formato=pdf")
                .then()
                .statusCode(200)
                .contentType(ArchivoGenerado.PDF_MEDIA_TYPE);
    }

    @Test
    void emptyAndP32BlockWithJsonWithoutAttachment() throws Exception {
        var empty = fixture(ds, mailbox, false);
        blocked(empty, "presupuesto-vacio");
        var f = fixture(ds, mailbox, true);
        try (var c = ds.getConnection()) {
            sql(
                    c,
                    "update rubro set cantidad=0,precio_unitario=0 where capitulo_id in (select id from capitulo where presupuesto_id=?)",
                    f.budget());
        }
        blocked(f, "presupuesto-pu-cero");
        blocked(f, "presupuesto-cantidad-cero");
        var noActivity = fixture(ds, mailbox, true, false);
        blocked(noActivity, "presupuesto-sin-actividad");
    }

    @Test
    void inconsistentCrossOwnerLinkFailsClosed() throws Exception {
        var selected = fixture(ds, mailbox, true);
        var foreign = fixture(ds, mailbox, true);
        try (var c = ds.getConnection()) {
            sql(
                    c,
                    "insert into apu(presupuesto_id,codigo,descripcion,unidad) values (?,'FOREIGN-SECRET','Foreign secret','u')",
                    foreign.budget());
            sql(
                    c,
                    "update rubro set apu_id=(select id from apu where presupuesto_id=? and codigo='FOREIGN-SECRET') where capitulo_id in (select id from capitulo where presupuesto_id=?)",
                    foreign.budget(),
                    selected.budget());
        }
        for (String format : new String[] {"xlsx", "pdf"}) {
            var response = given().auth()
                    .oauth2(selected.token())
                    .get(selected.path() + "?formato=" + format)
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

    static void blocked(Fixture f, String code) {
        for (String format : new String[] {"xlsx", "pdf"}) {
            var response = given().auth()
                    .oauth2(f.token())
                    .get(f.path() + "?formato=" + format)
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
    void realSelectedHistoricalDownloads() throws Exception {
        var f = fixture(ds, mailbox, true);
        byte[] xlsx = download(f, "formato=xlsx", ArchivoGenerado.XLSX_MEDIA_TYPE);
        try (var wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
            boolean quantity = false, pu = false, pt = false;
            StringBuilder text = new StringBuilder();
            for (var row : wb.getSheetAt(0))
                for (var cell : row) {
                    if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                        BigDecimal value = new BigDecimal(((org.apache.poi.xssf.usermodel.XSSFCell) cell)
                                .getCTCell()
                                .getV());
                        quantity |= value.compareTo(new BigDecimal("1.2345")) == 0;
                        pu |= value.compareTo(new BigDecimal("12.34")) == 0;
                        pt |= value.compareTo(new BigDecimal("15.241470")) == 0;
                        assertNotEquals(0, value.compareTo(new BigDecimal("99")));
                    } else text.append(cell.toString()).append('\n');
                }
            assertTrue(quantity && pu && pt);
            assertTrue(text.toString().contains("Proyecto anterior"));
            assertTrue(text.toString().contains("Responsable anterior"));
        }
        for (boolean landscape : new boolean[] {false, true}) {
            byte[] pdf = download(
                    f, "formato=pdf" + (landscape ? "&orientacion=horizontal" : ""), ArchivoGenerado.PDF_MEDIA_TYPE);
            try (var doc = Loader.loadPDF(pdf)) {
                var box = doc.getPage(0).getMediaBox();
                assertEquals(landscape ? 842 : 595, box.getWidth(), 1);
                assertEquals(landscape ? 595 : 842, box.getHeight(), 1);
                String text = new PDFTextStripper().getText(doc);
                assertTrue(text.contains("Proyecto anterior"));
                assertTrue(text.contains("Responsable anterior"));
                assertTrue(text.contains("12.34"));
            }
        }
    }
}
