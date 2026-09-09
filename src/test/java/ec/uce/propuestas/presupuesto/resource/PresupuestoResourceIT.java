package ec.uce.propuestas.presupuesto.resource;

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

    private String crearProyecto(String token) {
        return given().contentType(JSON)
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
            String token,
            String proyectoPublicId,
            String codigo,
            String tipo,
            String desc,
            String unidad,
            double precio) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "codigo", codigo,
                        "tipo", tipo,
                        "descripcion", desc,
                        "unidad", unidad,
                        "precioUnitario", precio))
                .when()
                .post("/api/v1/proyectos/" + proyectoPublicId + "/insumos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String crearApu(String token, String presupuestoPublicId, String codigo, String desc, String unidad) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "codigo", codigo,
                        "descripcion", desc,
                        "unidad", unidad))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoPublicId + "/apus")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private void agregarDetalleApu(
            String token, String apuPublicId, String tipo, String insumoPublicId, double cantidad, Double rend) {
        Map<String, Object> body = rend != null
                ? Map.of("seccionTipo", tipo, "insumoId", insumoPublicId, "cantidad", cantidad, "rendimiento", rend)
                : Map.of("seccionTipo", tipo, "insumoId", insumoPublicId, "cantidad", cantidad);
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(body)
                .when()
                .post("/api/v1/apus/" + apuPublicId + "/detalles")
                .then()
                .statusCode(201);
    }

    @Test
    void testFlujoCompletoPresupuesto() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "presupuesto-user@uce.edu.ec");
        String proyectoPublicId = crearProyecto(token);

        // 1. Insert presupuesto v1 via SQL (no auto-creation)
        Long pres1Id = insertarPresupuesto(proyectoPublicId);
        String pres1PublicId = presupuestoPublicId(pres1Id);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + proyectoPublicId + "/presupuestos")
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].version", is(1))
                .body("[0].esVigente", is(true));

        // 2. Crear insumos y APU (UUIDv7 endpoints)
        String matId = crearInsumo(token, proyectoPublicId, "MAT-01", "MATERIAL", "Cemento", "saco", 7.50);
        String apuPublicId = crearApu(token, pres1PublicId, "APU-01", "Hormigón simple", "m3");
        agregarDetalleApu(token, apuPublicId, "MATERIAL", matId, 4.0, null);
        Long apuInternalId = internalId("apu", apuPublicId);

        // 3. Crear Estructura de Capítulos (Long presupuesto endpoints)
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

        // 4. Agregar Rubro (Long apuId in body)
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuInternalId, "cantidad", 10.0))
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
                .post("/api/v1/proyectos/" + proyectoPublicId + "/presupuestos")
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
