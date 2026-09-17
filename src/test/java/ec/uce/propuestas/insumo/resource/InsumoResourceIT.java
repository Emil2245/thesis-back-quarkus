package ec.uce.propuestas.insumo.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;

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

/**
 * Plan 07 — los path params de {@code /proyectos/{proyectoId}/insumos/*} son
 * UUIDv7 (identidad externa inmutable, columna {@code public_id}). El id del
 * JSON es siempre el {@code publicId} UUIDv7, nunca el {@code BIGINT} interno.
 * UUID mal formado → 400 {@code validacion}; recurso ajeno → 404
 * {@code no-encontrado}.
 */
@QuarkusTest
class InsumoResourceIT {

    private static final String UUID_V7 =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

    /** UUIDv4 (no v7) bien formado — usado para verificar la frontera de validación 400. */
    private static final String UUID_NO_V7 = "550e8400-e29b-41d4-a716-446655440000";

    /** UUIDv7 inexistente pero bien formado — usado para verificar 404. */
    private static final String UUID_INEXISTENTE_V7 = "0192f6c4-7c8a-7000-8000-000000000000";

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
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, "
                    + "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    private String crearProyecto(String token) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto", "Riego La Virginia",
                        "anio", (short) 2026,
                        "plazoEjecucion", (short) 4,
                        "plazoUnidad", "MES",
                        "direccionInstitucional", "GAD"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    @Test
    void TC_P13_crear_y_listar_insumos_de_la_base_del_proyecto() {
        String token = AuthSupport.registrarConToken(mailbox, "insumos@ex.com");
        String proyecto = crearProyecto(token);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "codigo",
                        "MAT-001",
                        "tipo",
                        "MATERIAL",
                        "descripcion",
                        "Cemento",
                        "unidad",
                        "kg",
                        "precioUnitario",
                        0.650000))
                .when()
                .post("/api/v1/proyectos/" + proyecto + "/insumos")
                .then()
                .statusCode(201)
                .body("codigo", equalTo("MAT-001"))
                .body("tipo", equalTo("MATERIAL"))
                .body("id", matchesPattern(UUID_V7));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + proyecto + "/insumos")
                .then()
                .statusCode(200)
                .body("items.size()", is(1))
                .body("total", is(1))
                .body("items[0].id", matchesPattern(UUID_V7));
    }

    @Test
    void TC_P13_codigo_duplicado_en_una_base_devuelve_400() {
        String token = AuthSupport.registrarConToken(mailbox, "dup@ex.com");
        String proyecto = crearProyecto(token);
        String url = "/api/v1/proyectos/" + proyecto + "/insumos";

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "codigo",
                        "DUP-1",
                        "tipo",
                        "MATERIAL",
                        "descripcion",
                        "A",
                        "unidad",
                        "kg",
                        "precioUnitario",
                        1.0))
                .when()
                .post(url)
                .then()
                .statusCode(201);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "codigo",
                        "DUP-1",
                        "tipo",
                        "MATERIAL",
                        "descripcion",
                        "A",
                        "unidad",
                        "kg",
                        "precioUnitario",
                        1.0))
                .when()
                .post(url)
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P06_fuerza_unidad_h_para_mano_de_obra() {
        String token = AuthSupport.registrarConToken(mailbox, "mo@ex.com");
        String proyecto = crearProyecto(token);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "codigo",
                        "MO-1",
                        "tipo",
                        "MANO_OBRA",
                        "descripcion",
                        "Peón",
                        "unidad",
                        "h",
                        "precioUnitario",
                        3.50))
                .when()
                .post("/api/v1/proyectos/" + proyecto + "/insumos")
                .then()
                .statusCode(201);
    }

    @Test
    void TC_P07_path_uuid_v4_no_v7_en_path_proyecto_devuelve_400_validacion() {
        String token = AuthSupport.registrarConToken(mailbox, "wu07-ins-v4@ex.com");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + UUID_NO_V7 + "/insumos")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    @Test
    void TC_P07_path_uuid_v7_inexistente_en_path_proyecto_devuelve_404() {
        String token = AuthSupport.registrarConToken(mailbox, "wu07-ins-inex@ex.com");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/proyectos/" + UUID_INEXISTENTE_V7 + "/insumos")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    // =====================================================================
    // Plan 032 (P-18) — GET /proyectos/{id}/insumos/{id}/usos
    //
    // El endpoint estaba enrutado y validaba permisos, pero devolvía
    // `List.of()` literal. Estos tests afirman el CONTENIDO, no la forma:
    // un test que sólo comprueba «devuelve una lista» pasa igual con el
    // stub vacío.
    // =====================================================================

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

    /**
     * Plan 021 — el presupuesto v1 vigente lo crea {@code POST /proyectos}.
     * Este helper sólo lee su {@code publicId} UUIDv7.
     */
    private String presupuestoDeProyecto(String proyectoPublicId) throws Exception {
        return unaColumna(
                "SELECT p.public_id FROM presupuesto p JOIN proyecto pr ON pr.id = p.proyecto_id "
                        + "WHERE pr.public_id = ? AND p.version = 1",
                UUID.fromString(proyectoPublicId));
    }

    private String crearApu(String token, String presupuestoId, String codigo, String descripcion) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("codigo", codigo, "descripcion", descripcion, "unidad", "m3"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/apus")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    /**
     * Agrega una fila al APU y devuelve el {@code publicId} del detalle creado.
     * El índice de sección es el canónico 0=EQUIPO, 1=MANO_OBRA, 2=MATERIAL,
     * 3=TRANSPORTE; en EQUIPO la fila 0 es la de herramienta menor.
     */
    private String agregarDetalle(
            String token,
            String apuId,
            String seccionTipo,
            String insumoId,
            double cantidad,
            Double rendimiento,
            int indiceSeccion,
            int indiceDetalle) {
        var body = new java.util.HashMap<String, Object>();
        body.put("seccionTipo", seccionTipo);
        body.put("insumoId", insumoId);
        body.put("cantidad", cantidad);
        if (rendimiento != null) body.put("rendimiento", rendimiento);

        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(body)
                .when()
                .post("/api/v1/apus/" + apuId + "/detalles")
                .then()
                .statusCode(201)
                .extract()
                .path("secciones[" + indiceSeccion + "].detalles[" + indiceDetalle + "].id");
    }

    private String unaColumna(String sql, Object parametro) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, parametro);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private String usosUrl(String proyectoId, String insumoId) {
        return "/api/v1/proyectos/" + proyectoId + "/insumos/" + insumoId + "/usos";
    }

    /** 1 — un insumo usado en un APU devuelve una entrada con los datos del APU. */
    @Test
    void TC_P18_01_insumo_usado_en_un_apu_devuelve_una_entrada() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p18-uso@ex.com");
        String proyecto = crearProyecto(token);
        String presupuesto = presupuestoDeProyecto(proyecto);
        String insumo = crearInsumo(token, proyecto, "MAT-100", "MATERIAL", "Cemento", "kg", 0.65);
        String apu = crearApu(token, presupuesto, "APU-100", "Hormigón simple");
        agregarDetalle(token, apu, "MATERIAL", insumo, 2.0, null, 2, 0);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get(usosUrl(proyecto, insumo))
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].apuId", equalTo(apu))
                .body("[0].apuId", matchesPattern(UUID_V7))
                .body("[0].codigo", equalTo("APU-100"))
                .body("[0].descripcion", equalTo("Hormigón simple"))
                .body("[0].bloque", equalTo("O"))
                .body("[0].override", is(false));
    }

    /**
     * 2 — bloque y override se leen por sección: MATERIAL con
     * {@code precio_unitario_tarifa} → bloque O + override; EQUIPO sin
     * {@code tarifa_jornal} → bloque M + heredado. Es el corazón de P-18.
     */
    @Test
    void TC_P18_02_bloque_y_override_dependen_de_la_seccion_de_la_fila() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p18-bloque@ex.com");
        String proyecto = crearProyecto(token);
        String presupuesto = presupuestoDeProyecto(proyecto);
        String mat = crearInsumo(token, proyecto, "MAT-200", "MATERIAL", "Tubo", "m", 2.0);
        String eq = crearInsumo(token, proyecto, "EQ-200", "EQUIPO", "Vibrador", "h", 3.0);
        String apu = crearApu(token, presupuesto, "APU-200", "Instalación");

        String detalleMat = agregarDetalle(token, apu, "MATERIAL", mat, 3.0, null, 2, 0);
        // La fila 0 de EQUIPO es la de herramienta menor: la nueva es la 1.
        agregarDetalle(token, apu, "EQUIPO", eq, 1.0, 1.0, 0, 1);

        // Precio manual sólo en la fila MATERIAL.
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("precioOverride", 9.5))
                .when()
                .patch("/api/v1/apus/" + apu + "/detalles/" + detalleMat)
                .then()
                .statusCode(200);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get(usosUrl(proyecto, mat))
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].bloque", equalTo("O"))
                .body("[0].override", is(true));

        given().header("Authorization", "Bearer " + token)
                .when()
                .get(usosUrl(proyecto, eq))
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].bloque", equalTo("M"))
                .body("[0].override", is(false));
    }

    /** 3 — un insumo sin usos devuelve lista vacía con 200, no 404. */
    @Test
    void TC_P18_03_insumo_sin_usos_devuelve_lista_vacia_con_200() {
        String token = AuthSupport.registrarConToken(mailbox, "p18-sinuso@ex.com");
        String proyecto = crearProyecto(token);
        String insumo = crearInsumo(token, proyecto, "MAT-300", "MATERIAL", "Arena", "m3", 12.0);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get(usosUrl(proyecto, insumo))
                .then()
                .statusCode(200)
                .body("size()", is(0));
    }

    /**
     * 4 — aislamiento entre proyectos. {@code apu_detalle} no lleva proyecto
     * encima y su FK a {@code insumo} no está acotada por proyecto, así que el
     * filtro por {@code proyectoId} es lo único que impide enseñar los APUs del
     * otro proyecto (RNF-05). La segunda mitad fuerza por SQL la referencia
     * cruzada que el flujo normal («copia al usar», N04 §A9) nunca produce:
     * sin ese empujón el filtro no se ejercita y se cae en cualquier refactor.
     */
    @Test
    void TC_P18_04_los_usos_no_cruzan_entre_proyectos() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p18-aisla@ex.com");

        String proyectoA = crearProyecto(token);
        String presupuestoA = presupuestoDeProyecto(proyectoA);
        String insumoA = crearInsumo(token, proyectoA, "MAT-500", "MATERIAL", "Cemento", "kg", 0.65);
        String apuA = crearApu(token, presupuestoA, "APU-A", "Obra en A");
        agregarDetalle(token, apuA, "MATERIAL", insumoA, 1.0, null, 2, 0);

        String proyectoB = crearProyecto(token);
        String presupuestoB = presupuestoDeProyecto(proyectoB);
        String insumoB = crearInsumo(token, proyectoB, "MAT-500", "MATERIAL", "Cemento", "kg", 0.65);
        String apuB = crearApu(token, presupuestoB, "APU-B", "Obra en B");
        String detalleB = agregarDetalle(token, apuB, "MATERIAL", insumoB, 1.0, null, 2, 0);

        // Copias independientes: cada proyecto ve sólo su propio APU.
        given().header("Authorization", "Bearer " + token)
                .when()
                .get(usosUrl(proyectoA, insumoA))
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].codigo", equalTo("APU-A"));

        // Referencia cruzada forzada: la fila de B pasa a apuntar al insumo de A.
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE apu_detalle SET insumo_id = (SELECT id FROM insumo WHERE public_id = ?) "
                                + "WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(insumoA));
            ps.setObject(2, UUID.fromString(detalleB));
            org.junit.jupiter.api.Assertions.assertEquals(1, ps.executeUpdate());
        }

        // El filtro por proyecto sigue dejando fuera el APU de B.
        given().header("Authorization", "Bearer " + token)
                .when()
                .get(usosUrl(proyectoA, insumoA))
                .then()
                .statusCode(200)
                .body("size()", is(1))
                .body("[0].codigo", equalTo("APU-A"));
    }

    /** 5 — RNF-05 owner-to-404: otro usuario no distingue «ajeno» de «inexistente». */
    @Test
    void TC_P18_05_usos_de_insumo_ajeno_devuelve_404_no_403() throws Exception {
        String dueno = AuthSupport.registrarConToken(mailbox, "p18-dueno@ex.com");
        String proyecto = crearProyecto(dueno);
        String presupuesto = presupuestoDeProyecto(proyecto);
        String insumo = crearInsumo(dueno, proyecto, "MAT-600", "MATERIAL", "Grava", "m3", 15.0);
        String apu = crearApu(dueno, presupuesto, "APU-600", "Obra ajena");
        agregarDetalle(dueno, apu, "MATERIAL", insumo, 1.0, null, 2, 0);

        String ajeno = AuthSupport.registrarConToken(mailbox, "p18-ajeno@ex.com");

        given().header("Authorization", "Bearer " + ajeno)
                .when()
                .get(usosUrl(proyecto, insumo))
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }
}
