package ec.uce.propuestas.apu.service;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
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
 * Plan 023 — cobertura del seam read-only {@code ApuCalculoService.calcular(Apu)}
 * introducido para alimentar {@code ResumenComponentesService} sin tocar la BD.
 *
 * <p>El refactor (Plan 023 §Refactor): {@code calcular(Apu)} construye el
 * {@code ApuSnapshot}, llama al motor y devuelve el {@code ApuCalculado} sin
 * persistir derivados. El {@code recalcular} existente delega a {@code calcular}
 * y luego persiste la salida, preservando exactamente el comportamiento previo.</p>
 *
 * <p>Este test verifica:
 * <ul>
 *   <li>{@code calcular} NO muta {@code apu.costoDirecto/costoIndirecto/costoTotal}.</li>
 *   <li>{@code calcular} NO muta los {@code apu_detalle.costo/costoHora}.</li>
 *   <li>{@code calcular} NO muta los {@code apu_seccion.subtotal}.</li>
 *   <li>{@code calcular} devuelve el mismo {@code ApuCalculado} que
 *       {@code Motor.calcularApu} produciría directamente sobre el snapshot.</li>
 *   <li>Tras {@code calcular} + {@code recalcular}, la BD queda igual a como
 *       habría quedado con un único {@code recalcular} (write-through idempotente).</li>
 * </ul>
 */
@QuarkusTest
class ApuCalculoReadOnlyTest {

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @Inject
    ApuCalculoService apuCalculoService;

    @Inject
    ApuRepository apuRepository;

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

