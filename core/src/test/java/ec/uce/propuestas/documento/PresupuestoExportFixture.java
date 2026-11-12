package ec.uce.propuestas.documento;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import java.sql.Connection;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;

/** Selected historical budget version fixture shared by the POST export ITs (was in the retired GET IT). */
final class PresupuestoExportFixture {
    private PresupuestoExportFixture() {}

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
}
