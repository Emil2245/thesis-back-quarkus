package ec.uce.propuestas.cronograma.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
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

    private Long crearProyecto(String token) {
        return ((Number) given().contentType(JSON)
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

    @Test
    void crear_cronograma_auto_importa_actividades() {
        String token = AuthSupport.registrarConToken(mailbox, "crono-user@uce.edu.ec");
        Long proyectoId = crearProyecto(token);
        Long presId = obtenerPresupuestoVigente(token, proyectoId);

        Long matId = crearInsumo(token, proyectoId, "MAT-C1", "MATERIAL", "Cemento", 10.0);
        Long apuId = crearApu(token, presId, "APU-C1", "Hormigón");
        agregarDetalleApu(token, apuId, "MATERIAL", matId, 2.0);
        Long capId = crearCapitulo(token, presId, "Obras");
        crearRubro(token, presId, capId, apuId, 5.0);

        // Create cronograma -> actividades auto-imported
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

        // GET should return the same
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presId + "/cronograma")
                .then()
                .statusCode(200)
                .body("id", is(cronogramaId.intValue()))
                .body("actividades.size()", is(1));

        // Duplicate creation -> 409
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("unidadTiempo", "SEMANA", "numeroPeriodos", 12))
                .when()
                .post("/api/v1/presupuestos/" + presId + "/cronograma")
                .then()
                .statusCode(409);
    }

    @Test
    void actualizar_avance_y_configurar_periodos() {
        String token = AuthSupport.registrarConToken(mailbox, "crono-avance@uce.edu.ec");
        Long proyectoId = crearProyecto(token);
        Long presId = obtenerPresupuestoVigente(token, proyectoId);

        Long matId = crearInsumo(token, proyectoId, "MAT-A1", "MATERIAL", "Varilla", 5.0);
        Long apuId = crearApu(token, presId, "APU-A1", "Estructura");
        agregarDetalleApu(token, apuId, "MATERIAL", matId, 3.0);
        Long capId = crearCapitulo(token, presId, "Estructuras");
        crearRubro(token, presId, capId, apuId, 10.0);

        // Create cronograma with 4 periods
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

        // Update avance for periods 1-4
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

        // Invalid period key -> 400
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("avancePorPeriodo", Map.of("5", 0.10)))
                .when()
                .patch("/api/v1/cronogramas/" + cronogramaId + "/actividades/" + actividadId)
                .then()
                .statusCode(400);

        // Reduce periods from 4 to 2 without confirmar -> 409 (data in periods 3,4)
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("numeroPeriodos", 2))
                .when()
                .put("/api/v1/cronogramas/" + cronogramaId)
                .then()
                .statusCode(409);

        // Reduce periods with confirmarPerdida -> 200
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
    void marcar_revisado_y_desactualizado() {
        String token = AuthSupport.registrarConToken(mailbox, "crono-rev@uce.edu.ec");
        Long proyectoId = crearProyecto(token);
        Long presId = obtenerPresupuestoVigente(token, proyectoId);

        // Create cronograma on empty presupuesto
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

        // Mark revisado
        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/cronogramas/" + cronogramaId + "/revisado")
                .then()
                .statusCode(200)
                .body("totalGeneralRevisado", notNullValue())
                .body("fechaRevision", notNullValue())
                .body("desactualizado", is(false));

        // Add a rubro to change presupuesto total
        Long matId = crearInsumo(token, proyectoId, "MAT-R1", "MATERIAL", "Arena", 8.0);
        Long apuId = crearApu(token, presId, "APU-R1", "Relleno");
        agregarDetalleApu(token, apuId, "MATERIAL", matId, 1.0);
        Long capId = crearCapitulo(token, presId, "Rellenos");
        crearRubro(token, presId, capId, apuId, 10.0);

        // Now cronograma should be desactualizado (total changed)
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presId + "/cronograma")
                .then()
                .statusCode(200)
                .body("desactualizado", is(true));
    }
}
