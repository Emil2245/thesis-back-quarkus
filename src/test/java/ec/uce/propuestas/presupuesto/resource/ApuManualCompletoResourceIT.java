package ec.uce.propuestas.presupuesto.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Focused REST coverage for Plan 004 atomic manual APU creation. */
@QuarkusTest
class ApuManualCompletoResourceIT {

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        mailbox.clear();
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE log_actividad, actividad, cronograma, apu_detalle, apu_seccion, apu, rubro, "
                    + "capitulo, presupuesto, insumo, base_insumos, parametros_proyecto, firmante, proyecto, "
                    + "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    @Test
    void TC_P04_01_crea_apu_completo_con_hm_rubroy_totales_coherentes() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p04-happy@ex.com");
        String proyectoId = crearProyecto(token);
        String presupuestoId = presupuestoDeProyecto(proyectoId);
        String capituloId = crearCapitulo(token, presupuestoId, "Obras");
        String manoObra = crearInsumo(token, proyectoId, "MO-04", "MANO_OBRA", "Peón", "h", 4.0);
        String material = crearInsumo(token, proyectoId, "MA-04", "MATERIAL", "Hormigón", "m3", 2.0);

        var response = crearManual(
                        token,
                        presupuestoId,
                        body(
                                "MAN-001",
                                capituloId,
                                null,
                                List.of(
                                        detalle("MANO_OBRA", manoObra, "2.000000", "1.000000"),
                                        detalle("MATERIAL", material, "3.000000", null))))
                .then()
                .statusCode(201)
                .body("apu.codigo", equalTo("MAN-001"))
                .body("apu.secciones.size()", is(4))
                .body("apu.secciones[0].tipo", equalTo("EQUIPO"))
                .body("apu.secciones[0].detalles[0].esHerramientaMenor", is(true))
                .body("apu.secciones[0].detalles[0].orden", is(1))
                .body("apu.secciones[1].detalles[0].orden", is(1))
                .body("apu.secciones[2].detalles[0].orden", is(1))
                .body("presupuesto.capitulos[0].rubros[0].cantidad", equalTo("1.000000"))
                .body("presupuesto.capitulos[0].rubros[0].precioUnitario", equalTo("14.400000"))
                .body("presupuesto.capitulos[0].rubros[0].precioTotal", equalTo("14.400000"))
                .extract()
                .response();

