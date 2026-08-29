package ec.uce.propuestas.presupuesto.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

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
class PresupuestoResourceIT {

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, token_usuario, "
                    + "refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    private Long crearProyecto(String token) {
        return ((Number) given().contentType(JSON)
                        .header("Authorization", "Bearer " + token)
                        .body(Map.of(
                                "nombreProyecto", "Construcción UCE",
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

    private Long crearInsumo(
            String token, Long proyectoId, String codigo, String tipo, String desc, String unidad, double precio) {
        return ((Number) given().contentType(JSON)
                        .header("Authorization", "Bearer " + token)
                        .body(Map.of(
                                "codigo", codigo,
                                "tipo", tipo,
                                "descripcion", desc,
                                "unidad", unidad,
                                "precioUnitario", precio))
                        .when()
                        .post("/api/v1/proyectos/" + proyectoId + "/insumos")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id"))
                .longValue();
    }

    private Long crearApu(String token, Long presupuestoId, String codigo, String desc, String unidad) {
        return ((Number) given().contentType(JSON)
                        .header("Authorization", "Bearer " + token)
                        .body(Map.of(
                                "codigo", codigo,
                                "descripcion", desc,
                                "unidad", unidad))
                        .when()
                        .post("/api/v1/presupuestos/" + presupuestoId + "/apus")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id"))
                .longValue();
    }

    private void agregarDetalleApu(String token, Long apuId, String tipo, Long insumoId, double cantidad, Double rend) {
        Map<String, Object> body = rend != null
                ? Map.of("seccionTipo", tipo, "insumoId", insumoId, "cantidad", cantidad, "rendimiento", rend)
                : Map.of("seccionTipo", tipo, "insumoId", insumoId, "cantidad", cantidad);
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(body)
                .when()
                .post("/api/v1/apus/" + apuId + "/detalles")
                .then()
                .statusCode(201);
    }

    @Test
    void testFlujoCompletoPresupuesto() {
        String token = AuthSupport.registrarConToken(mailbox, "presupuesto-user@uce.edu.ec");
        Long proyectoId = crearProyecto(token);

        // 1. Listar versiones iniciales (al crear proyecto, se crea versión 1 vigente)
        Number pIdNum = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + proyectoId + "/presupuestos")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].version", is(1))
                .body("[0].esVigente", is(true))
                .extract()
                .path("[0].presupuestoId");
        Long pres1Id = pIdNum.longValue();

        // 2. Crear insumos y APU
        Long matId = crearInsumo(token, proyectoId, "MAT-01", "MATERIAL", "Cemento", "saco", 7.50);
        Long apuId = crearApu(token, pres1Id, "APU-01", "Hormigón simple", "m3");
        agregarDetalleApu(token, apuId, "MATERIAL", matId, 4.0, null); // 4 * 7.50 = 30.00 CD

        // 3. Crear Estructura de Capítulos
        // Root: "1" Preliminares
        Number cap1Num = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "Preliminares"))
                .when()
                .post("/api/v1/presupuestos/" + pres1Id + "/capitulos")
                .then()
                .statusCode(201)
                .body("capitulos.size()", is(1))
                .body("capitulos[0].item", equalTo("1"))
                .body("capitulos[0].descripcion", equalTo("Preliminares"))
                .extract()
                .path("capitulos[0].id");
        Long cap1Id = cap1Num.longValue();

        // Subcapítulo: "1.1" Obras de inicio
        Number subcapNum = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "Obras de inicio", "parentId", cap1Id))
                .when()
                .post("/api/v1/presupuestos/" + pres1Id + "/capitulos")
                .then()
                .statusCode(201)
                .body("capitulos[0].subcapitulos.size()", is(1))
                .body("capitulos[0].subcapitulos[0].item", equalTo("1.1"))
                .extract()
                .path("capitulos[0].subcapitulos[0].id");
        Long subcap1Id = subcapNum.longValue();

        // 4. Agregar Rubro al subcapítulo
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId, "cantidad", 10.0))
                .when()
                .post("/api/v1/presupuestos/" + pres1Id + "/capitulos/" + subcap1Id + "/rubros")
                .then()
                .statusCode(201)
                .body("totalGeneral", notNullValue())
                .body("capitulos[0].subcapitulos[0].rubros.size()", is(1))
                .body("capitulos[0].subcapitulos[0].rubros[0].item", equalTo("1.1.1"))
                .body("capitulos[0].subcapitulos[0].rubros[0].codigo", equalTo("APU-01"))
                .body("capitulos[0].subcapitulos[0].rubros[0].precioTotal", notNullValue());

        // 5. Verificar validación y resumen
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + pres1Id + "/resumen")
                .then()
                .statusCode(200)
                .body("porComponente.MATERIAL", notNullValue())
                .body("totalGeneral", notNullValue());

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + pres1Id + "/validacion")
                .then()
                .statusCode(200)
                .body("itemsPuCero", empty())
                .body("itemsCantidadCero", empty());

        // 6. Crear versión 2 (deep copy)
        Number pres2Num = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("origenId", pres1Id, "notas", "Versión de prueba 2"))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/presupuestos")
                .then()
                .statusCode(201)
                .body("version", is(2))
                .body("esVigente", is(false))
                .body("origenId", is(pres1Id.intValue()))
                .extract()
                .path("presupuestoId");
        Long pres2Id = pres2Num.longValue();

        // 7. Comparar versiones 1 y 2
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + pres1Id + "/comparar?con=" + pres2Id)
                .then()
                .statusCode(200)
                .body("versiones.size()", is(2));

        // 8. Marcar versión 2 como vigente
        given().header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/presupuestos/" + pres2Id + "/vigente")
                .then()
                .statusCode(200)
                .body("esVigente", is(true));

        // 9. Intentar eliminar versión 2 (vigente) -> 409
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/presupuestos/" + pres2Id)
                .then()
                .statusCode(409);

        // 10. Eliminar versión 1 (ya no vigente) -> 204
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/presupuestos/" + pres1Id)
                .then()
                .statusCode(204);
    }
}