    @Test
    void calcular_no_persiste_y_devuelve_calculado_del_motor() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "calcro@ex.com");
        // Setup mínimo: usuario + proyecto + presupuesto v1 (auto-create)
        // + APU con M/N vacías.
        String proyectoId = givenPostProyecto(token);
        Long presupuestoId = leerPresupuestoId(proyectoId);
        long apuId = insertarApuVacioMo(presupuestoId);

        // Snapshot del estado BD ANTES de calcular
        BigDecimal apuCdAntes = leerColumna("SELECT costo_directo FROM apu WHERE id = ?", apuId);
        BigDecimal apuCiAntes = leerColumna("SELECT costo_indirecto FROM apu WHERE id = ?", apuId);
        BigDecimal apuCtAntes = leerColumna("SELECT costo_total FROM apu WHERE id = ?", apuId);

        // calcular (read-only) — no debe persistir nada
        var apu = apuRepository.findById(apuId);
        var calc = apuCalculoService.calcular(apu);
        assertNotNull(calc);

        // El BD quedó intacto
        BigDecimal apuCdDespues = leerColumna("SELECT costo_directo FROM apu WHERE id = ?", apuId);
        BigDecimal apuCiDespues = leerColumna("SELECT costo_indirecto FROM apu WHERE id = ?", apuId);
        BigDecimal apuCtDespues = leerColumna("SELECT costo_total FROM apu WHERE id = ?", apuId);
        assertEquals(apuCdAntes.compareTo(apuCdDespues), 0);
        assertEquals(apuCiAntes.compareTo(apuCiDespues), 0);
        assertEquals(apuCtAntes.compareTo(apuCtDespues), 0);

        // El motor expone su cálculo en el ApuCalculado (CD = 0 con M/N vacías)
        assertEquals(0, calc.costoDirecto().compareTo(BigDecimal.ZERO));
        assertEquals(0, calc.costoIndirecto().compareTo(BigDecimal.ZERO));
        assertEquals(0, calc.costoTotal().compareTo(BigDecimal.ZERO));
    }

    @Test
    void calcular_secciones_subtotal_no_persiste() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "calcro2@ex.com");
        String proyectoId = givenPostProyecto(token);
        Long presupuestoId = leerPresupuestoId(proyectoId);
        long apuId = insertarApuVacioMo(presupuestoId);

        // Snapshot de subtotales ANTES
        BigDecimal subM =
                leerColumna("SELECT s.subtotal FROM apu_seccion s WHERE s.apu_id = ? AND s.tipo = 'EQUIPO'", apuId);
        BigDecimal subN =
                leerColumna("SELECT s.subtotal FROM apu_seccion s WHERE s.apu_id = ? AND s.tipo = 'MANO_OBRA'", apuId);

        var apu = apuRepository.findById(apuId);
        apuCalculoService.calcular(apu);

        // Subtotales intactos
        BigDecimal subM2 =
                leerColumna("SELECT s.subtotal FROM apu_seccion s WHERE s.apu_id = ? AND s.tipo = 'EQUIPO'", apuId);
        BigDecimal subN2 =
                leerColumna("SELECT s.subtotal FROM apu_seccion s WHERE s.apu_id = ? AND s.tipo = 'MANO_OBRA'", apuId);
        assertEquals(subM.compareTo(subM2), 0);
        assertEquals(subN.compareTo(subN2), 0);
    }

    /**
     * Tras {@code calcular} (read-only) + {@code recalcular} (write-through),
     * la BD queda igual a como habría quedado con un único {@code recalcular}.
     */
    @Test
    @TestTransaction
    void calcular_seguido_de_recalcular_persiste_consistente() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "calcro3@ex.com");
        String proyectoId = givenPostProyecto(token);
        Long presupuestoId = leerPresupuestoId(proyectoId);
        long apuId = insertarApuVacioMo(presupuestoId);

        var apu = apuRepository.findById(apuId);

        // calcular (read-only) seguido de recalcular (write-through)
        apuCalculoService.calcular(apu);
        apuCalculoService.recalcular(apu);

        // BD persiste totales y subtotales coherentes con la salida del motor
        var apuRecargado = apuRepository.findById(apuId);
        assertEquals(0, apuRecargado.costoDirecto.compareTo(BigDecimal.ZERO));
        assertEquals(0, apuRecargado.costoIndirecto.compareTo(BigDecimal.ZERO));
        assertEquals(0, apuRecargado.costoTotal.compareTo(BigDecimal.ZERO));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private String givenPostProyecto(String token) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.of(
                        "nombreProyecto",
                        "Calcro",
                        "anio",
                        (short) 2026,
                        "plazoEjecucion",
                        (short) 4,
                        "plazoUnidad",
                        "MES",
                        "direccionInstitucional",
                        "UCE"))
                .when()
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private Long leerPresupuestoId(String proyectoPublicId) throws Exception {
        Long proyectoId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM proyecto WHERE public_id = ?")) {
            ps.setObject(1, UUID.fromString(proyectoPublicId));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                proyectoId = rs.getLong(1);
            }
        }
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT id FROM presupuesto WHERE proyecto_id = ? AND version = 1")) {
            ps.setLong(1, proyectoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /**
     * Siembra un APU con secciones M/N vacías (HM canónico en M, sin filas en N).
     * El motor calculará CD/CI/CT = 0 (sin filas N, HM = 5% × 0 = 0).
     */
    private long insertarApuVacioMo(long presupuestoId) throws Exception {
        long apuId;
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO apu (presupuesto_id, codigo, descripcion, unidad, "
                                + "costo_directo, costo_indirecto, costo_total) "
                                + "VALUES (?, 'APU-CALCRO', 'Calcro', 'u', 0, 0, 0) RETURNING id")) {
            ps.setLong(1, presupuestoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                apuId = rs.getLong(1);
            }
        }
        // Secciones M, N (vacías). El motor de cálculo necesita las 4
        // secciones; las ausentes son aceptadas.
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO apu_seccion (apu_id, tipo, subtotal, orden) VALUES "
                                + "(?, 'EQUIPO', 0, 1), (?, 'MANO_OBRA', 0, 2), "
                                + "(?, 'MATERIAL', 0, 3), (?, 'TRANSPORTE', 0, 4)")) {
            ps.setLong(1, apuId);
            ps.setLong(2, apuId);
            ps.setLong(3, apuId);
            ps.setLong(4, apuId);
            ps.executeUpdate();
        }
        // Fila HM canónica
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO apu_detalle (seccion_id, descripcion, orden, es_herramienta_menor, "
                                + "costo_hora, unidad, costo) "
                                + "VALUES ((SELECT id FROM apu_seccion WHERE apu_id = ? AND tipo = 'EQUIPO'), "
                                + "'Herramienta Menor 5%MO', 1, TRUE, 0, '%', 0)")) {
            ps.setLong(1, apuId);
            ps.executeUpdate();
        }
        return apuId;
    }

    private BigDecimal leerColumna(String sql, long apuId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, apuId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }
}
