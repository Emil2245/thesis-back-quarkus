package ec.uce.propuestas.apu.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Flujo REST del agregado {@code apu} (P-19…P-22). Patrón {@code InsumoResourceIT}.
 * El presupuesto se inserta por SQL (el módulo presupuesto es iteración I-07).
 */
@QuarkusTest
class ApuResourceIT {

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
                                "nombreProyecto", "Redes UCE",
                                "anio", (short) 2026,
                                "plazoEjecucion", (short) 4,
                                "plazoUnidad", "MES",
                                "direccionInstitucional", "Universidad Central del Ecuador"))
                        .when()
                        .post("/api/v1/proyectos")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id"))
                .longValue();
    }

    private Long crearInsumo(
            String token,
            Long proyectoId,
            String codigo,
            String tipo,
            String descripcion,
            String unidad,
            double precio) {
        return ((Number) given().contentType(JSON)
                        .header("Authorization", "Bearer " + token)
                        .body(Map.of(
                                "codigo",
                                codigo,
                                "tipo",
                                tipo,
                                "descripcion",
                                descripcion,
                                "unidad",
                                unidad,
                                "precioUnitario",
                                precio))
                        .when()
                        .post("/api/v1/proyectos/" + proyectoId + "/insumos")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id"))
                .longValue();
    }

    private Long insertarPresupuesto(Long proyectoId) throws Exception {
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

    private Long crearApu(String token, Long presupuestoId, String codigo) {
        return ((Number) given().contentType(JSON)
                        .header("Authorization", "Bearer " + token)
                        .body(Map.of("codigo", codigo, "descripcion", "Instalación", "unidad", "m"))
                        .when()
                        .post("/api/v1/presupuestos/" + presupuestoId + "/apus")
                        .then()
                        .statusCode(201)
                        .extract()
                        .path("id"))
                .longValue();
    }

    @Test
    void TC_P20_01_crear_apu_crea_4_secciones_ordenadas_y_fila_hm() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p20@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("codigo", "PZ-001", "descripcion", "Pozo", "unidad", "u"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/apus")
                .then()
                .statusCode(201)
                .body("codigo", equalTo("PZ-001"))
                .body("secciones.size()", is(4))
                .body("secciones[0].tipo", equalTo("EQUIPO"))
                .body("secciones[1].tipo", equalTo("MANO_OBRA"))
                .body("secciones[2].tipo", equalTo("MATERIAL"))
                .body("secciones[3].tipo", equalTo("TRANSPORTE"))
                .body("secciones[0].detalles[0].esHerramientaMenor", is(true))
                .body("secciones[0].detalles[0].descripcion", equalTo("Herramienta Menor 5%MO"));
    }

    @Test
    void TC_P20_03_codigo_duplicado_unicamente_por_version() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p20dup@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoV1 = insertarPresupuesto(proyectoId);

        crearApu(token, presupuestoV1, "DUP-001");

        // mismo código en la misma versión → 400 codigo-duplicado
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("codigo", "DUP-001", "descripcion", "Otro", "unidad", "u"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoV1 + "/apus")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("codigo-duplicado"));

        // misma versión, listado → un solo APU
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoV1 + "/apus")
                .then()
                .statusCode(200)
                .body("total", is(1));
    }

    @Test
    void TC_P21_02_agregar_filas_mo_y_material_recalcula_totales() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p21@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        Long mo = crearInsumo(token, proyectoId, "MO-010", "MANO_OBRA", "Peón", "h", 4.0);
        Long mat = crearInsumo(token, proyectoId, "MA-010", "MATERIAL", "Tubo", "m", 2.0);
        Long apuId = crearApu(token, presupuestoId, "TB-001");

        int numSec = 1;
        int matSec = 2;

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "seccionTipo", "MANO_OBRA", "insumoId", mo.intValue(), "cantidad", 2.0, "rendimiento", 1.0))
                .when()
                .post("/api/v1/apus/" + apuId + "/detalles")
                .then()
                .statusCode(201)
                .body("secciones[" + numSec + "].detalles.size()", is(1));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("seccionTipo", "MATERIAL", "insumoId", mat.intValue(), "cantidad", 3.0))
                .when()
                .post("/api/v1/apus/" + apuId + "/detalles")
                .then()
                .statusCode(201)
                .body("secciones[" + matSec + "].detalles.size()", is(1))
                // CD = HM(0.05×4×2×1=0.4) + N(2×4×1=8) + O(3×2=6) = 14.4
                .body("costoDirecto", comparesTo(new BigDecimal("14.4")))
                .body("costoTotal", comparesTo(new BigDecimal("14.4")))
                .body("costoIndirecto", comparesTo(BigDecimal.ZERO));
    }

    @Test
    void TC_P21_03_fila_hm_no_editable_ni_eliminable() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p21hm@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        Long apuId = crearApu(token, presupuestoId, "HM-001");

        Integer detalleId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId)
                .then()
                .statusCode(200)
                .extract()
                .path("secciones[0].detalles[0].id");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("cantidad", 3.0))
                .when()
                .patch("/api/v1/apus/" + apuId + "/detalles/" + detalleId)
                .then()
                .statusCode(409)
                .body("codigo", equalTo("fila-protegida"));

        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/apus/" + apuId + "/detalles/" + detalleId)
                .then()
                .statusCode(409)
                .body("codigo", equalTo("fila-protegida"));
    }

    @Test
    void TC_P22_02_override_precio_cambia_precio_efectivo_y_null_hereda() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        Long mo = crearInsumo(token, proyectoId, "MO-020", "MANO_OBRA", "Soldador", "h", 5.0);
        Long apuId = crearApu(token, presupuestoId, "OV-001");

        Integer detalleId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "seccionTipo", "MANO_OBRA", "insumoId", mo.intValue(), "cantidad", 1.0, "rendimiento", 1.0))
                .when()
                .post("/api/v1/apus/" + apuId + "/detalles")
                .then()
                .statusCode(201)
                .extract()
                .path("secciones[1].detalles[0].id");

        // hereda precio del insumo
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId)
                .then()
                .statusCode(200)
                .body("secciones[1].detalles[0].precioEfectivo", comparesTo(new BigDecimal("5.0")))
                .body("secciones[1].detalles[0].precioHeredado", is(true));

        // override → precio efectivo manual
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("precioOverride", 7.5))
                .when()
                .patch("/api/v1/apus/" + apuId + "/detalles/" + detalleId)
                .then()
                .statusCode(200)
                .body("secciones[1].detalles[0].precioEfectivo", comparesTo(new BigDecimal("7.5")))
                .body("secciones[1].detalles[0].precioHeredado", is(false));

        // null explícito → restaura herencia
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("{\"precioOverride\": null}")
                .when()
                .patch("/api/v1/apus/" + apuId + "/detalles/" + detalleId)
                .then()
                .statusCode(200)
                .body("secciones[1].detalles[0].precioEfectivo", comparesTo(new BigDecimal("5.0")))
                .body("secciones[1].detalles[0].precioHeredado", is(true));
    }

    @Test
    void TC_P22_01_editar_precio_insumo_cambia_fila_sin_override() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p22b@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        Long mat = crearInsumo(token, proyectoId, "MA-030", "MATERIAL", "Ángulo", "kg", 2.0);
        Long apuId = crearApu(token, presupuestoId, "PR-001");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("seccionTipo", "MATERIAL", "insumoId", mat.intValue(), "cantidad", 1.0))
                .when()
                .post("/api/v1/apus/" + apuId + "/detalles")
                .then()
                .statusCode(201);

        // actualizo el precio del insumo
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", "Ángulo 50", "unidad", "kg", "precioUnitario", 2.5))
                .when()
                .put("/api/v1/proyectos/" + proyectoId + "/insumos/" + mat)
                .then()
                .statusCode(200);

        // la fila sin override refleja el nuevo precio
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId)
                .then()
                .statusCode(200)
                .body("secciones[2].detalles[0].precioEfectivo", comparesTo(new BigDecimal("2.5")))
                .body("secciones[2].detalles[0].precioHeredado", is(true));
    }

    @Test
    void TC_P19_apu_vinculado_a_rubro_no_se_elimina_409() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "del@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        Long apuId = crearApu(token, presupuestoId, "VD-001");

        insertarRubroVinculado(presupuestoId, apuId);

        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/apus/" + apuId)
                .then()
                .statusCode(409)
                .body("codigo", equalTo("apu-referenciado"));
    }

    @Test
    void RNF05_apu_de_otro_usuario_devuelve_404() throws Exception {
        String dueno = AuthSupport.registrarConToken(mailbox, "dueno@ex.com");
        Long proyectoId = crearProyecto(dueno);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        Long apuId = crearApu(dueno, presupuestoId, "AJ-001");

        String intruso = AuthSupport.registrarConToken(mailbox, "intruso@ex.com");
        given().header("Authorization", "Bearer " + intruso)
                .when()
                .get("/api/v1/apus/" + apuId)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    private void insertarRubroVinculado(Long presupuestoId, Long apuId) throws Exception {
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("INSERT INTO capitulo (presupuesto_id, item, descripcion, orden) " + "VALUES (" + presupuestoId
                    + ", '1', 'Capitulo 1', 1)");
            st.execute("INSERT INTO rubro (capitulo_id, apu_id, item, codigo, descripcion, unidad, cantidad) "
                    + "SELECT c.id, " + apuId + ", '1', 'VD-001', 'Válvula', 'u', 1 "
                    + "FROM capitulo c WHERE c.presupuesto_id = " + presupuestoId);
        }
    }

    private static org.hamcrest.Matcher<Number> comparesTo(BigDecimal expected) {
        return new org.hamcrest.TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(Number n) {
                return new BigDecimal(n.toString()).compareTo(expected) == 0;
            }

            @Override
            public void describeTo(org.hamcrest.Description description) {
                description.appendText(expected.toPlainString());
            }
        };
    }

    @Test
    void TC_P23_P24_porcentajes_actualizan_y_restauran() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p23p24@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        Long apuId = crearApu(token, presupuestoId, "PCT-001");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("0.2200")
                .when()
                .patch("/api/v1/apus/" + apuId + "/porcentaje-indirecto")
                .then()
                .statusCode(200)
                .body("porcentajeIndirecto", comparesTo(new BigDecimal("0.22")));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("0.1000")
                .when()
                .patch("/api/v1/apus/" + apuId + "/porcentaje-descuento")
                .then()
                .statusCode(200)
                .body("porcentajeDescuento", comparesTo(new BigDecimal("0.10")));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("null")
                .when()
                .patch("/api/v1/apus/" + apuId + "/porcentaje-indirecto")
                .then()
                .statusCode(200)
                .body("porcentajeIndirecto", equalTo(null));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("null")
                .when()
                .patch("/api/v1/apus/" + apuId + "/porcentaje-descuento")
                .then()
                .statusCode(200)
                .body("porcentajeDescuento", comparesTo(BigDecimal.ZERO));
    }

    @Test
    void TC_P45_01_et_roundtrip_get_devuelve_contenido_persistente() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p45rt@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        Long apuId = crearApu(token, presupuestoId, "ET-RT-001");

        String texto = "Dosificación 1:2:3, vibrado mecánico, curado húmedo 7 días.\n"
                + "Calidad: cemento Portland tipo I, Norma NEC-2015, ACI 318.";

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("texto", texto))
                .when()
                .put("/api/v1/apus/" + apuId + "/especificacion-tecnica")
                .then()
                .statusCode(200)
                .body("codigo", equalTo("ET-RT-001"));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId + "/especificacion-tecnica")
                .then()
                .statusCode(200)
                .body("apuId", equalTo(apuId.intValue()))
                .body("contenido", equalTo(texto));
    }

    @Test
    void TC_P45_02_et_null_y_vacio_limpian_contenido() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p45clr@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        Long apuId = crearApu(token, presupuestoId, "ET-CLR-001");

        // sembrar texto
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("texto", "Texto inicial a limpiar"))
                .when()
                .put("/api/v1/apus/" + apuId + "/especificacion-tecnica")
                .then()
                .statusCode(200);

        // null explícito → contenido null en GET
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(java.util.Collections.singletonMap("texto", (Object) null))
                .when()
                .put("/api/v1/apus/" + apuId + "/especificacion-tecnica")
                .then()
                .statusCode(200);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId + "/especificacion-tecnica")
                .then()
                .statusCode(200)
                .body("apuId", equalTo(apuId.intValue()))
                .body("contenido", equalTo(null));

        // cadena vacía → contenido null en GET (limpieza)
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("texto", "Texto reaparecido"))
                .when()
                .put("/api/v1/apus/" + apuId + "/especificacion-tecnica")
                .then()
                .statusCode(200);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("texto", ""))
                .when()
                .put("/api/v1/apus/" + apuId + "/especificacion-tecnica")
                .then()
                .statusCode(200);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId + "/especificacion-tecnica")
                .then()
                .statusCode(200)
                .body("contenido", equalTo(null));
    }

    @Test
    void TC_P45_03_et_multibyte_excede_65536_bytes_rechaza_con_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p45mb@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        Long apuId = crearApu(token, presupuestoId, "ET-MB-001");

        // "á" son 2 bytes UTF-8; 65 537 caracteres 'á' = 131 074 bytes > 65 536
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 65_537; i++) {
            sb.append('á');
        }
        String enorme = sb.toString();
        int bytes = enorme.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        // sanity check del setup
        org.junit.jupiter.api.Assertions.assertEquals(131_074, bytes);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("texto", enorme))
                .when()
                .put("/api/v1/apus/" + apuId + "/especificacion-tecnica")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // verificación negativa: el contenido no debe haberse persistido
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId + "/especificacion-tecnica")
                .then()
                .statusCode(200)
                .body("contenido", equalTo(null));
    }

    @Test
    void TC_P45_04_et_exacto_65536_bytes_se_acepta() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p45bd@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        Long apuId = crearApu(token, presupuestoId, "ET-BD-001");

        // 'a' es 1 byte ASCII; 65 536 caracteres = exactamente el límite permitido
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 65_536; i++) {
            sb.append('a');
        }
        String limite = sb.toString();

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("texto", limite))
                .when()
                .put("/api/v1/apus/" + apuId + "/especificacion-tecnica")
                .then()
                .statusCode(200);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId + "/especificacion-tecnica")
                .then()
                .statusCode(200)
                .body("contenido.length()", is(65_536));
    }

    @Test
    void RNF05_et_de_otro_usuario_devuelve_404_en_get_y_put() throws Exception {
        String dueno = AuthSupport.registrarConToken(mailbox, "duenoet@ex.com");
        Long proyectoId = crearProyecto(dueno);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        Long apuId = crearApu(dueno, presupuestoId, "ET-AJ-001");

        String intruso = AuthSupport.registrarConToken(mailbox, "intrusoet@ex.com");

        given().header("Authorization", "Bearer " + intruso)
                .when()
                .get("/api/v1/apus/" + apuId + "/especificacion-tecnica")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + intruso)
                .body(Map.of("texto", "intento de adulteración"))
                .when()
                .put("/api/v1/apus/" + apuId + "/especificacion-tecnica")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        // verificación negativa: el contenido del dueño sigue intacto
        given().header("Authorization", "Bearer " + dueno)
                .when()
                .get("/api/v1/apus/" + apuId + "/especificacion-tecnica")
                .then()
                .statusCode(200)
                .body("contenido", equalTo(null));
    }
}
