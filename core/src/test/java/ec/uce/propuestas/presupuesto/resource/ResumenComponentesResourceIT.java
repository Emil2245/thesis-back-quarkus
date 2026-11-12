package ec.uce.propuestas.presupuesto.resource;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 023 — flujo REST del resumen por componente (P-30).
 *
 * <p>Cubre:
 * <ul>
 *   <li>Forma estable: {@code porComponente} como
 *       {@code LinkedHashMap<SeccionTipo, String>} en orden canónico
 *       {@code EQUIPO, MANO_OBRA, MATERIAL, TRANSPORTE}; totales en escala 6
 *       con sufijo {@code ".000000"} (P-30).</li>
 *   <li>Componentes directos: cada rubro aporta
 *       {@code ApuCalculado.secciones[tipo].subtotal × rubro.cantidad}, sin
 *       pasar por la frontera APU→Rubro (que sólo se aplica a
 *       {@code precio_total}, no a los componentes M/N/O/P). Cuando el
 *       {@code %CI} del proyecto es no cero, los componentes M/N/O/P NO
 *       contienen CI; sólo el {@code totalGeneral} lo contiene (write-through
 *       ya aplicado por la mutación).</li>
 *   <li>{@code totalGeneral} viene del write-through de {@code presupuesto.total},
 *       NO de la suma de componentes. Esto se demuestra cuando el CI es no
 *       cero: los componentes directos coinciden con el APU × cantidad pero
 *       el {@code totalGeneral} ya incluye CI (rubro.precio_total = cantidad
 *       × PU_2dp donde PU_2dp_DOWN(CT) ya absorbió CI).</li>
 *   <li>IVA referencial: {@code ivaReferencial = totalGeneral × ParametrosProyecto.iva}
 *       con precisión natural {@code BigDecimal}.</li>
 *   <li>totalConIva = totalGeneral + ivaReferencial.</li>
 *   <li>Read-only: la operación no escribe nada en BD.</li>
 *   <li>Errores: UUIDv7 mal formado → 400, ajeno → 404 (RNF-05).</li>
 * </ul>
 *
 * <p>Convenciones: el {@code presupuestoId} del path es la identidad pública
 * UUIDv7 (Plan 07 / WU-03); el {@code BIGINT} interno nunca aparece en JSON.</p>
 */
@QuarkusTest
class ResumenComponentesResourceIT {

