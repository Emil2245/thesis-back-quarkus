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
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    private String crearApu(String token, Long presupuestoId, String codigo) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("codigo", codigo, "descripcion", "Instalación", "unidad", "m"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/apus")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    /** UUIDv7 inexistente pero bien formado — usado para verificar 404 de la capa de owner. */
    private static final String UUID_INEXISTENTE_V7 = "0192f6c4-7c8a-7000-8000-000000000000";

    /** UUIDv4 (no v7) bien formado — usado para verificar la frontera de validación 400. */
    private static final String UUID_NO_V7 = "550e8400-e29b-41d4-a716-446655440000";

    /**
     * Resuelve el {@code BIGINT} interno de un APU a partir de su UUID público. Se usa
     * exclusivamente para sembrar filas SQL (p. ej. {@code rubro.apu_id}) que requieren el id
     * interno; los asserts de contrato y las URLs de los resources usan el UUID público.
     */
    private Long internalApuId(String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM apu WHERE public_id = ?")) {
            ps.setObject(1, java.util.UUID.fromString(publicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
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
        String apuId = crearApu(token, presupuestoId, "TB-001");

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
        String apuId = crearApu(token, presupuestoId, "HM-001");

        String detalleId = given().header("Authorization", "Bearer " + token)
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
        String apuId = crearApu(token, presupuestoId, "OV-001");

        String detalleId = given().contentType(JSON)
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
        String apuId = crearApu(token, presupuestoId, "PR-001");

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
        String apuId = crearApu(token, presupuestoId, "VD-001");

        // SQL necesita el BIGINT interno; la URL del resource usa el UUID público.
        insertarRubroVinculado(presupuestoId, internalApuId(apuId));

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
        String apuId = crearApu(dueno, presupuestoId, "AJ-001");

        String intruso = AuthSupport.registrarConToken(mailbox, "intruso@ex.com");
        given().header("Authorization", "Bearer " + intruso)
                .when()
                .get("/api/v1/apus/" + apuId)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    /**
     * WU-02C — N04 §A9 "copia al usar": cuando se agrega una fila de APU cuyo insumo
     * fuente es CENTRAL, el {@code insumoId} persistido en {@code apu_detalle} debe
     * apuntar a la copia PROYECTO del proyecto del APU, nunca al insumo CENTRAL
     * original. Aquí se siembra un insumo CENTRAL por SQL (la API de Super-Admin
     * para CRUD de centrales aún no existe), se llama al endpoint normal de
     * {@code agregarDetalle}, y se verifica vía SQL que la fila persistida vive
     * en la base PROYECTO del proyecto.
     */
    @Test
    void TC_WU02C_agregarDetalle_persiste_copia_proyecto_nunca_central() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "wu02c@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(token, presupuestoId, "WU02C-001");

        // 1. sembrar base CENTRAL e insumo CENTRAL vía SQL (la API admin no existe aún)
        long baseCentralId;
        long insumoCentralId;
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("INSERT INTO base_insumos (nombre, tipo, archivada) "
                    + "VALUES ('Central WU02C', 'CENTRAL', FALSE)");
            try (ResultSet rs = st.executeQuery("SELECT id FROM base_insumos WHERE nombre = 'Central WU02C'")) {
                rs.next();
                baseCentralId = rs.getLong(1);
            }
        }
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO insumo (base_id, codigo, tipo, descripcion, unidad, precio_unitario) "
                                + "VALUES (?, 'WC-CENT-1', 'MATERIAL', 'Cemento CENTRAL', 'kg', 0.650000) RETURNING id")) {
            ps.setLong(1, baseCentralId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                insumoCentralId = rs.getLong(1);
            }
        }

        // 2. POST /detalles con el insumo CENTRAL (origen)
        String detalleId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("seccionTipo", "MATERIAL", "insumoId", insumoCentralId, "cantidad", 1.0))
                .when()
                .post("/api/v1/apus/" + apuId + "/detalles")
                .then()
                .statusCode(201)
                .extract()
                .path("secciones[2].detalles[0].id");

        // 3. el insumoId reportado en el JSON NO es el CENTRAL
        Number insumoIdReportado = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .get("secciones[2].detalles[0].insumoId");
        org.junit.jupiter.api.Assertions.assertNotEquals(
                insumoCentralId,
                insumoIdReportado.longValue(),
                "el insumoId de la fila NO es el BIGINT del insumo CENTRAL");

        // 4. verificar por SQL que la fila vive en una base PROYECTO del proyecto
        long insumoEnDetalle;
        long baseDeInsumo;
        String tipoBase;
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT d.insumo_id, i.base_id, b.tipo "
                        + "FROM apu_detalle d "
                        + "JOIN insumo i ON i.id = d.insumo_id "
                        + "JOIN base_insumos b ON b.id = i.base_id "
                        + "WHERE d.public_id = ?")) {
            ps.setObject(1, java.util.UUID.fromString(detalleId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                insumoEnDetalle = rs.getLong(1);
                baseDeInsumo = rs.getLong(2);
                tipoBase = rs.getString(3);
            }
        }
        org.junit.jupiter.api.Assertions.assertNotEquals(
                insumoCentralId, insumoEnDetalle, "apu_detalle.insumo_id NO debe ser el CENTRAL original");
        org.junit.jupiter.api.Assertions.assertEquals(
                "PROYECTO", tipoBase, "la fila persistida vive en una base PROYECTO");
        org.junit.jupiter.api.Assertions.assertNotEquals(
                baseCentralId, baseDeInsumo, "el base_id del insumo persistido NO es la base CENTRAL");

        // 5. existe exactamente 1 insumo en la base PROYECTO del proyecto con codigo WC-CENT-1
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT count(*) FROM insumo i "
                        + "JOIN base_insumos b ON b.id = i.base_id "
                        + "WHERE b.proyecto_id = ? AND i.codigo = 'WC-CENT-1'")) {
            ps.setLong(1, proyectoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                org.junit.jupiter.api.Assertions.assertEquals(
                        1, rs.getLong(1), "la base PROYECTO del proyecto tiene exactamente 1 copia WC-CENT-1");
            }
        }
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
        String apuId = crearApu(token, presupuestoId, "PCT-001");

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
        String apuId = crearApu(token, presupuestoId, "ET-RT-001");

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
                .body("apuId", equalTo(apuId))
                .body("contenido", equalTo(texto));
    }

    @Test
    void TC_P45_02_et_null_y_vacio_limpian_contenido() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p45clr@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(token, presupuestoId, "ET-CLR-001");

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
                .body("apuId", equalTo(apuId))
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
        String apuId = crearApu(token, presupuestoId, "ET-MB-001");

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
        String apuId = crearApu(token, presupuestoId, "ET-BD-001");

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
        String apuId = crearApu(dueno, presupuestoId, "ET-AJ-001");

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

    @Test
    void TC_P46_01_duplicar_deep_copy_preserva_secciones_filas_orden_y_overrides() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p46deep@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        Long mo = crearInsumo(token, proyectoId, "MO-D-010", "MANO_OBRA", "Peón", "h", 4.0);
        Long mat = crearInsumo(token, proyectoId, "MA-D-010", "MATERIAL", "Tubo", "m", 2.5);
        String apuId = crearApu(token, presupuestoId, "DUP-SRC");

        // fila MO con override para que la copia preserve el override (no heredar)
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "seccionTipo", "MANO_OBRA", "insumoId", mo.intValue(), "cantidad", 2.0, "rendimiento", 1.5))
                .when()
                .post("/api/v1/apus/" + apuId + "/detalles")
                .then()
                .statusCode(201)
                .extract()
                .path("secciones[1].detalles[0].id");

        String moDetalleId = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId)
                .then()
                .statusCode(200)
                .extract()
                .path("secciones[1].detalles[0].id");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("precioOverride", 7.0))
                .when()
                .patch("/api/v1/apus/" + apuId + "/detalles/" + moDetalleId)
                .then()
                .statusCode(200)
                .body("secciones[1].detalles[0].precioEfectivo", comparesTo(new BigDecimal("7.0")))
                .body("secciones[1].detalles[0].precioHeredado", is(false));

        // fila MATERIAL sin override (heredada)
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("seccionTipo", "MATERIAL", "insumoId", mat.intValue(), "cantidad", 3.0))
                .when()
                .post("/api/v1/apus/" + apuId + "/detalles")
                .then()
                .statusCode(201);

        // duplicar
        String copiaId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of())
                .when()
                .post("/api/v1/apus/" + apuId + "/duplicar")
                .then()
                .statusCode(201)
                .body("codigo", equalTo("APU-002"))
                .body("descripcion", equalTo("Instalación"))
                .body("unidad", equalTo("m"))
                .body("secciones.size()", is(4))
                .body("secciones[0].tipo", equalTo("EQUIPO"))
                .body("secciones[1].tipo", equalTo("MANO_OBRA"))
                .body("secciones[2].tipo", equalTo("MATERIAL"))
                .body("secciones[3].tipo", equalTo("TRANSPORTE"))
                // HM copiada en bloque M (orden 1)
                .body("secciones[0].detalles[0].esHerramientaMenor", is(true))
                // fila MO copiada: cantidad, rendimiento, override preservados
                .body("secciones[1].detalles.size()", is(1))
                .body("secciones[1].detalles[0].cantidad", comparesTo(new BigDecimal("2.0")))
                .body("secciones[1].detalles[0].rendimiento", comparesTo(new BigDecimal("1.5")))
                .body("secciones[1].detalles[0].precioEfectivo", comparesTo(new BigDecimal("7.0")))
                .body("secciones[1].detalles[0].precioHeredado", is(false))
                .body("secciones[1].detalles[0].insumoId", is(mo.intValue()))
                // fila MATERIAL copiada: cantidad y precio heredado preservados
                .body("secciones[2].detalles.size()", is(1))
                .body("secciones[2].detalles[0].cantidad", comparesTo(new BigDecimal("3.0")))
                .body("secciones[2].detalles[0].precioEfectivo", comparesTo(new BigDecimal("2.5")))
                .body("secciones[2].detalles[0].precioHeredado", is(true))
                .body("secciones[2].detalles[0].insumoId", is(mat.intValue()))
                // totales recalculados: HM(0.05*7*2*1.5=1.05) + N(2*7*1.5=21) + O(3*2.5=7.5) = 29.55
                .body("costoDirecto", comparesTo(new BigDecimal("29.55")))
                .body("costoTotal", comparesTo(new BigDecimal("29.55")))
                .extract()
                .path("id");

        // código del duplicado NO debe coincidir con el del origen
        org.junit.jupiter.api.Assertions.assertNotEquals(apuId, copiaId);

        // verificación de IDs de detalle distintos (deep copy, no compartidos)
        java.util.List<java.util.List<String>> srcDetIdsNested = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .get("secciones.detalles.id");
        java.util.List<java.util.List<String>> copiaDetIdsNested = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + copiaId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .get("secciones.detalles.id");
        java.util.Set<String> srcDetIds = srcDetIdsNested.stream()
                .flatMap(java.util.Collection::stream)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Set<String> copiaDetIds = copiaDetIdsNested.stream()
                .flatMap(java.util.Collection::stream)
                .collect(java.util.stream.Collectors.toSet());
        org.junit.jupiter.api.Assertions.assertFalse(
                srcDetIds.stream().anyMatch(copiaDetIds::contains),
                "los IDs de detalle del origen no deben aparecer en la copia");
    }

    @Test
    void TC_P46_02_duplicar_copiarET_true_copia_especificacion_tecnica() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p46ettrue@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(token, presupuestoId, "ET-COPY-T-001");

        String et = "Dosificación 1:2:3, vibrado, curado 7 días.";

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("texto", et))
                .when()
                .put("/api/v1/apus/" + apuId + "/especificacion-tecnica")
                .then()
                .statusCode(200);

        String copiaId = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("copiarET", true))
                .when()
                .post("/api/v1/apus/" + apuId + "/duplicar")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // GET de la copia debe mostrar el mismo ET
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + copiaId + "/especificacion-tecnica")
                .then()
                .statusCode(200)
                .body("contenido", equalTo(et));
    }

    @Test
    void TC_P46_03_duplicar_copiarET_false_y_ausente_no_copian_especificacion() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p46etfalse@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(token, presupuestoId, "ET-COPY-F-001");

        String et = "Texto que NO debe copiarse a ninguna de las dos copias.";

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("texto", et))
                .when()
                .put("/api/v1/apus/" + apuId + "/especificacion-tecnica")
                .then()
                .statusCode(200);

        // copiarET=false explícito
        String copia1 = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("copiarET", false))
                .when()
                .post("/api/v1/apus/" + apuId + "/duplicar")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // body ausente (null) → default false
        String copia2 = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .when()
                .post("/api/v1/apus/" + apuId + "/duplicar")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + copia1 + "/especificacion-tecnica")
                .then()
                .statusCode(200)
                .body("contenido", equalTo(null));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + copia2 + "/especificacion-tecnica")
                .then()
                .statusCode(200)
                .body("contenido", equalTo(null));
    }

    @Test
    void TC_P46_04_duplicar_genera_codigo_unico_APU_n_sin_colision() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p46uniq@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(token, presupuestoId, "APU-001");

        // 1ª copia → APU-002
        String copia1 = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of())
                .when()
                .post("/api/v1/apus/" + apuId + "/duplicar")
                .then()
                .statusCode(201)
                .body("codigo", equalTo("APU-002"))
                .extract()
                .path("id");

        // 2ª copia → APU-003
        String copia2 = given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of())
                .when()
                .post("/api/v1/apus/" + apuId + "/duplicar")
                .then()
                .statusCode(201)
                .body("codigo", equalTo("APU-003"))
                .extract()
                .path("id");

        // duplicar la 1ª copia → APU-004
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of())
                .when()
                .post("/api/v1/apus/" + copia1 + "/duplicar")
                .then()
                .statusCode(201)
                .body("codigo", equalTo("APU-004"));

        // ninguna colisiona con el origen ni entre sí
        org.junit.jupiter.api.Assertions.assertNotEquals(apuId, copia1);
        org.junit.jupiter.api.Assertions.assertNotEquals(apuId, copia2);
        org.junit.jupiter.api.Assertions.assertNotEquals(copia1, copia2);

        // el presupuesto ahora tiene 4 APUs (origen + 3 copias)
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/apus")
                .then()
                .statusCode(200)
                .body("total", is(4));
    }

    @Test
    void TC_P46_05_duplicar_APU_de_otro_usuario_devuelve_404() throws Exception {
        String dueno = AuthSupport.registrarConToken(mailbox, "duenodup@ex.com");
        Long proyectoId = crearProyecto(dueno);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(dueno, presupuestoId, "AJ-DUP-001");

        String intruso = AuthSupport.registrarConToken(mailbox, "intrusodup@ex.com");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + intruso)
                .body(Map.of())
                .when()
                .post("/api/v1/apus/" + apuId + "/duplicar")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        // verificación negativa: el dueño aún tiene exactamente 1 APU
        given().header("Authorization", "Bearer " + dueno)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/apus")
                .then()
                .statusCode(200)
                .body("total", is(1));
    }

    @Test
    void TC_P46_06_duplicar_no_muta_el_origen_inmutabilidad() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p46inmut@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        Long mo = crearInsumo(token, proyectoId, "MO-IMM-010", "MANO_OBRA", "Peón", "h", 4.0);
        String apuId = crearApu(token, presupuestoId, "IMM-SRC");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "seccionTipo", "MANO_OBRA", "insumoId", mo.intValue(), "cantidad", 2.0, "rendimiento", 1.0))
                .when()
                .post("/api/v1/apus/" + apuId + "/detalles")
                .then()
                .statusCode(201);

        // snapshot del origen antes de duplicar
        var antes = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();

        Number cdAntes = antes.get("costoDirecto");
        Number totalAntes = antes.get("costoTotal");
        String codigoAntes = antes.get("codigo");
        int numSeccionesAntes = antes.getList("secciones").size();
        int numFilasMoAntes = antes.getList("secciones[1].detalles").size();

        // duplicar dos veces
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of())
                .when()
                .post("/api/v1/apus/" + apuId + "/duplicar")
                .then()
                .statusCode(201);
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("copiarET", true))
                .when()
                .post("/api/v1/apus/" + apuId + "/duplicar")
                .then()
                .statusCode(201);

        // verificación: el origen NO cambió tras las duplicaciones
        var despues = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();

        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.Objects.toString(antes.get("id")),
                java.util.Objects.toString(despues.get("id")),
                "id inmutable");
        org.junit.jupiter.api.Assertions.assertEquals(codigoAntes, despues.getString("codigo"), "codigo inmutable");
        org.junit.jupiter.api.Assertions.assertEquals(
                numSeccionesAntes, despues.getList("secciones").size(), "secciones.size inmutable");
        org.junit.jupiter.api.Assertions.assertEquals(
                numFilasMoAntes, despues.getList("secciones[1].detalles").size(), "filas MO inmutables");
        org.junit.jupiter.api.Assertions.assertEquals(
                cdAntes.doubleValue(),
                ((Number) despues.get("costoDirecto")).doubleValue(),
                0.0001,
                "costoDirecto inmutable");
        org.junit.jupiter.api.Assertions.assertEquals(
                totalAntes.doubleValue(),
                ((Number) despues.get("costoTotal")).doubleValue(),
                0.0001,
                "costoTotal inmutable");

        // presupuesto ahora tiene 3 APUs (origen + 2 copias)
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/apus")
                .then()
                .statusCode(200)
                .body("total", is(3));
    }

    @Test
    void TC_P27_01_calculo_shape_4_secciones_parametros_resumen() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p27shape@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        Long mo = crearInsumo(token, proyectoId, "MO-P27-1", "MANO_OBRA", "Peón", "h", 4.0);
        Long mat = crearInsumo(token, proyectoId, "MA-P27-1", "MATERIAL", "Tubo", "m", 2.0);
        String apuId = crearApu(token, presupuestoId, "P27-SHAPE");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "seccionTipo", "MANO_OBRA", "insumoId", mo.intValue(), "cantidad", 2.0, "rendimiento", 1.0))
                .when()
                .post("/api/v1/apus/" + apuId + "/detalles")
                .then()
                .statusCode(201);
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("seccionTipo", "MATERIAL", "insumoId", mat.intValue(), "cantidad", 3.0))
                .when()
                .post("/api/v1/apus/" + apuId + "/detalles")
                .then()
                .statusCode(201);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId + "/calculo")
                .then()
                .statusCode(200)
                .body("apuId", equalTo(apuId))
                .body("codigo", equalTo("P27-SHAPE"))
                .body("parametros.hm", comparesTo(new BigDecimal("0.0500")))
                .body("parametros.descuento", comparesTo(new BigDecimal("0.0000")))
                .body("parametros.ciAplicado", comparesTo(new BigDecimal("0.0000")))
                .body("secciones.size()", is(4))
                .body("secciones[0].tipo", equalTo("EQUIPO"))
                .body("secciones[1].tipo", equalTo("MANO_OBRA"))
                .body("secciones[2].tipo", equalTo("MATERIAL"))
                .body("secciones[3].tipo", equalTo("TRANSPORTE"))
                .body("secciones[0].lineas.size()", is(1))
                .body("secciones[0].lineas[0].esHerramientaMenor", is(true))
                .body("secciones[1].lineas.size()", is(1))
                .body("secciones[2].lineas.size()", is(1))
                .body("secciones[3].lineas.size()", is(0))
                .body("secciones[3].operacion", equalTo("0"))
                .body("resumen.cd", comparesTo(new BigDecimal("14.400000")))
                .body("resumen.cdAjustado", comparesTo(new BigDecimal("14.400000")))
                .body("resumen.ci", comparesTo(new BigDecimal("0.000000")))
                .body("resumen.ct", comparesTo(new BigDecimal("14.400000")));
    }

    @Test
    void TC_P27_02_calculo_valores_y_operaciones_a_6dp_con_porcentajes() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p27vals@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        Long mo = crearInsumo(token, proyectoId, "MO-P27-2", "MANO_OBRA", "Soldador", "h", 5.0);
        Long mat = crearInsumo(token, proyectoId, "MA-P27-2", "MATERIAL", "Cemento", "kg", 1.5);
        String apuId = crearApu(token, presupuestoId, "P27-VALS");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "seccionTipo", "MANO_OBRA", "insumoId", mo.intValue(), "cantidad", 2.0, "rendimiento", 1.0))
                .when()
                .post("/api/v1/apus/" + apuId + "/detalles")
                .then()
                .statusCode(201);
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("seccionTipo", "MATERIAL", "insumoId", mat.intValue(), "cantidad", 3.0))
                .when()
                .post("/api/v1/apus/" + apuId + "/detalles")
                .then()
                .statusCode(201);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("0.2000")
                .when()
                .patch("/api/v1/apus/" + apuId + "/porcentaje-indirecto")
                .then()
                .statusCode(200);
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("0.1000")
                .when()
                .patch("/api/v1/apus/" + apuId + "/porcentaje-descuento")
                .then()
                .statusCode(200);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId + "/calculo")
                .then()
                .statusCode(200)
                .body("parametros.hm", comparesTo(new BigDecimal("0.0500")))
                .body("parametros.ciDefault", equalTo(null))
                .body("parametros.ciAplicado", comparesTo(new BigDecimal("0.2000")))
                .body("parametros.descuento", comparesTo(new BigDecimal("0.1000")))
                .body("secciones[1].lineas[0].operacion", equalTo("2.000000 × 5.000000 × 1.000000"))
                .body("secciones[1].lineas[0].resultado", comparesTo(new BigDecimal("10.000000")))
                .body("secciones[1].operacion", equalTo("10.000000"))
                .body("secciones[1].subtotal", comparesTo(new BigDecimal("10.000000")))
                .body("secciones[1].resultado", comparesTo(new BigDecimal("10.000000")))
                .body("secciones[2].lineas[0].operacion", equalTo("3.000000 × 1.500000"))
                .body("secciones[2].lineas[0].resultado", comparesTo(new BigDecimal("4.500000")))
                .body("secciones[0].lineas[0].operacion", equalTo("0.050000 × 10.000000"))
                .body("secciones[0].lineas[0].resultado", comparesTo(new BigDecimal("0.500000")))
                .body("secciones[0].subtotal", comparesTo(new BigDecimal("0.500000")))
                .body("resumen.cd", comparesTo(new BigDecimal("15.000000")))
                .body("resumen.cdAjustado", comparesTo(new BigDecimal("13.500000")))
                .body("resumen.operacionCdAjustado", equalTo("15.000000 × 0.9000"))
                .body("resumen.ci", comparesTo(new BigDecimal("2.700000")))
                .body("resumen.ct", comparesTo(new BigDecimal("16.200000")));
    }

    @Test
    void TC_P27_03_calculo_excluye_tipos_internos_del_motor() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p27noexpose@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        Long mo = crearInsumo(token, proyectoId, "MO-P27-3", "MANO_OBRA", "Peón", "h", 4.0);
        String apuId = crearApu(token, presupuestoId, "P27-LEAK");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "seccionTipo", "MANO_OBRA", "insumoId", mo.intValue(), "cantidad", 1.0, "rendimiento", 1.0))
                .when()
                .post("/api/v1/apus/" + apuId + "/detalles")
                .then()
                .statusCode(201);

        var json = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId + "/calculo")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();

        @SuppressWarnings("unchecked")
        java.util.Set<String> bodyKeys =
                (java.util.Set<String>) (java.util.Set<?>) json.getMap("").keySet();
        org.junit.jupiter.api.Assertions.assertFalse(
                bodyKeys.contains("motor") || bodyKeys.contains("filas") || bodyKeys.contains("apu"),
                "no debe exponer nombres de campos internos del motor/aggregado");

        @SuppressWarnings("unchecked")
        java.util.Set<String> resumenKeys = (java.util.Set<String>)
                (java.util.Set<?>) json.getMap("resumen").keySet();
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.Set.of("cd", "cdAjustado", "operacionCdAjustado", "ci", "ct"),
                resumenKeys,
                "resumen expone exactamente los 5 campos del contrato");

        @SuppressWarnings("unchecked")
        java.util.Set<String> parametrosKeys = (java.util.Set<String>)
                (java.util.Set<?>) json.getMap("parametros").keySet();
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.Set.of("hm", "ciDefault", "ciAplicado", "descuento"),
                parametrosKeys,
                "parametros expone los 4 campos");

        @SuppressWarnings("unchecked")
        java.util.Set<String> seccionKeys = (java.util.Set<String>)
                (java.util.Set<?>) json.getMap("secciones[0]").keySet();
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.Set.of("tipo", "subtotal", "operacion", "resultado", "lineas"),
                seccionKeys,
                "seccion expone los 5 campos");

        @SuppressWarnings("unchecked")
        java.util.Set<String> lineaKeys = (java.util.Set<String>)
                (java.util.Set<?>) json.getMap("secciones[0].lineas[0]").keySet();
        org.junit.jupiter.api.Assertions.assertEquals(
                java.util.Set.of(
                        "detalleId",
                        "orden",
                        "seccion",
                        "esHerramientaMenor",
                        "insumoId",
                        "descripcion",
                        "cantidad",
                        "rendimiento",
                        "precioEfectivo",
                        "costoHora",
                        "operacion",
                        "resultado"),
                lineaKeys,
                "linea expone los 12 campos del contrato, sin tipos del motor");
    }

    @Test
    void TC_P27_04_calculo_de_otro_usuario_devuelve_404() throws Exception {
        String dueno = AuthSupport.registrarConToken(mailbox, "p27dueno@ex.com");
        Long proyectoId = crearProyecto(dueno);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(dueno, presupuestoId, "P27-AJ");

        String intruso = AuthSupport.registrarConToken(mailbox, "p27intruso@ex.com");

        given().header("Authorization", "Bearer " + intruso)
                .when()
                .get("/api/v1/apus/" + apuId + "/calculo")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        // UUIDv7 bien formado pero inexistente → 404 (la frontera 400 validacion ya se cubrió
        // arriba en el caso del intruso). Aquí ejercitamos la rama de "UUID válido pero
        // ningún APU del dueño coincide".
        given().header("Authorization", "Bearer " + dueno)
                .when()
                .get("/api/v1/apus/" + UUID_INEXISTENTE_V7 + "/calculo")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    // =========================================================================
    // WU-03 — frontera de validación de UUIDv7 en el path.
    //
    // El resource parsea el {apuId} con UuidV7.parse ANTES de tocar la BD
    // (ver ApuResource#resolverApu). Una entrada malformada o no-v7 debe
    // rechazarse con 400 validacion sin invocar al repositorio, de modo que
    // la superficie pública quede protegida incluso antes del scope de owner.
    // =========================================================================

    @Test
    void TC_WU03_01_path_uuid_v4_no_v7_devuelve_400_validacion_en_get() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "wu03v4@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + UUID_NO_V7)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_WU03_02_path_uuid_malformado_devuelve_400_validacion_en_todos_los_verbos() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "wu03mal@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);

        // basura que no es ni siquiera un UUID → 400 validacion antes de la BD
        String basura = "esto-no-es-un-uuid";

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + basura)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // la misma validación se aplica al PATCH del porcentaje indirecto
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body("0.1000")
                .when()
                .patch("/api/v1/apus/" + basura + "/porcentaje-indirecto")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // DELETE sobre UUID basura también es 400 (no 404) — la validación precede al lookup
        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/apus/" + basura)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        // POST duplicar también — la frontera es uniforme
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of())
                .when()
                .post("/api/v1/apus/" + basura + "/duplicar")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_WU03_03_path_uuid_v7_inexistente_devuelve_404_no_encontrado() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "wu03nf@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);

        // UUIDv7 bien formado pero que no existe en BD → 404 (no 400)
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + UUID_INEXISTENTE_V7)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/apus/" + UUID_INEXISTENTE_V7)
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    // =========================================================================
    // Plan 03 — Cerrar el contrato APU actual (P-21 reordenamiento atómico).
    //
    // El PATCH de detalle acepta `orden` (JsonNullable<Integer>). La semántica
    // es MOVE atómico dentro de la sección: los hermanos entre old→new se
    // desplazan ±1 para preservar contigüidad 1..count. Validar el orden
    // omitido no produce cambios; orden fuera de [1..count] devuelve 400;
    // editar campos no-orden sobre HM sigue protegido (409 fila-protegida);
    // borrar HM sigue 409.
    // =========================================================================

    private List<String> agregarFilasMoSecuenciales(String token, String apuId, Long proyectoId, int cantidad)
            throws Exception {
        List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            String codigo = String.format("MO-RNG-%02d", i);
            Long insumoId = crearInsumo(token, proyectoId, codigo, "MANO_OBRA", "Peón " + codigo, "h", 4.0);
            String id = given().contentType(JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(Map.of(
                            "seccionTipo",
                            "MANO_OBRA",
                            "insumoId",
                            insumoId.intValue(),
                            "cantidad",
                            1.0,
                            "rendimiento",
                            1.0))
                    .when()
                    .post("/api/v1/apus/" + apuId + "/detalles")
                    .then()
                    .statusCode(201)
                    .extract()
                    .path("secciones[1].detalles[" + i + "].id");
            ids.add(id);
        }
        return ids;
    }

    private static List<Integer> ordenSeccion(String token, String apuId, int idx) {
        return given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("secciones[" + idx + "].detalles.orden", Integer.class);
    }

    private static List<String> idSeccion(String token, String apuId, int idx) {
        return given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("secciones[" + idx + "].detalles.id");
    }

    /**
     * a) `orden` omitido no cambia nada. PATCH con solo `cantidad` deja el
     * orden de la sección intacto (validación de la semántica 3-state de
     * {@code JsonNullable}: omitido ≠ null explícito).
     */
    @Test
    void TC_P21_03_orden_omitido_no_cambia_orden_de_la_seccion() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p21omit@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(token, presupuestoId, "RNG-OMIT");
        List<String> ids = agregarFilasMoSecuenciales(token, apuId, proyectoId, 3);

        org.junit.jupiter.api.Assertions.assertEquals(List.of(1, 2, 3), ordenSeccion(token, apuId, 1));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("cantidad", 2.5))
                .when()
                .patch("/api/v1/apus/" + apuId + "/detalles/" + ids.get(1))
                .then()
                .statusCode(200);

        org.junit.jupiter.api.Assertions.assertEquals(List.of(1, 2, 3), ordenSeccion(token, apuId, 1));
    }

    /**
     * b) MOVE atómico al subir (new < old): los hermanos con orden en
     * [new, old) incrementan en 1. Verifica el shape en {@code GET /calculo}.
     */
    @Test
    void TC_P21_04_orden_sube_desplaza_rango_y_calculo_respeta_orden() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p21up@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(token, presupuestoId, "RNG-UP");
        List<String> ids = agregarFilasMoSecuenciales(token, apuId, proyectoId, 4);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("orden", 1))
                .when()
                .patch("/api/v1/apus/" + apuId + "/detalles/" + ids.get(3))
                .then()
                .statusCode(200);

        List<String> ordenadosPorId = idSeccion(token, apuId, 1);
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of(ids.get(3), ids.get(0), ids.get(1), ids.get(2)), ordenadosPorId, "rango desplazado al subir");
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of(1, 2, 3, 4), ordenSeccion(token, apuId, 1), "contiguo 1..4");

        List<Integer> ordenCalculo = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId + "/calculo")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("secciones[1].lineas.orden", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of(1, 2, 3, 4), ordenCalculo, "/calculo respeta el orden persistido");

        String detalleIdOrden1 = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId + "/calculo")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("secciones[1].lineas.find { it.orden == 1 }.detalleId");
        org.junit.jupiter.api.Assertions.assertEquals(ids.get(3), detalleIdOrden1);
    }

    /**
     * Variante simétrica: MOVE atómico al bajar (new > old): los hermanos
     * con orden en (old, new] decrementan en 1.
     */
    @Test
    void TC_P21_05_orden_baja_desplaza_rango_inverso() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p21down@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(token, presupuestoId, "RNG-DOWN");
        List<String> ids = agregarFilasMoSecuenciales(token, apuId, proyectoId, 4);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("orden", 4))
                .when()
                .patch("/api/v1/apus/" + apuId + "/detalles/" + ids.get(0))
                .then()
                .statusCode(200);

        List<String> ordenadosPorId = idSeccion(token, apuId, 1);
        org.junit.jupiter.api.Assertions.assertEquals(
                List.of(ids.get(1), ids.get(2), ids.get(3), ids.get(0)), ordenadosPorId, "rango desplazado al bajar");
        org.junit.jupiter.api.Assertions.assertEquals(List.of(1, 2, 3, 4), ordenSeccion(token, apuId, 1));
    }

    /** MOVE a la misma posición es no-op (idempotente). */
    @Test
    void TC_P21_06_orden_igual_a_actual_es_noop() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p21noop@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(token, presupuestoId, "RNG-NOOP");
        List<String> ids = agregarFilasMoSecuenciales(token, apuId, proyectoId, 3);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("orden", 2))
                .when()
                .patch("/api/v1/apus/" + apuId + "/detalles/" + ids.get(1))
                .then()
                .statusCode(200);

        org.junit.jupiter.api.Assertions.assertEquals(
                List.of(ids.get(0), ids.get(1), ids.get(2)), idSeccion(token, apuId, 1));
        org.junit.jupiter.api.Assertions.assertEquals(List.of(1, 2, 3), ordenSeccion(token, apuId, 1));
    }

    /**
     * c) HM: moverla por `orden` SÍ se permite (Plan 03 / N04 §A3). Pero
     * PATCH con campos editables no-orden sigue siendo 409 fila-protegida,
     * y DELETE sigue siendo 409 fila-protegida. Tras el MOVE, el cálculo
     * respeta la nueva posición.
     */
    @Test
    void TC_P21_07_hm_reordenable_pero_protegida_contra_editar_y_borrar() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p21hmord@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(token, presupuestoId, "HM-ORD");

        Long eq1 = crearInsumo(token, proyectoId, "EQ-RNG-1", "EQUIPO", "Compactador", "h", 5.0);
        Long eq2 = crearInsumo(token, proyectoId, "EQ-RNG-2", "EQUIPO", "Vibrador", "h", 6.0);
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("seccionTipo", "EQUIPO", "insumoId", eq1.intValue(), "cantidad", 1.0, "rendimiento", 1.0))
                .when()
                .post("/api/v1/apus/" + apuId + "/detalles")
                .then()
                .statusCode(201);
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("seccionTipo", "EQUIPO", "insumoId", eq2.intValue(), "cantidad", 1.0, "rendimiento", 1.0))
                .when()
                .post("/api/v1/apus/" + apuId + "/detalles")
                .then()
                .statusCode(201);

        String hmId = idSeccion(token, apuId, 0).get(0);
        org.junit.jupiter.api.Assertions.assertEquals(List.of(1, 2, 3), ordenSeccion(token, apuId, 0));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("orden", 3))
                .when()
                .patch("/api/v1/apus/" + apuId + "/detalles/" + hmId)
                .then()
                .statusCode(200);

        org.junit.jupiter.api.Assertions.assertEquals(List.of(1, 2, 3), ordenSeccion(token, apuId, 0));
        String hmTras = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getString("secciones[0].detalles.find { it.orden == 3 }.id");
        org.junit.jupiter.api.Assertions.assertEquals(hmId, hmTras);

        List<Integer> ordenCalculoEq = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId + "/calculo")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("secciones[0].lineas.orden", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(List.of(1, 2, 3), ordenCalculoEq);
        org.junit.jupiter.api.Assertions.assertTrue(given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/apus/" + apuId + "/calculo")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getBoolean("secciones[0].lineas.find { it.orden == 3 }.esHerramientaMenor"));

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("cantidad", 2.0))
                .when()
                .patch("/api/v1/apus/" + apuId + "/detalles/" + hmId)
                .then()
                .statusCode(409)
                .body("codigo", equalTo("fila-protegida"));

        given().header("Authorization", "Bearer " + token)
                .when()
                .delete("/api/v1/apus/" + apuId + "/detalles/" + hmId)
                .then()
                .statusCode(409)
                .body("codigo", equalTo("fila-protegida"));
    }

    /**
     * d) Fronteras de validación del `orden`: null, 0 o > count → 400 validacion.
     * Cobertura parametrizada para que ningún cambio futuro del rango válido
     * pase silenciosamente.
     */
    @ParameterizedTest(name = "orden inválido: {0}")
    @ValueSource(ints = {0, -1, 99})
    void TC_P21_08_orden_fuera_de_rango_rechaza_400(int nuevoOrden) throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p21rng" + nuevoOrden + "@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        String apuId = crearApu(token, presupuestoId, "RNG-BAD");
        List<String> ids = agregarFilasMoSecuenciales(token, apuId, proyectoId, 3);

        // `nuevoOrden == 0` modela `{"orden": null}` explícito.
        String body = nuevoOrden == 0
                ? "{\"orden\": null}"
                : "{\"orden\":" + nuevoOrden + "}";
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(body)
                .when()
                .patch("/api/v1/apus/" + apuId + "/detalles/" + ids.get(0))
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));

        org.junit.jupiter.api.Assertions.assertEquals(List.of(1, 2, 3), ordenSeccion(token, apuId, 1));
    }
}
