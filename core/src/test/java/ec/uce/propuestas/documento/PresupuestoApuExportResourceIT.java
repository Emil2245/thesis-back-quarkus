package ec.uce.propuestas.documento;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PresupuestoApuExportResourceIT {
    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @Inject
    ec.uce.propuestas.cronograma.export.CronogramaExportPreflightService stale;

    @Inject
    ec.uce.propuestas.presupuesto.repository.PresupuestoRepository budgets;

    @Inject
    ec.uce.propuestas.cronograma.repository.CronogramaRepository schedules;

    @Test
    void emptyBudgetAndStrictOptions() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "exp03-" + System.nanoTime() + "@test.ec");
        String project = given().contentType("application/json")
                .auth()
                .oauth2(token)
                .body(Map.of(
                        "nombreProyecto",
                        "EXP03",
                        "codigo",
                        "E" + System.nanoTime(),
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
        String budget;
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "select p.public_id from presupuesto p join proyecto pr on pr.id=p.proyecto_id where pr.public_id=?")) {
            p.setObject(1, java.util.UUID.fromString(project));
            try (ResultSet r = p.executeQuery()) {
                r.next();
                budget = r.getString(1);
            }
        }
        for (String document : new String[] {"presupuesto", "apus"}) {
            String path = "/api/v1/documentos/" + document + "/" + budget + "/preflight";
            given().auth()
                    .oauth2(token)
                    .get(path + "?formato=xlsx")
                    .then()
                    .statusCode(200)
                    .body(
                            "presupuestoId",
                            equalTo(budget),
                            "exportable",
                            equalTo(false),
                            "bloqueos.codigo",
                            hasItem("presupuesto-vacio"));
            for (String query : new String[] {
                "",
                "formato=",
                "formato=pdf&formato=pdf",
                "formato=mspdi",
                "formato=xlsx&orientacion=vertical",
                "formato=pdf&layout=pestanas",
                "formato=pdf&papel=a4",
                "formato=xlsx&extra=1",
                "formato=PDF",
                "formato=%20pdf",
                "formato=pdf&orientacion=",
                "formato=xlsx&layout=",
                "formato=pdf&orientacion=vertical&orientacion=vertical",
                "formato=xlsx&layout=pestanas&layout=pestanas"
            }) {
                given().auth()
                        .oauth2(token)
                        .get(path + (query.isEmpty() ? "" : "?" + query))
                        .then()
                        .statusCode(400)
                        .body("codigo", equalTo("validacion"));
            }
            given().auth()
                    .oauth2(token)
                    .get(path.replace(budget, "bad") + "?formato=pdf")
                    .then()
                    .statusCode(400);
            given().auth()
                    .oauth2(token)
                    .get(path.replace(budget, "550e8400-e29b-41d4-a716-446655440000") + "?formato=pdf")
                    .then()
                    .statusCode(400);
            given().get(path + "?formato=pdf").then().statusCode(401);
            given().auth()
                    .oauth2(token)
                    .get(path.replace(budget, "01900000-0000-7000-8000-000000000000") + "?formato=pdf")
                    .then()
                    .statusCode(404);
            given().auth()
                    .oauth2(token)
                    .get(path.replace("/preflight", "") + "?formato=pdf")
                    .then()
                    .statusCode(409);
            String otherEmail = "other-" + System.nanoTime() + "@test.ec";
            String other = AuthSupport.registrarConToken(mailbox, otherEmail);
            String absent = given().auth()
                    .oauth2(other)
                    .get(path + "?formato=pdf")
                    .then()
                    .statusCode(404)
                    .extract()
                    .asString();
            org.junit.jupiter.api.Assertions.assertEquals(
                    absent,
                    given().auth()
                            .oauth2(other)
                            .get(path.replace(budget, "01900000-0000-7000-8000-000000000000") + "?formato=pdf")
                            .then()
                            .statusCode(404)
                            .extract()
                            .asString());
            try (Connection c = ds.getConnection();
                    PreparedStatement p = c.prepareStatement("update usuario set rol='SUPER_ADMIN' where email=?")) {
                p.setString(1, otherEmail);
                p.executeUpdate();
            }
            String admin = given().contentType("application/json")
                    .body(Map.of("email", otherEmail, "password", "Pass1234", "recordarSesion", false))
                    .post("/api/v1/auth/login")
                    .then()
                    .statusCode(200)
                    .extract()
                    .path("accessToken");
            given().auth().oauth2(admin).get(path + "?formato=pdf").then().statusCode(404);
            given().auth()
                    .oauth2(token)
                    .get(path + "?formato=pdf")
                    .then()
                    .statusCode(200)
                    .body(
                            "opciones",
                            document.equals("presupuesto") ? hasEntry("orientacion", "vertical") : anEmptyMap());
            if (document.equals("apus")) {
                given().auth()
                        .oauth2(token)
                        .get(path + "?formato=xlsx&layout=apilado")
                        .then()
                        .statusCode(200)
                        .body("opciones.layout", equalTo("apilado"), "bloqueos.codigo", hasItem("apus-vacios"));
            } else {
                given().auth()
                        .oauth2(token)
                        .get(path + "?formato=pdf&orientacion=horizontal")
                        .then()
                        .statusCode(200)
                        .body("opciones.orientacion", equalTo("horizontal"));
            }
        }
    }

    @Test
    void selectedNonCurrentVersionUsesP32NotDistributionAndStaleIsReadOnly() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "selected-" + System.nanoTime() + "@test.ec");
        String project = given().contentType("application/json")
                .auth()
                .oauth2(token)
                .body(Map.of(
                        "nombreProyecto",
                        "Selected",
                        "codigo",
                        "S" + System.nanoTime(),
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
        long id;
        String publicId;
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "insert into presupuesto(proyecto_id,version,es_vigente,total) select id,2,false,10 from proyecto where public_id=? returning id,public_id")) {
            p.setObject(1, java.util.UUID.fromString(project));
            try (ResultSet r = p.executeQuery()) {
                r.next();
                id = r.getLong(1);
                publicId = r.getString(2);
            }
        }
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "with cap as (insert into capitulo(presupuesto_id,item,descripcion,orden,total) values (?, '1','C',1,10) returning id), "
                                + "a as (insert into apu(presupuesto_id,codigo,descripcion,unidad,costo_directo,costo_total) values (?, 'USED','A','u',10,10) returning id) "
                                + "insert into rubro(capitulo_id,apu_id,item,codigo,descripcion,unidad,cantidad,precio_unitario,precio_total) select cap.id,a.id,'1.1','USED','R','u',1,10,10 from cap,a")) {
            p.setLong(1, id);
            p.setLong(2, id);
            p.executeUpdate();
        }
        String path = "/api/v1/documentos/apus/" + publicId + "/preflight?formato=xlsx";
        given().auth()
                .oauth2(token)
                .get(path)
                .then()
                .statusCode(200)
                .body(
                        "version",
                        equalTo(2),
                        "exportable",
                        equalTo(false),
                        "bloqueos.codigo",
                        hasItem("presupuesto-sin-actividad"));
        given().contentType("application/json")
                .auth()
                .oauth2(token)
                .body(Map.of("unidadTiempo", "MES", "numeroPeriodos", 6))
                .post("/api/v1/presupuestos/" + publicId + "/cronograma")
                .then()
                .statusCode(201);
        given().auth()
                .oauth2(token)
                .get(path)
                .then()
                .statusCode(200)
                .body("exportable", equalTo(true), "bloqueos", hasSize(0), "warnings", hasSize(0));
        // Unlinked catalogue APU does not participate; modifying a rubro produces only canonical warnings/P-32.
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "insert into apu(presupuesto_id,codigo,descripcion,unidad) values (?, 'UNUSED','Unlinked','u')")) {
            p.setLong(1, id);
            p.executeUpdate();
        }
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement("update presupuesto set total=11 where id=?")) {
            p.setLong(1, id);
            p.executeUpdate();
        }
        given().auth()
                .oauth2(token)
                .get(path)
                .then()
                .statusCode(200)
                .body("exportable", equalTo(true), "warnings.codigo", hasItem("cronograma-desactualizado"));
        var budget = budgets.findById(id);
        var schedule = schedules.find("presupuestoId", id).firstResult();
        var snapshot = new ec.uce.propuestas.cronograma.export.CronogramaExportPreflightService.SnapshotCompleto(
                schedule, budget, null, budget.total, null, java.util.List.of(), false, false, false, null);
        org.junit.jupiter.api.Assertions.assertEquals(stale.esStale(snapshot), stale.esStale(schedule, budget));
        org.junit.jupiter.api.Assertions.assertEquals(
                new java.math.BigDecimal("10.000000"), schedule.totalGeneralRevisado);
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "update rubro set cantidad=0,precio_unitario=0 where apu_id in (select id from apu where presupuesto_id=?)")) {
            p.setLong(1, id);
            p.executeUpdate();
        }
        given().auth()
                .oauth2(token)
                .get(path)
                .then()
                .statusCode(200)
                .body(
                        "exportable",
                        equalTo(false),
                        "bloqueos.codigo",
                        hasItems("presupuesto-pu-cero", "presupuesto-cantidad-cero"));
    }
}