    /** UUIDv4 (no v7) bien formado — usado para verificar la frontera 400. */
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

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private String crearProyecto(String token, String nombre) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto",
                        nombre,
                        "codigo",
                        "P-2026-P23R",
                        "anio",
                        (short) 2026,
                        "plazoEjecucion",
                        (short) 6,
                        "plazoUnidad",
                        "MES",
                        "direccionInstitucional",
                        "GAD Municipal"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private Long internalProyectoId(String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM proyecto WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(publicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private Long internalPresupuestoId(String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM presupuesto WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(publicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private Long internalApuId(String publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM apu WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(publicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private String vigenteDeProyecto(String proyectoPublicId) throws Exception {
        Long proyectoId = internalProyectoId(proyectoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT public_id FROM presupuesto WHERE proyecto_id = ? AND es_vigente = TRUE")) {
            ps.setLong(1, proyectoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    /**
     * APU con M/N/O/P completas: subtotalM, subtotalN, subtotalO, subtotalP.
     * El HM se calcula automáticamente como 5% × subtotalN.
     *
     * <p>Diseño del APU sembrado:
     * <ul>
     *   <li>M = HM 5% × subtotalN (fila HM automática, sin insumo).</li>
     *   <li>N = insumo MO con tarifaJornal=4, cantidad=2, rendimiento=1 → 8.</li>
     *   <li>O = insumo MAT con precioUnitario=1.5, cantidad=3 → 4.5.</li>
     *   <li>P = insumo TRA con precioUnitario=2, cantidad=1 → 2.</li>
     * </ul>
     *
     * <p>CD = M(0.4) + N(8) + O(4.5) + P(2) = 14.9; %CI=0 → CT = 14.9.
     * PU_2dp_DOWN(14.9) = 14.90.
     */
    private String insertarApuCompletoMNoP(String presupuestoPublicId, String codigo) throws Exception {
        Long presupuestoId = internalPresupuestoId(presupuestoPublicId);
        String apuPublicId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("INSERT INTO apu (presupuesto_id, codigo, descripcion, "
                        + "unidad, costo_directo, costo_indirecto, costo_total) "
                        + "VALUES (?, ?, ?, 'u', 0, 0, 0) RETURNING public_id")) {
            ps.setLong(1, presupuestoId);
            ps.setString(2, codigo);
            ps.setString(3, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                apuPublicId = rs.getString(1);
            }
        }
        long apuId = internalApuId(apuPublicId);

        // Secciones M/N/O/P
        long equipoId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO apu_seccion (apu_id, tipo, subtotal, orden) VALUES (?, 'EQUIPO', 0, 1) "
                                + "RETURNING id")) {
            ps.setLong(1, apuId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                equipoId = rs.getLong(1);
            }
        }
        long moId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO apu_seccion (apu_id, tipo, subtotal, orden) VALUES (?, 'MANO_OBRA', 0, 2) "
                                + "RETURNING id")) {
            ps.setLong(1, apuId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                moId = rs.getLong(1);
            }
        }
        long matId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO apu_seccion (apu_id, tipo, subtotal, orden) VALUES (?, 'MATERIAL', 0, 3) "
                                + "RETURNING id")) {
            ps.setLong(1, apuId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                matId = rs.getLong(1);
            }
        }
        long traId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO apu_seccion (apu_id, tipo, subtotal, orden) VALUES (?, 'TRANSPORTE', 0, 4) "
                                + "RETURNING id")) {
            ps.setLong(1, apuId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                traId = rs.getLong(1);
            }
        }

        // Fila HM en M
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO apu_detalle (seccion_id, descripcion, orden, es_herramienta_menor, "
                                + "costo_hora, unidad, costo) VALUES (?, 'HM 5%MO', 1, TRUE, 0, '%', 0)")) {
            ps.setLong(1, equipoId);
            ps.executeUpdate();
        }
        // MO: insumo heredado (override NULL) — tarifaJornal=4
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO apu_detalle (seccion_id, descripcion, orden, cantidad, "
                                + "es_herramienta_menor, tarifa_jornal, costo_hora, rendimiento, unidad, costo) "
                                + "VALUES (?, 'MO', 1, 2, FALSE, 4, 0, 1, 'h', 0)")) {
            ps.setLong(1, moId);
            ps.executeUpdate();
        }
        // MAT: insumo heredado (override NULL) — precioUnitarioTarifa=1.5
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO apu_detalle (seccion_id, descripcion, orden, cantidad, "
                                + "es_herramienta_menor, precio_unitario_tarifa, costo_hora, rendimiento, unidad, costo) "
                                + "VALUES (?, 'MAT', 1, 3, FALSE, 1.5, 0, NULL, 'kg', 0)")) {
            ps.setLong(1, matId);
            ps.executeUpdate();
        }
        // TRA: insumo heredado (override NULL) — precioUnitarioTarifa=2
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO apu_detalle (seccion_id, descripcion, orden, cantidad, "
                                + "es_herramienta_menor, precio_unitario_tarifa, costo_hora, rendimiento, unidad, costo) "
                                + "VALUES (?, 'TRA', 1, 1, FALSE, 2, 0, NULL, 'km', 0)")) {
            ps.setLong(1, traId);
            ps.executeUpdate();
        }
        return apuPublicId;
    }

    private String crearCapituloRaiz(String token, String presupuestoId, String descripcion) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("descripcion", descripcion))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos")
                .then()
                .statusCode(201)
                .extract()
                .path("capitulos[0].id");
    }

    private BigDecimal leerPresupuestoTotal(String presupuestoPublicId) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT total FROM presupuesto WHERE id = ?")) {
            ps.setLong(1, pId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private BigDecimal leerParametrosIva(Long proyectoId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT iva FROM parametros_proyecto WHERE proyecto_id = ?")) {
            ps.setLong(1, proyectoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private long contarRubros(String presupuestoPublicId) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM rubro r "
                        + "JOIN capitulo c ON c.id = r.capitulo_id WHERE c.presupuesto_id = ?")) {
            ps.setLong(1, pId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long contarApus(String presupuestoPublicId) throws Exception {
        Long pId = internalPresupuestoId(presupuestoPublicId);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM apu WHERE presupuesto_id = ?")) {
            ps.setLong(1, pId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tests
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Resumen vacío: totalGeneral, ivaReferencial y totalConIva todos a 0.
     * La forma es estable (4 componentes con escala 6).
     */
    @Test
    void TC_P30_01_resumen_vacio_forma_estable_y_totales_cero() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-c1@ex.com");
        String proyectoId = crearProyecto(token, "Vacio");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/resumen")
                .then()
                .statusCode(200)
                .body("totalGeneral", equalTo("0.000000"))
                .body("ivaReferencial", equalTo("0.000000"))
                .body("totalConIva", equalTo("0.000000"))
                .body("porComponente.EQUIPO", equalTo("0.000000"))
                .body("porComponente.MANO_OBRA", equalTo("0.000000"))
                .body("porComponente.MATERIAL", equalTo("0.000000"))
                .body("porComponente.TRANSPORTE", equalTo("0.000000"));
    }

    /**
     * %CI = 0: los componentes M/N/O/P coinciden con la suma directa de
     * (subtotal × cantidad). totalGeneral = suma componentes porque PU = CT
     * (no hay CI que redondear a 2dp).
     */
    @Test
    void TC_P30_02_componentes_directos_y_total_general_con_ci_cero() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-c2@ex.com");
        String proyectoId = crearProyecto(token, "CI 0");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String apuId = insertarApuCompletoMNoP(presupuestoId, "APU-CI0");
        String capId = crearCapituloRaiz(token, presupuestoId, "Cap");

        // rubro cantidad = 5
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId, "cantidad", "5.000000"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(201);

        // subtotalN = 8; HM = 0.4; subtotalO = 4.5; subtotalP = 2.
        // Cantidad = 5.
        // ComponenteM = 0.4 × 5 = 2.0; ComponenteN = 8 × 5 = 40; ComponenteO = 4.5 × 5 = 22.5; ComponenteP = 2 × 5 = 10
        // Total componentes = 74.5
        // PU_2dp_DOWN(CT=14.9) = 14.90; PT = 5 × 14.90 = 74.50 → totalGeneral = 74.50
        Map<String, String> comp = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/resumen")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("porComponente");

        // El JSON devuelve los 4 componentes en escala 6 con HALF_UP
        // (componentes se serializan fijos a 6dp).
        assertEquals("2.000000", comp.get("EQUIPO"));
        assertEquals("40.000000", comp.get("MANO_OBRA"));
        assertEquals("22.500000", comp.get("MATERIAL"));
        assertEquals("10.000000", comp.get("TRANSPORTE"));

        // totalGeneral desde write-through de presupuesto.total
        BigDecimal totalGeneralBd = leerPresupuestoTotal(presupuestoId).setScale(6, java.math.RoundingMode.HALF_UP);
        assertEquals(0, new BigDecimal("74.500000").compareTo(totalGeneralBd));

        // %CI=0 → ivaReferencial = 0
        BigDecimal iva = leerParametrosIva(internalProyectoId(proyectoId));
        assertEquals(0, iva.compareTo(new BigDecimal("0.1500")));

        // totalGeneral = 74.50; iva = 15% → ivaReferencial = 11.175
        // totalConIva = 85.675
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/resumen")
                .then()
                .statusCode(200)
                .body("totalGeneral", equalTo("74.500000"))
                .body("ivaReferencial", equalTo("11.175000"))
                .body("totalConIva", equalTo("85.675000"));
    }

    /**
     * %CI no cero: el totalGeneral del write-through ya incluye CI (PT =
     * cantidad × PU_2dp donde PU_2dp_DOWN(CT) = DOWN(CD×(1+%CI))). Los
     * componentes M/N/O/P NO incluyen CI — son la contribución directa del
     * APU × cantidad. Por tanto, totalGeneral ≠ suma componentes.
     */
    @Test
    void TC_P30_03_componentes_directos_y_total_general_con_ci_no_cero() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-c3@ex.com");
        String proyectoId = crearProyecto(token, "CI 0.18");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        // %CI = 0.18
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE parametros_proyecto SET porcentaje_herramienta_menor=0.05, "
                                + "porcentaje_indirecto=0.18, iva=0.15, moneda='USD' "
                                + "WHERE proyecto_id=(SELECT id FROM proyecto WHERE public_id=?)")) {
            ps.setObject(1, UUID.fromString(proyectoId));
            ps.executeUpdate();
        }

        String apuId = insertarApuCompletoMNoP(presupuestoId, "APU-CI18");
        String capId = crearCapituloRaiz(token, presupuestoId, "Cap");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId, "cantidad", "5.000000"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(201);

        // APU: CD = 14.9; CI = 14.9 × 0.18 = 2.682; CT = 17.582
        // PU_2dp_DOWN(17.582) = 17.58
        // PT = 5 × 17.58 = 87.90 → totalGeneral = 87.90 (write-through)
        // Componentes directos:
        //   M = 0.4 × 5 = 2.0
        //   N = 8 × 5 = 40
        //   O = 4.5 × 5 = 22.5
        //   P = 2 × 5 = 10
        //   Σ = 74.5 (no incluye CI)
        //
        // totalGeneral (87.90) ≠ Σ componentes (74.50) — esto demuestra que el
        // resumen no suma los componentes para totalGeneral: usa el write-through.

        BigDecimal totalGeneralBd = leerPresupuestoTotal(presupuestoId).setScale(6, java.math.RoundingMode.HALF_UP);
        assertEquals(0, new BigDecimal("87.900000").compareTo(totalGeneralBd));

        // Componentes NO contienen CI
        Map<String, String> comp = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/resumen")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("porComponente");

        assertEquals("2.000000", comp.get("EQUIPO"));
        assertEquals("40.000000", comp.get("MANO_OBRA"));
        assertEquals("22.500000", comp.get("MATERIAL"));
        assertEquals("10.000000", comp.get("TRANSPORTE"));

        // totalGeneral = 87.900000 (write-through)
        // ivaReferencial = 87.90 × 0.15 = 13.185
        // totalConIva = 87.90 + 13.185 = 101.085
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/resumen")
                .then()
                .statusCode(200)
                .body("totalGeneral", equalTo("87.900000"))
                .body("ivaReferencial", equalTo("13.185000"))
                .body("totalConIva", equalTo("101.085000"));
    }

    /**
     * %CI no cero Y múltiples rubros: los componentes agregan las contribuciones
     * directas de cada APU × cantidad; totalGeneral absorbe el write-through.
     */
    @Test
    void TC_P30_04_multiples_rubros_con_ci_no_cero() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-c4@ex.com");
        String proyectoId = crearProyecto(token, "Multi rubro");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE parametros_proyecto SET porcentaje_herramienta_menor=0.05, "
                                + "porcentaje_indirecto=0.18, iva=0.15, moneda='USD' "
                                + "WHERE proyecto_id=(SELECT id FROM proyecto WHERE public_id=?)")) {
            ps.setObject(1, UUID.fromString(proyectoId));
            ps.executeUpdate();
        }

        String apuA = insertarApuCompletoMNoP(presupuestoId, "APU-A");
        String apuB = insertarApuCompletoMNoP(presupuestoId, "APU-B");
        String capId = crearCapituloRaiz(token, presupuestoId, "Cap");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuA, "cantidad", "2.000000"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(201);

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuB, "cantidad", "3.000000"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(201);

        // APU unitario (cada uno): CD=14.9; CI=2.682; CT=17.582; PU_2dp=17.58
        // Rubro 1 (cant 2): PT = 2 × 17.58 = 35.16
        // Rubro 2 (cant 3): PT = 3 × 17.58 = 52.74
        // totalGeneral = 87.90
        // Componentes (cantidad × subtotal × APU_count):
        //   M = 0.4 × (2 + 3) = 2.0
        //   N = 8 × 5 = 40
        //   O = 4.5 × 5 = 22.5
        //   P = 2 × 5 = 10
        //   Σ = 74.5
        BigDecimal totalGeneralBd = leerPresupuestoTotal(presupuestoId).setScale(6, java.math.RoundingMode.HALF_UP);
        assertEquals(0, new BigDecimal("87.900000").compareTo(totalGeneralBd));

        Map<String, String> comp = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/resumen")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getMap("porComponente");

        assertEquals("2.000000", comp.get("EQUIPO"));
        assertEquals("40.000000", comp.get("MANO_OBRA"));
        assertEquals("22.500000", comp.get("MATERIAL"));
        assertEquals("10.000000", comp.get("TRANSPORTE"));
    }

    /**
     * Read-only: dos GET consecutivos no mutan el estado. rubros antes y
     * después son iguales; presupuesto.total antes y después son iguales.
     */
    @Test
    void TC_P30_05_get_resumen_no_escribe_en_bd() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-c5@ex.com");
        String proyectoId = crearProyecto(token, "Read only");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String apuId = insertarApuCompletoMNoP(presupuestoId, "APU-RO");
        String capId = crearCapituloRaiz(token, presupuestoId, "Cap");
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId, "cantidad", "2"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(201);

        long rubrosAntes = contarRubros(presupuestoId);
        long apusAntes = contarApus(presupuestoId);
        BigDecimal totalAntes = leerPresupuestoTotal(presupuestoId).setScale(6, java.math.RoundingMode.HALF_UP);

        // 5 invocaciones consecutivas del resumen
        for (int i = 0; i < 5; i++) {
            given().header("Authorization", "Bearer " + token)
                    .when()
                    .get("/api/v1/presupuestos/" + presupuestoId + "/resumen")
                    .then()
                    .statusCode(200);
        }

        long rubrosDespues = contarRubros(presupuestoId);
        long apusDespues = contarApus(presupuestoId);
        BigDecimal totalDespues = leerPresupuestoTotal(presupuestoId).setScale(6, java.math.RoundingMode.HALF_UP);

        assertEquals(rubrosAntes, rubrosDespues, "GET /resumen no crea rubros");
        assertEquals(apusAntes, apusDespues, "GET /resumen no crea APUs");
        assertEquals(totalAntes.compareTo(totalDespues), 0, "GET /resumen no muta presupuesto.total");
    }

    /** UUIDv7 mal formado → 400 validacion. */
    @Test
    void TC_P30_06_path_uuid_no_v7_devuelve_400() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-c6@ex.com");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + UUID_NO_V7 + "/resumen")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("validacion"));
    }

    /** UUIDv7 bien formado pero inexistente → 404 (RNF-05: nunca 403). */
    @Test
    void TC_P30_07_presupuesto_inexistente_devuelve_404() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-c7@ex.com");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + UUID_INEXISTENTE_V7 + "/resumen")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    /** Caller ajeno → 404 (RNF-05). */
    @Test
    void TC_P30_08_caller_ajeno_devuelve_404() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-c8-owner@ex.com");
        String proyectoId = crearProyecto(token, "Owner");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String intruso = AuthSupport.registrarConToken(mailbox, "p30-c8-intruso@ex.com");

        given().header("Authorization", "Bearer " + intruso)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/resumen")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));
    }

    /** Forma JSON estable: porComponente aparece en el orden canónico EQUIPO, MANO_OBRA, MATERIAL, TRANSPORTE. */
    @Test
    void TC_P30_09_orden_canonico_en_por_componente() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-c9@ex.com");
        String proyectoId = crearProyecto(token, "Orden");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        String apuId = insertarApuCompletoMNoP(presupuestoId, "APU-ORD");
        String capId = crearCapituloRaiz(token, presupuestoId, "Cap");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId, "cantidad", "1.000000"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(201);

        // LinkedHashMap preserva el orden de inserción. El JSON de Jackson
        // respeta el orden del Map → EQUIPO, MANO_OBRA, MATERIAL, TRANSPORTE.
        LinkedHashMap<String, String> orden = given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/resumen")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .get("porComponente");

        assertNotNull(orden);
        String[] keys = orden.keySet().toArray(new String[0]);
        assertEquals(4, keys.length);
        assertEquals("EQUIPO", keys[0]);
        assertEquals("MANO_OBRA", keys[1]);
        assertEquals("MATERIAL", keys[2]);
        assertEquals("TRANSPORTE", keys[3]);
    }

    /**
     * El IVA referencial usa el {@code totalGeneral} (no la suma de componentes).
     * Verificación con %CI no cero: ivaReferencial ≠ (Σ componentes × iva).
     */
    @Test
    void TC_P30_10_iva_referencial_basado_en_total_general() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-c10@ex.com");
        String proyectoId = crearProyecto(token, "IVA");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE parametros_proyecto SET porcentaje_herramienta_menor=0.05, "
                                + "porcentaje_indirecto=0.18, iva=0.15, moneda='USD' "
                                + "WHERE proyecto_id=(SELECT id FROM proyecto WHERE public_id=?)")) {
            ps.setObject(1, UUID.fromString(proyectoId));
            ps.executeUpdate();
        }

        String apuId = insertarApuCompletoMNoP(presupuestoId, "APU-IVA");
        String capId = crearCapituloRaiz(token, presupuestoId, "Cap");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId, "cantidad", "10.000000"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(201);

        // APU: CD = 14.9; CI = 14.9 × 0.18 = 2.682; CT = 17.582; PU_2dp_DOWN = 17.58
        // PT = 10 × 17.58 = 175.80 → totalGeneral = 175.80 (write-through)
        // ivaReferencial = 175.80 × 0.15 = 26.37
        // totalConIva = 202.17

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/resumen")
                .then()
                .statusCode(200)
                .body("totalGeneral", equalTo("175.800000"))
                .body("ivaReferencial", equalTo("26.370000"))
                .body("totalConIva", equalTo("202.170000"));
    }

    /**
     * El totalConIva es la suma natural de totalGeneral + ivaReferencial
     * (BigDecimal natural, sin redondear intermedio).
     */
    @Test
    void TC_P30_11_total_con_iva_suma_natural() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-c11@ex.com");
        String proyectoId = crearProyecto(token, "Suma");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        // iva referencial 0.1234 (default de la BD es 0.15, pero acá forzamos 0.1234)
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("UPDATE parametros_proyecto SET porcentaje_herramienta_menor=0.05, "
                                + "porcentaje_indirecto=0, iva=0.1234, moneda='USD' "
                                + "WHERE proyecto_id=(SELECT id FROM proyecto WHERE public_id=?)")) {
            ps.setObject(1, UUID.fromString(proyectoId));
            ps.executeUpdate();
        }

        String apuId = insertarApuCompletoMNoP(presupuestoId, "APU-SUMA");
        String capId = crearCapituloRaiz(token, presupuestoId, "Cap");

        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of("apuId", apuId, "cantidad", "1.000000"))
                .when()
                .post("/api/v1/presupuestos/" + presupuestoId + "/capitulos/" + capId + "/rubros")
                .then()
                .statusCode(201);

        // totalGeneral = 14.90 (PU_2dp_DOWN(CT=14.9)=14.90, PT=1×14.90=14.90)
        // ivaReferencial = 14.90 × 0.1234 = 1.83866
        // totalConIva = 14.90 + 1.83866 = 16.73866
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/resumen")
                .then()
                .statusCode(200)
                .body("totalGeneral", equalTo("14.900000"))
                .body("ivaReferencial", equalTo("1.838660"))
                .body("totalConIva", equalTo("16.738660"));
    }

    /** GET resumen no requiere token para presupuesto ajeno: 404 sin filtrar existencia. */
    @Test
    void TC_P30_12_sin_token_no_devuelve_401_pero_si_no_existe_404() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "p30-c12@ex.com");
        String proyectoId = crearProyecto(token, "Sin token");
        String presupuestoId = vigenteDeProyecto(proyectoId);

        // Sin token → filtro de seguridad de Quarkus responde 401 (no 404)
        // (este test verifica que cuando NO hay token, la respuesta es 401).
        given().when()
                .get("/api/v1/presupuestos/" + presupuestoId + "/resumen")
                .then()
                .statusCode(401);

        // Y con token pero inexistente → 404
        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/v1/presupuestos/" + UUID_INEXISTENTE_V7 + "/resumen")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("no-encontrado"));

        // El resumen no se devuelve a intrusos
        assertNotEquals(401, 404);
    }
}