        assertEquals(
                response.path("apu.id"),
                response.path("presupuesto.capitulos[0].rubros[0].apuId").toString());
        assertEquals(
                new BigDecimal("14.4"),
                new BigDecimal(response.path("apu.costoDirecto").toString()));
        assertEquals(
                new BigDecimal("14.4"),
                new BigDecimal(response.path("apu.costoTotal").toString()));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + response.path("apu.id"))
                .then()
                .statusCode(200)
                .body("costoTotal", comparesTo(new BigDecimal("14.4")));
    }

    @Test
    void TC_P04_02_codigo_nulo_o_blanco_se_autogenera() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p04-code@ex.com");
        String proyectoId = crearProyecto(token);
        String presupuestoId = presupuestoDeProyecto(proyectoId);
        crearCapitulo(token, presupuestoId, "Obras");
        String material = crearInsumo(token, proyectoId, "MA-CODE", "MATERIAL", "Arena", "m3", 1.0);

        crearManual(token, presupuestoId, body(null, null, null, List.of(detalle("MATERIAL", material, "1", null))))
                .then()
                .statusCode(201)
                .body("apu.codigo", equalTo("APU-001"));

        crearManual(token, presupuestoId, body("", null, null, List.of(detalle("MATERIAL", material, "1", null))))
                .then()
                .statusCode(201)
                .body("apu.codigo", equalTo("APU-002"));
    }

    @Test
    void TC_P04_03_codigo_duplicado_revierte_sin_nuevas_filas_ni_log() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p04-duplicate@ex.com");
        String proyectoId = crearProyecto(token);
        String presupuestoId = presupuestoDeProyecto(proyectoId);
        crearCapitulo(token, presupuestoId, "Obras");
        String material = crearInsumo(token, proyectoId, "MA-DUP", "MATERIAL", "Arena", "m3", 1.0);

        crearManual(
                        token,
                        presupuestoId,
                        body("DUP-001", null, null, List.of(detalle("MATERIAL", material, "1", null))))
                .then()
                .statusCode(201);
        long apusAntes = count("select count(*) from apu");
        long rubrosAntes = count("select count(*) from rubro");
        long logsAntes = count("select count(*) from log_actividad where evento = 'apu.creado'");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(body("DUP-001", null, null, List.of(detalle("MATERIAL", material, "1", null))))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/apus/completo")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("codigo-duplicado"));

        assertEquals(apusAntes, count("select count(*) from apu"));
        assertEquals(rubrosAntes, count("select count(*) from rubro"));
        assertEquals(logsAntes, count("select count(*) from log_actividad where evento = 'apu.creado'"));
    }

    @Test
    void TC_P04_04_error_en_una_fila_revierte_cabecera_secciones_rubro_y_log() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p04-rollback@ex.com");
        String proyectoId = crearProyecto(token);
        String presupuestoId = presupuestoDeProyecto(proyectoId);
        crearCapitulo(token, presupuestoId, "Obras");
        String manoObra = crearInsumo(token, proyectoId, "MO-ROLL", "MANO_OBRA", "Peón", "h", 4.0);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(body(
                        "ROLL-001",
                        null,
                        null,
                        List.of(
                                detalle("MANO_OBRA", manoObra, "1", "1"),
                                // A MANO_OBRA insumo cannot be submitted in MATERIAL.
                                detalle("MATERIAL", manoObra, "1", null))))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/apus/completo")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        assertEquals(0L, count("select count(*) from apu"));
        assertEquals(0L, count("select count(*) from apu_seccion"));
        assertEquals(0L, count("select count(*) from apu_detalle"));
        assertEquals(0L, count("select count(*) from rubro"));
        assertEquals(0L, count("select count(*) from log_actividad where evento = 'apu.creado'"));
    }

    @Test
    void TC_P04_05_rechaza_rendimiento_no_aplicable_y_campo_derivado() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p04-strict@ex.com");
        String proyectoId = crearProyecto(token);
        String presupuestoId = presupuestoDeProyecto(proyectoId);
        crearCapitulo(token, presupuestoId, "Obras");
        String material = crearInsumo(token, proyectoId, "MA-STRICT", "MATERIAL", "Arena", "m3", 1.0);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(body("STRICT-001", null, null, List.of(detalle("MATERIAL", material, "1", "1"))))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/apus/completo")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        String unknownField = "{"
                + "\"descripcion\":\"No debe entrar\","
                + "\"unidad\":\"u\","
                + "\"costoDirecto\":\"99\","
                + "\"detalles\":[{"
                + "\"seccionTipo\":\"MATERIAL\","
                + "\"insumoId\":\"" + material + "\","
                + "\"cantidad\":\"1\"}]}";
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(unknownField)
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/apus/completo")
                .then()
                .statusCode(400);
    }

    @Test
    void TC_P04_06_rendimiento_de_mano_de_obra_es_obligatorio() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p04-rend@ex.com");
        String proyectoId = crearProyecto(token);
        String presupuestoId = presupuestoDeProyecto(proyectoId);
        crearCapitulo(token, presupuestoId, "Obras");
        String manoObra = crearInsumo(token, proyectoId, "MO-REND", "MANO_OBRA", "Peón", "h", 4.0);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(body("REND-001", null, null, List.of(detalle("MANO_OBRA", manoObra, "1", null))))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/apus/completo")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        assertEquals(0L, count("select count(*) from apu"));
    }

    @Test
    void TC_P04_07_porcentaje_indirecto_se_persiste_como_override() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p04-ci@ex.com");
        String proyectoId = crearProyecto(token);
        String presupuestoId = presupuestoDeProyecto(proyectoId);
        crearCapitulo(token, presupuestoId, "Obras");
        String material = crearInsumo(token, proyectoId, "MA-CI", "MATERIAL", "Arena", "m3", 10.0);

        crearManual(
                        token,
                        presupuestoId,
                        body("CI-001", null, "0.2500", List.of(detalle("MATERIAL", material, "2", null))))
                .then()
                .statusCode(201)
                .body("apu.porcentajeIndirecto", comparesTo(new BigDecimal("0.2500")))
                .body("apu.porcentajeIndirectoEfectivo", comparesTo(new BigDecimal("0.2500")))
                .body("apu.costoDirecto", comparesTo(new BigDecimal("20")))
                .body("apu.costoIndirecto", comparesTo(new BigDecimal("5")))
                .body("apu.costoTotal", comparesTo(new BigDecimal("25")));
    }

    @Test
    void TC_P04_08_sin_capitulo_explicito_usa_la_ultima_hoja_profunda() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p04-leaf@ex.com");
        String proyectoId = crearProyecto(token);
        String presupuestoId = presupuestoDeProyecto(proyectoId);
        crearCapitulo(token, presupuestoId, "Raíz 1");
        String raiz2 = crearCapitulo(token, presupuestoId, "Raíz 2");
        crearCapituloHijo(token, presupuestoId, raiz2, "Hijo último");
        String material = crearInsumo(token, proyectoId, "MA-LEAF", "MATERIAL", "Arena", "m3", 1.0);

        crearManual(
                        token,
                        presupuestoId,
                        body("LEAF-001", null, null, List.of(detalle("MATERIAL", material, "1", null))))
                .then()
                .statusCode(201)
                .body("presupuesto.capitulos[1].subcapitulos[0].rubros[0].item", equalTo("2.1.1"));
    }

    @Test
    void TC_P04_09_presupuesto_sin_capitulos_devuelve_conflicto() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p04-no-cap@ex.com");
        String proyectoId = crearProyecto(token);
        String presupuestoId = presupuestoDeProyecto(proyectoId);
        String material = crearInsumo(token, proyectoId, "MA-NOCAP", "MATERIAL", "Arena", "m3", 1.0);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(body("NOCAP-001", null, null, List.of(detalle("MATERIAL", material, "1", null))))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/apus/completo")
                .then()
                .statusCode(409)
                .body("codigo", equalTo("presupuesto-sin-capitulos"));
        assertEquals(0L, count("select count(*) from apu"));
    }

    @Test
    void TC_P04_10_path_uuid_invalido_y_presupuesto_ajeno_no_crean() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p04-uuid@ex.com");
        String proyectoId = crearProyecto(token);
        String presupuestoId = presupuestoDeProyecto(proyectoId);
        crearCapitulo(token, presupuestoId, "Obras");
        String material = crearInsumo(token, proyectoId, "MA-UUID", "MATERIAL", "Arena", "m3", 1.0);
        String body = jsonBody("UUID-001", null, null, detalle("MATERIAL", material, "1", null));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(body)
                .when()
                .post("/api/v1/presupuestos/550e8400-e29b-41d4-a716-446655440000/apus/completo")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        String otherToken = AuthSupport.registrarConToken(mailbox, "p04-uuid-other@ex.com");
        String otherProject = crearProyecto(otherToken);
        String otherBudget = presupuestoDeProyecto(otherProject);
        crearCapitulo(otherToken, otherBudget, "Otro");
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(body)
                .when()
                .post("/api/v1/presupuestos/" + otherBudget + "/apus/completo")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    private io.restassured.response.Response crearManual(String token, String presupuestoId, Map<String, Object> body) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(body)
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/apus/completo");
    }

    private String crearProyecto(String token) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto", "Proyecto Plan 004",
                        "anio", (short) 2026,
                        "plazoEjecucion", (short) 4,
                        "plazoUnidad", "MES",
                        "direccionInstitucional", "Universidad Central del Ecuador"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String presupuestoDeProyecto(String proyectoId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "select p.public_id from presupuesto p join proyecto pr on pr.id = p.proyecto_id "
                                + "where pr.public_id = ? and p.version = 1")) {
            ps.setObject(1, java.util.UUID.fromString(proyectoId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private String crearCapitulo(String token, String presupuestoId, String descripcion) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", descripcion))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[-1].id");
    }

    private String crearCapituloHijo(String token, String presupuestoId, String parentId, String descripcion) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", descripcion, "parentId", parentId))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[1].subcapitulos[0].id");
    }

    private String crearInsumo(
            String token,
            String proyectoId,
            String codigo,
            String tipo,
            String descripcion,
            String unidad,
            double precio) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "codigo", codigo,
                        "tipo", tipo,
                        "descripcion", descripcion,
                        "unidad", unidad,
                        "precioUnitario", precio))
                .when()
                .post("/api/v1/proyectos/" + proyectoId + "/insumos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private static Map<String, Object> detalle(
            String seccionTipo, String insumoId, String cantidad, String rendimiento) {
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("seccionTipo", seccionTipo);
        detalle.put("insumoId", insumoId);
        detalle.put("cantidad", cantidad);
        if (rendimiento != null) {
            detalle.put("rendimiento", rendimiento);
        }
        return detalle;
    }

    private static Map<String, Object> body(
            String codigo, String capituloId, String porcentajeIndirecto, List<Map<String, Object>> detalles) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (codigo != null) {
            body.put("codigo", codigo);
        }
        body.put("descripcion", "APU manual");
        body.put("unidad", "u");
        if (capituloId != null) {
            body.put("capituloId", capituloId);
        }
        if (porcentajeIndirecto != null) {
            body.put("porcentajeIndirecto", porcentajeIndirecto);
        }
        body.put("detalles", detalles);
        return body;
    }

    private static String jsonBody(
            String codigo, String capituloId, String porcentajeIndirecto, Map<String, Object> detalle)
            throws Exception {
        Map<String, Object> body = body(codigo, capituloId, porcentajeIndirecto, List.of(detalle));
        return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body);
    }

    private long count(String sql) throws Exception {
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement();
                ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static org.hamcrest.Matcher<Number> comparesTo(BigDecimal expected) {
        return new org.hamcrest.TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(Number actual) {
                return new BigDecimal(actual.toString()).compareTo(expected) == 0;
            }

            @Override
            public void describeTo(org.hamcrest.Description description) {
                description.appendText("a number numerically equal to ").appendValue(expected);
            }
        };
    }
}
