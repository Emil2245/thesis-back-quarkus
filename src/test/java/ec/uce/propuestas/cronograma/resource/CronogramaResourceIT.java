package ec.uce.propuestas.cronograma.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class CronogramaResourceIT {

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
                        "nombreProyecto", "Proyecto Cronograma",
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

    @Test
    void crear_cronograma_auto_importa_actividades() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "crono-user@uce.edu.ec");
        String proyectoPublicId = crearProyecto(token);
        Long presId = insertarPresupuesto(proyectoPublicId);
        String presPublicId = presupuestoPublicId(presId);

        String matId = crearInsumo(token, proyectoPublicId, "MAT-C1", "MATERIAL", "Cemento", 10.0);
        String apuPublicId = crearApu(token, presPublicId, "APU-C1", "Hormigón");
        agregarDetalleApu(token, apuPublicId, "MATERIAL", matId, 2.0);
        Long apuInternalId = internalId("apu", apuPublicId);
        Long capId = crearCapitulo(token, presId, "Obras");
        crearRubro(token, presId, capId, apuInternalId, 5.0);

        Number cronogramaId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "MES", "numeroPeriodos", 6))
                .when()
                .post("/api/v1/presupuestos/" + presId + "/cronograma")
                .then()
                .statusCode(201)
                .body("presupuestoId", is(presId.intValue()))
                .body("unidadTiempo", equalTo("MES"))
                .body("numeroPeriodos", is(6))
                .body("actividades.size()", is(1))
                .body("actividades[0].descripcion", equalTo("Hormigón"))
                .extract()
                .path("id");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presId + "/cronograma")
                .then()
                .statusCode(200)
                .body("id", is(cronogramaId.intValue()))
                .body("actividades.size()", is(1));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 12))
                .when()
                .post("/api/v1/presupuestos/" + presId + "/cronograma")
                .then()
                .statusCode(409);
    }

    @Test
    void actualizar_avance_y_configurar_periodos() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "crono-avance@uce.edu.ec");
        String proyectoPublicId = crearProyecto(token);
        Long presId = insertarPresupuesto(proyectoPublicId);
        String presPublicId = presupuestoPublicId(presId);

        String matId = crearInsumo(token, proyectoPublicId, "MAT-A1", "MATERIAL", "Varilla", 5.0);
        String apuPublicId = crearApu(token, presPublicId, "APU-A1", "Estructura");
        agregarDetalleApu(token, apuPublicId, "MATERIAL", matId, 3.0);
        Long apuInternalId = internalId("apu", apuPublicId);
        Long capId = crearCapitulo(token, presId, "Estructuras");
        crearRubro(token, presId, capId, apuInternalId, 10.0);

        var resp = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "MES", "numeroPeriodos", 4))
                .when()
                .post("/api/v1/presupuestos/" + presId + "/cronograma")
                .then()
                .statusCode(201)
                .extract();
        Long cronogramaId = ((Number) resp.path("id")).longValue();
        Long actividadId = ((Number) resp.path("actividades[0].id")).longValue();

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("avancePorPeriodo", Map.of("1", 0.25, "2", 0.25, "3", 0.25, "4", 0.25)))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(200)
                .body("actividades[0].avancePorPeriodo.size()", is(4))
                .body("actividades[0].desviacion", notNullValue())
                .body("avancePorPeriodo.size()", is(4))
                .body("avanceAcumulado.size()", is(4));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("avancePorPeriodo", Map.of("5", 0.10)))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(400);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("numeroPeriodos", 2))
                .when()
                .put("/api/v1/cronogramas/" + cronogramaId)
                .then()
                .statusCode(409);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("numeroPeriodos", 2, "confirmarPerdida", true))
                .when()
                .put("/api/v1/cronogramas/" + cronogramaId)
                .then()
                .statusCode(200)
                .body("numeroPeriodos", is(2))
                .body("actividades[0].avancePorPeriodo.size()", is(2));
    }

    @Test
    void marcar_revisado_y_desactualizado() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "crono-rev@uce.edu.ec");
        String proyectoPublicId = crearProyecto(token);
        Long presId = insertarPresupuesto(proyectoPublicId);
        String presPublicId = presupuestoPublicId(presId);

        Number cronogramaId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 8))
                .when()
                .post("/api/v1/presupuestos/" + presId + "/cronograma")
                .then()
                .statusCode(201)
                .body("desactualizado", is(false))
                .extract()
                .path("id");

        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/cronogramas/" + cronogramaId + "/revisado")
                .then()
                .statusCode(200)
                .body("totalGeneralRevisado", notNullValue())
                .body("fechaRevision", notNullValue())
                .body("desactualizado", is(false));

        String matId = crearInsumo(token, proyectoPublicId, "MAT-R1", "MATERIAL", "Arena", 8.0);
        String apuPublicId = crearApu(token, presPublicId, "APU-R1", "Relleno");
        agregarDetalleApu(token, apuPublicId, "MATERIAL", matId, 1.0);
        Long apuInternalId = internalId("apu", apuPublicId);
        Long capId = crearCapitulo(token, presId, "Rellenos");
        crearRubro(token, presId, capId, apuInternalId, 10.0);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presId + "/cronograma")
                .then()
                .statusCode(200)
                .body("desactualizado", is(true));
    }
}
