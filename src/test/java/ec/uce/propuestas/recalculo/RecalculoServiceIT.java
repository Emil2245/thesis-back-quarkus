package ec.uce.propuestas.recalculo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ec.uce.propuestas.common.ProblemaException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * RED — Plan 020 activation of the {@code recalculo} module (write-through).
 *
 * <p>Sealed {@link Alcance} with exactly {@link Alcance.Version},
 * {@link Alcance.Apu} and {@link Alcance.Insumo} (no {@code Capitulo} or
 * {@code Rubro} variants). Behaviour under each scope:
 * <ul>
 *   <li>{@link Alcance.Version}: persists {@code presupuesto.total},
 *       recursive {@code capitulo.total}, {@code rubro.precio_unitario} /
 *       {@code rubro.precio_total} (workbook-consistent frontier).</li>
 *   <li>{@link Alcance.Apu}: write-through APU; if linked, propagate to
 *       {@code rubro} and ascend to {@code capitulo}/{@code presupuesto}.</li>
 *   <li>{@link Alcance.Insumo}: re-evaluates APUs that inherit the insumo
 *       price (override NULL); APUs with explicit override are untouched.</li>
 * </ul>
 *
 * <p>Fixtures are persisted directly via SQL — no endpoint of Plan 021/022/
 * 023/024 is consumed. The seam under test is the
 * {@link RecalculoService#recalcular(Alcance)} entry point invoked from a
 * caller transactional context.
 *
 * <p>RED today: compile fails because
 * {@code ec.uce.propuestas.recalculo.RecalculoService} and
 * {@code ec.uce.propuestas.recalculo.Alcance} do not exist yet — the
 * module is BLOCKED until Plan 015 closes. GREEN once Plan 015 retires
 * the discount seam AND Plan 020 lands.
 */
@QuarkusTest
class RecalculoServiceIT {

    @Inject
    DataSource ds;

    @Inject
    RecalculoService recalculoService;

    @BeforeEach
    void reset() throws Exception {
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, plantilla_apu, "
                    + "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    // ─── Fixture helpers ───────────────────────────────────────────────────────

    private long insertarUsuario() throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO usuario (nombre, email, password_hash, email_verificado, activo) "
                                + "VALUES ('u','u@e','x',TRUE,TRUE) RETURNING id")) {
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long insertarProyecto(long usuarioId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO proyecto (usuario_id, nombre_proyecto, anio, plazo_ejecucion, plazo_unidad, estado, direccion_institucional) "
                                + "VALUES (?, 'P', 2026, 4, 'MES', 'BORRADOR', 'UCE') RETURNING id")) {
            ps.setLong(1, usuarioId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long insertarPresupuesto(long proyectoId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO presupuesto (proyecto_id, version, es_vigente) "
                                + "VALUES (?, 1, TRUE) RETURNING id")) {
            ps.setLong(1, proyectoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void insertarParametros(long proyectoId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO parametros_proyecto (proyecto_id, porcentaje_herramienta_menor, "
                                + "porcentaje_indirecto, iva, moneda) "
                                + "VALUES (?, 0.05, 0.18, 0.15, 'USD')")) {
            ps.setLong(1, proyectoId);
            ps.executeUpdate();
        }
    }

    private long insertarBaseProyecto(long proyectoId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO base_insumos (nombre, tipo, proyecto_id, archivada) "
                                + "VALUES ('Base', 'PROYECTO', ?, FALSE) RETURNING id")) {
            ps.setLong(1, proyectoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long insertarInsumo(long baseId, String codigo, String tipo, String unidad, BigDecimal precio)
            throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO insumo (base_id, codigo, tipo, descripcion, unidad, precio_unitario) "
                                + "VALUES (?, ?, ?, ?, ?, ?) RETURNING id")) {
            ps.setLong(1, baseId);
            ps.setString(2, codigo);
            ps.setString(3, tipo);
            ps.setString(4, codigo);
            ps.setString(5, unidad);
            ps.setBigDecimal(6, precio);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long insertarApu(long presupuestoId, String codigo) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO apu (presupuesto_id, codigo, descripcion, unidad) "
                                + "VALUES (?, ?, ?, 'u') RETURNING id")) {
            ps.setLong(1, presupuestoId);
            ps.setString(2, codigo);
            ps.setString(3, codigo);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long insertarSeccion(long apuId, String tipo, int orden) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("INSERT INTO apu_seccion (apu_id, tipo, subtotal, orden) "
                        + "VALUES (?, ?, 0, ?) RETURNING id")) {
            ps.setLong(1, apuId);
            ps.setString(2, tipo);
            ps.setInt(3, orden);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    /**
     * Inserta la sección EQUIPO con la fila HM canónica (la que
     * {@code ApuCrudService.crearSecciones} crea al materializar un APU
     * por el camino CRUD). Como el fixture de este test inserta secciones
     * por SQL directo, debe replicar ese detalle estructural para que
     * el motor calcule el HM como {@code %HM × subtotalN}.
     */
    private long insertarEquipoConHm(long apuId, int orden) throws Exception {
        long equipoSeccionId = insertarSeccion(apuId, "EQUIPO", orden);
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO apu_detalle (seccion_id, descripcion, orden, es_herramienta_menor, "
                                + "costo_hora, unidad, costo) "
                                + "VALUES (?, 'Herramienta Menor 5%MO', 1, TRUE, 0, '%', 0)")) {
            ps.setLong(1, equipoSeccionId);
            ps.executeUpdate();
        }
        return equipoSeccionId;
    }

    /**
     * Inserts a {@code apu_detalle} row. For {@code MANO_OBRA}/{@code EQUIPO}
     * the override lands in {@code tarifa_jornal}; for {@code MATERIAL}/
     * {@code TRANSPORTE} it lands in {@code precio_unitario_tarifa}. Pass
     * {@code override=null} for inheritance.
     */
    private void insertarDetalleMo(
            long seccionId, long insumoId, BigDecimal cantidadFila, BigDecimal rendimiento, BigDecimal tarifaOverride)
            throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO apu_detalle (seccion_id, insumo_id, descripcion, orden, cantidad, "
                                + "es_herramienta_menor, tarifa_jornal, costo_hora, rendimiento, unidad, costo) "
                                + "VALUES (?, ?, 'MO', 1, ?, FALSE, ?, 0, ?, 'h', 0)")) {
            ps.setLong(1, seccionId);
            if (insumoId == 0L) ps.setNull(2, java.sql.Types.BIGINT);
            else ps.setLong(2, insumoId);
            ps.setBigDecimal(3, cantidadFila);
            if (tarifaOverride == null) ps.setNull(4, java.sql.Types.NUMERIC);
            else ps.setBigDecimal(4, tarifaOverride);
            ps.setBigDecimal(5, rendimiento);
            ps.executeUpdate();
        }
    }

    private long insertarCapitulo(long presupuestoId, Long parentId, String item, String desc, int orden)
            throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO capitulo (presupuesto_id, parent_id, item, descripcion, orden, total) "
                                + "VALUES (?, ?, ?, ?, ?, 0) RETURNING id")) {
            ps.setLong(1, presupuestoId);
            if (parentId == null) ps.setNull(2, java.sql.Types.BIGINT);
            else ps.setLong(2, parentId);
            ps.setString(3, item);
            ps.setString(4, desc);
            ps.setInt(5, orden);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long insertarRubro(
            long capituloId, long apuId, String item, String codigo, String unidad, BigDecimal cantidad)
            throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO rubro (capitulo_id, apu_id, item, codigo, descripcion, unidad, cantidad, "
                                + "precio_unitario, precio_total) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0) RETURNING id")) {
            ps.setLong(1, capituloId);
            ps.setLong(2, apuId);
            ps.setString(3, item);
            ps.setString(4, codigo);
            ps.setString(5, codigo);
            ps.setString(6, unidad);
            ps.setBigDecimal(7, cantidad);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private BigDecimal leerApuCostoTotal(long apuId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT costo_total FROM apu WHERE id = ?")) {
            ps.setLong(1, apuId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private BigDecimal leerRubroPrecioUnitario(long rubroId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT precio_unitario FROM rubro WHERE id = ?")) {
            ps.setLong(1, rubroId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private BigDecimal leerRubroPrecioTotal(long rubroId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT precio_total FROM rubro WHERE id = ?")) {
            ps.setLong(1, rubroId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private BigDecimal leerCapituloTotal(long capituloId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT total FROM capitulo WHERE id = ?")) {
            ps.setLong(1, capituloId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private BigDecimal leerPresupuestoTotal(long presupuestoId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT total FROM presupuesto WHERE id = ?")) {
            ps.setLong(1, presupuestoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private void mutarCantidadRubro(long rubroId, BigDecimal nuevaCantidad) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE rubro SET cantidad = ? WHERE id = ?")) {
            ps.setBigDecimal(1, nuevaCantidad);
            ps.setLong(2, rubroId);
            ps.executeUpdate();
        }
    }

    private void mutarPrecioInsumo(long insumoId, BigDecimal nuevoPrecio) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE insumo SET precio_unitario = ? WHERE id = ?")) {
            ps.setBigDecimal(1, nuevoPrecio);
            ps.setLong(2, insumoId);
            ps.executeUpdate();
        }
    }

    private BigDecimal leerPrecioEfectivoDetalle(long seccionId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT tarifa_jornal FROM apu_detalle WHERE seccion_id = ?")) {
            ps.setLong(1, seccionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }

    private BigDecimal[] apuCostoTotal(long apuId, long insumoId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT costo_total FROM apu WHERE id = ?")) {
            ps.setLong(1, apuId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new BigDecimal[] {rs.getBigDecimal(1)};
            }
        }
    }

    // ─── Tests ───────────────────────────────────────────────────────────────

    /**
     * {@link Alcance.Apu} without a linked rubro must update only the APU's
     * own {@code costo_total}. The {@code rubro}/{@code capitulo}/{@code presupuesto}
     * tree is not touched (no rubro → no propagation target).
     */
    @Test
    void recalcular_apu_sin_rubro_vinculado_actualiza_solo_el_apu() throws Exception {
        long usuarioId = insertarUsuario();
        long proyectoId = insertarProyecto(usuarioId);
        long presupuestoId = insertarPresupuesto(proyectoId);
        insertarParametros(proyectoId);
        long baseId = insertarBaseProyecto(proyectoId);
        long insumoId = insertarInsumo(baseId, "MO-001", "MANO_OBRA", "h", new BigDecimal("4.00"));
        long apuId = insertarApu(presupuestoId, "APU-1");
        insertarEquipoConHm(apuId, 1);
        long moSeccionId = insertarSeccion(apuId, "MANO_OBRA", 2);
        insertarDetalleMo(moSeccionId, insumoId, new BigDecimal("2"), new BigDecimal("1"), null);

        // initial write-through: subtotalN = 2 × 4 × 1 = 8
        assertEquals(0, leerApuCostoTotal(apuId).compareTo(BigDecimal.ZERO));

        recalculoService.recalcular(new Alcance.Apu(apuId));

        // 1 MO row + HM 5% × 8 = 0.4 → CD = 8.4; %CI=18 → CI = 1.512; CT = 9.912
        BigDecimal expected =
                new BigDecimal("8.4").add(new BigDecimal("1.512")).setScale(6, java.math.RoundingMode.HALF_UP);
        assertEquals(
                0,
                expected.compareTo(leerApuCostoTotal(apuId).setScale(6, java.math.RoundingMode.HALF_UP)),
                "Alcance.Apu sin rubro: solo el APU se actualiza");
    }

    /**
     * {@link Alcance.Apu} WITH a linked rubro must propagate to the rubro
     * (workbook-consistent frontera), the chapter and the presupuesto.
     */
    @Test
    void recalcular_apu_con_rubro_propagacion_esperada() throws Exception {
        long usuarioId = insertarUsuario();
        long proyectoId = insertarProyecto(usuarioId);
        long presupuestoId = insertarPresupuesto(proyectoId);
        insertarParametros(proyectoId);
        long baseId = insertarBaseProyecto(proyectoId);
        long insumoId = insertarInsumo(baseId, "MO-001", "MANO_OBRA", "h", new BigDecimal("4.00"));
        long apuId = insertarApu(presupuestoId, "APU-1");
        insertarEquipoConHm(apuId, 1);
        long moSeccionId = insertarSeccion(apuId, "MANO_OBRA", 2);
        insertarDetalleMo(moSeccionId, insumoId, new BigDecimal("2"), new BigDecimal("1"), null);
        long capituloId = insertarCapitulo(presupuestoId, null, "1", "Capitulo 1", 1);
        long rubroId = insertarRubro(capituloId, apuId, "1", "APU-1", "u", new BigDecimal("3"));

        recalculoService.recalcular(new Alcance.Apu(apuId));

        // CT APU = 9.912; PU_2dp DOWN(9.912) = 9.91; PT = 3 × 9.91 = 29.73
        assertEquals(
                0,
                new BigDecimal("9.91")
                        .compareTo(leerRubroPrecioUnitario(rubroId).setScale(2, java.math.RoundingMode.DOWN)));
        assertEquals(
                0,
                new BigDecimal("29.730000")
                        .compareTo(leerRubroPrecioTotal(rubroId).setScale(6, java.math.RoundingMode.HALF_UP)));
        // capitulo.total = 29.730000; presupuesto.total = 29.730000
        assertEquals(
                0,
                new BigDecimal("29.730000")
                        .compareTo(leerCapituloTotal(capituloId).setScale(6, java.math.RoundingMode.HALF_UP)));
        assertEquals(
                0,
                new BigDecimal("29.730000")
                        .compareTo(leerPresupuestoTotal(presupuestoId).setScale(6, java.math.RoundingMode.HALF_UP)));
    }

    /**
     * {@link Alcance.Version} covers the structural mutations: mutar the
     * cantidad del rubro and verify that one recursive traversal updates
     * every chapter total and the presupuesto total. Idempotence: a second
     * call produces the exact same persisted state at scale 6.
     */
    @Test
    void recalcular_version_totaliza_capitulos_recursivos_y_es_idempotente() throws Exception {
        long usuarioId = insertarUsuario();
        long proyectoId = insertarProyecto(usuarioId);
        long presupuestoId = insertarPresupuesto(proyectoId);
        insertarParametros(proyectoId);
        long baseId = insertarBaseProyecto(proyectoId);
        long insumoId = insertarInsumo(baseId, "MO-001", "MANO_OBRA", "h", new BigDecimal("4.00"));
        long apuId = insertarApu(presupuestoId, "APU-1");
        insertarEquipoConHm(apuId, 1);
        long moSeccionId = insertarSeccion(apuId, "MANO_OBRA", 2);
        insertarDetalleMo(moSeccionId, insumoId, new BigDecimal("2"), new BigDecimal("1"), null);
        long capRoot = insertarCapitulo(presupuestoId, null, "1", "Root", 1);
        long capSub = insertarCapitulo(presupuestoId, capRoot, "1.1", "Sub", 1);
        long rubroId = insertarRubro(capSub, apuId, "1.1.1", "APU-1", "u", new BigDecimal("5"));

        // initial write-through APU
        recalculoService.recalcular(new Alcance.Apu(apuId));
        // first version-wide consolidation at cantidad=5: PT = 5 × 9.91 = 49.55
        recalculoService.recalcular(new Alcance.Version(presupuestoId));
        assertEquals(
                0,
                new BigDecimal("49.550000")
                        .compareTo(leerCapituloTotal(capRoot).setScale(6, java.math.RoundingMode.HALF_UP)));
        assertEquals(
                0,
                new BigDecimal("49.550000")
                        .compareTo(leerCapituloTotal(capSub).setScale(6, java.math.RoundingMode.HALF_UP)));
        assertEquals(
                0,
                new BigDecimal("49.550000")
                        .compareTo(leerPresupuestoTotal(presupuestoId).setScale(6, java.math.RoundingMode.HALF_UP)));

        // mutate cantidad then re-run version consolidation: PT = 8 × 9.91 = 79.28
        mutarCantidadRubro(rubroId, new BigDecimal("8"));
        recalculoService.recalcular(new Alcance.Version(presupuestoId));
        assertEquals(
                0,
                new BigDecimal("79.280000")
                        .compareTo(leerCapituloTotal(capRoot).setScale(6, java.math.RoundingMode.HALF_UP)));
        assertEquals(
                0,
                new BigDecimal("79.280000")
                        .compareTo(leerCapituloTotal(capSub).setScale(6, java.math.RoundingMode.HALF_UP)));
        assertEquals(
                0,
                new BigDecimal("79.280000")
                        .compareTo(leerPresupuestoTotal(presupuestoId).setScale(6, java.math.RoundingMode.HALF_UP)));

        // idempotence: capture totals after the mutation+version, then a second
        // version call must produce the exact same persisted state.
        BigDecimal primerCapRoot = leerCapituloTotal(capRoot).setScale(6, java.math.RoundingMode.HALF_UP);
        BigDecimal primerCapSub = leerCapituloTotal(capSub).setScale(6, java.math.RoundingMode.HALF_UP);
        BigDecimal primerPres = leerPresupuestoTotal(presupuestoId).setScale(6, java.math.RoundingMode.HALF_UP);
        recalculoService.recalcular(new Alcance.Version(presupuestoId));
        assertEquals(
                0,
                primerCapRoot.compareTo(leerCapituloTotal(capRoot).setScale(6, java.math.RoundingMode.HALF_UP))
                        + primerCapSub.compareTo(leerCapituloTotal(capSub).setScale(6, java.math.RoundingMode.HALF_UP))
                        + primerPres.compareTo(
                                leerPresupuestoTotal(presupuestoId).setScale(6, java.math.RoundingMode.HALF_UP)),
                "second call must produce the same totals at scale 6");
    }

    /**
     * {@link Alcance.Insumo} propagates the new precioUnitario to APUs that
     * inherit (override NULL) and skips APUs with explicit override.
     */
    @Test
    void recalcular_insumo_propaga_a_herederos_excluye_overrides_explicitos() throws Exception {
        long usuarioId = insertarUsuario();
        long proyectoId = insertarProyecto(usuarioId);
        long presupuestoId = insertarPresupuesto(proyectoId);
        insertarParametros(proyectoId);
        long baseId = insertarBaseProyecto(proyectoId);
        long insumoId = insertarInsumo(baseId, "MO-001", "MANO_OBRA", "h", new BigDecimal("5.00"));

        long apuHeredero = insertarApu(presupuestoId, "APU-H");
        insertarEquipoConHm(apuHeredero, 1);
        long seccionHeredero = insertarSeccion(apuHeredero, "MANO_OBRA", 2);
        insertarDetalleMo(seccionHeredero, insumoId, new BigDecimal("1"), new BigDecimal("1"), null);

        long apuOverride = insertarApu(presupuestoId, "APU-O");
        insertarEquipoConHm(apuOverride, 1);
        long seccionOverride = insertarSeccion(apuOverride, "MANO_OBRA", 2);
        insertarDetalleMo(seccionOverride, insumoId, new BigDecimal("1"), new BigDecimal("1"), new BigDecimal("99.00"));

        // initial write-through at insumo=5.00 / override=99.00
        recalculoService.recalcular(new Alcance.Apu(apuHeredero));
        recalculoService.recalcular(new Alcance.Apu(apuOverride));
        BigDecimal ctHerederoInicial = leerApuCostoTotal(apuHeredero).setScale(6, java.math.RoundingMode.HALF_UP);
        BigDecimal ctOverrideInicial = leerApuCostoTotal(apuOverride).setScale(6, java.math.RoundingMode.HALF_UP);

        // mutate insumo.precioUnitario 5.00 → 7.50
        mutarPrecioInsumo(insumoId, new BigDecimal("7.50"));
        recalculoService.recalcular(new Alcance.Insumo(insumoId));

        // heredero: absorbs the new insumo price → CT must change
        assertNotEquals(
                0,
                ctHerederoInicial.compareTo(leerApuCostoTotal(apuHeredero).setScale(6, java.math.RoundingMode.HALF_UP)),
                "APU heredero (sin override) absorbe la mutación del insumo");

        // override: tarifa_jornal explicit stays at 99 → CT must NOT change
        assertEquals(
                0,
                ctOverrideInicial.compareTo(leerApuCostoTotal(apuOverride).setScale(6, java.math.RoundingMode.HALF_UP)),
                "APU con override explícito no absorbe la mutación del insumo");
    }

    // ──────────────────────────────────────────────────────────────────────
    // Plan 029 — sincronización 1:1 y cronograma snapshot persistente
    // ──────────────────────────────────────────────────────────────────────

    private long insertarRubroConCodigo(
            long capituloId,
            long apuId,
            String item,
            String codigo,
            String unidad,
            BigDecimal cantidad,
            BigDecimal precio)
            throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO rubro (capitulo_id, apu_id, item, codigo, descripcion, unidad, cantidad, "
                                + "precio_unitario, precio_total) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id")) {
            ps.setLong(1, capituloId);
            ps.setLong(2, apuId);
            ps.setString(3, item);
            ps.setString(4, codigo);
            ps.setString(5, codigo);
            ps.setString(6, unidad);
            ps.setBigDecimal(7, cantidad);
            ps.setBigDecimal(8, precio);
            ps.setBigDecimal(9, precio);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long insertarCronograma(long presupuestoId, short numeroPeriodos) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("INSERT INTO cronograma (presupuesto_id, unidad_tiempo, numero_periodos) "
                                + "VALUES (?, 'SEMANA', ?) RETURNING id")) {
            ps.setLong(1, presupuestoId);
            ps.setShort(2, numeroPeriodos);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void insertarActividad(long cronogramaId, long rubroId, BigDecimal peso, String jsonbAvance)
            throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO actividad (cronograma_id, rubro_id, peso_ponderado, avance_por_periodo) "
                                + "VALUES (?, ?, ?, ?::jsonb)")) {
            ps.setLong(1, cronogramaId);
            ps.setLong(2, rubroId);
            ps.setBigDecimal(3, peso);
            ps.setString(4, jsonbAvance);
            ps.executeUpdate();
        }
    }

    private long contarActividades(long presupuestoId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT COUNT(*) FROM actividad a JOIN cronograma c ON c.id = a.cronograma_id "
                                + "WHERE c.presupuesto_id = ?")) {
            ps.setLong(1, presupuestoId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private void actualizarAvance(long cronogramaId, String jsonb) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE actividad SET avance_por_periodo = ?::jsonb WHERE cronograma_id = ?")) {
            ps.setString(1, jsonb);
            ps.setLong(2, cronogramaId);
            ps.executeUpdate();
        }
    }

    private String leerAvance(long cronogramaId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT avance_por_periodo::text FROM actividad WHERE cronograma_id = ?")) {
            ps.setLong(1, cronogramaId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1).replace(" ", "");
            }
        }
    }

    /**
     * Plan 029: tras un {@link Alcance.Version}, la sincronización central
     * mantiene 1 actividad por rubro, con {@code avance_por_periodo} y
     * {@code peso_ponderado} consistentes con el totalGeneral y los rubros
     * vigentes. La FK CASCADE ya borró las huérfanas al eliminar el rubro;
     * un nuevo rubro sembrado por SQL recibe actividad con mapa "{}" y el
     * peso se determina por {@code PrecioPonderadoCalculador}.
     */
    @Test
    void consolidar_version_crea_actividad_para_rubro_nuevo_si_hay_cronograma() throws Exception {
        long usuarioId = insertarUsuario();
        long proyectoId = insertarProyecto(usuarioId);
        long presupuestoId = insertarPresupuesto(proyectoId);
        insertarParametros(proyectoId);
        long baseId = insertarBaseProyecto(proyectoId);
        long insumoId = insertarInsumo(baseId, "MO-001", "MANO_OBRA", "h", new BigDecimal("4.00"));
        long apuId = insertarApu(presupuestoId, "APU-1");
        insertarEquipoConHm(apuId, 1);
        long moSeccionId = insertarSeccion(apuId, "MANO_OBRA", 2);
        insertarDetalleMo(moSeccionId, insumoId, new BigDecimal("2"), new BigDecimal("1"), null);
        long capituloId = insertarCapitulo(presupuestoId, null, "1", "Root", 1);
        long cronogramaId = insertarCronograma(presupuestoId, (short) 4);

        // Antes del recálculo no hay actividad porque no había rubro.
        assertEquals(0L, contarActividades(presupuestoId));

        // Insertar el rubro directo (sin pasar por POST).
        long rubroId = insertarRubroConCodigo(
                capituloId, apuId, "1.1", "APU-1", "u", new BigDecimal("3"), new BigDecimal("9.912"));
        // Activar la sincronización vía recálculo del alcance.Version.
        recalculoService.recalcular(new Alcance.Version(presupuestoId));

        // Actividad creada con mapa {} y un peso coherente con el total.
        assertEquals(1L, contarActividades(presupuestoId), "1 actividad para 1 rubro (regla de cobertura 1:1)");
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "SELECT peso_ponderado::text, avance_por_periodo::text FROM actividad WHERE cronograma_id = ?")) {
            ps.setLong(1, cronogramaId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                // peso 100.0000 (único rubro).
                assertEquals(
                        new BigDecimal("100.0000"), rs.getBigDecimal(1).setScale(4, java.math.RoundingMode.HALF_UP));
                // mapa {}
                assertEquals("{}", rs.getString(2));
            }
        }
        // Mutar el rubro y volver a calcular; el peso se conserva con el
        // nuevo totalGeneral (siempre 100.0000% por haber un solo rubro).
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE rubro SET precio_total = ? WHERE id = ?")) {
            ps.setBigDecimal(1, new BigDecimal("5.5"));
            ps.setLong(2, rubroId);
            ps.executeUpdate();
        }
        recalculoService.recalcular(new Alcance.Version(presupuestoId));
        assertEquals(1L, contarActividades(presupuestoId), "sigue habiendo 1 sola actividad tras la mutación");

        actualizarAvance(cronogramaId, "{\"1\":\"40.0000\",\"3\":\"60.0000\"}");
        recalculoService.recalcular(new Alcance.Version(presupuestoId));
        assertEquals("{\"1\":\"40.0000\",\"3\":\"60.0000\"}", leerAvance(cronogramaId));
    }

    @Test
    void consolidar_version_rechaza_mapa_persistido_malformado_y_no_escribe_totales() throws Exception {
        long usuarioId = insertarUsuario();
        long proyectoId = insertarProyecto(usuarioId);
        long presupuestoId = insertarPresupuesto(proyectoId);
        insertarParametros(proyectoId);
        long apuId = insertarApu(presupuestoId, "APU-MAL");
        insertarEquipoConHm(apuId, 1);
        long capituloId = insertarCapitulo(presupuestoId, null, "1", "Root", 1);
        long rubroId = insertarRubroConCodigo(capituloId, apuId, "1.1", "APU-MAL", "u", BigDecimal.ONE, BigDecimal.ONE);
        long cronogramaId = insertarCronograma(presupuestoId, (short) 4);
        insertarActividad(cronogramaId, rubroId, new BigDecimal("100.0000"), "{\"x\":\"1.0000\"}");
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE presupuesto SET total = 77 WHERE id = ?")) {
            ps.setLong(1, presupuestoId);
            ps.executeUpdate();
        }

        assertThrows(ProblemaException.class, () -> recalculoService.recalcular(new Alcance.Version(presupuestoId)));
        assertEquals(
                0,
                new BigDecimal("77.000000")
                        .compareTo(leerPresupuestoTotal(presupuestoId).setScale(6, java.math.RoundingMode.HALF_UP)));
        assertEquals("{\"x\":\"1.0000\"}", leerAvance(cronogramaId));
    }

    /**
     * Plan 029 — el alcance.Version sin cronograma es no-op para la
     * sincronización (no crea actividades espurias).
     */
    @Test
    void consolidar_version_sin_cronograma_no_crea_actividades() throws Exception {
        long usuarioId = insertarUsuario();
        long proyectoId = insertarProyecto(usuarioId);
        long presupuestoId = insertarPresupuesto(proyectoId);
        insertarParametros(proyectoId);
        long baseId = insertarBaseProyecto(proyectoId);
        long insumoId = insertarInsumo(baseId, "MO-001", "MANO_OBRA", "h", new BigDecimal("4.00"));
        long apuId = insertarApu(presupuestoId, "APU-1");
        insertarEquipoConHm(apuId, 1);
        long moSeccionId = insertarSeccion(apuId, "MANO_OBRA", 2);
        insertarDetalleMo(moSeccionId, insumoId, new BigDecimal("2"), new BigDecimal("1"), null);
        long capituloId = insertarCapitulo(presupuestoId, null, "1", "Root", 1);
        insertarRubroConCodigo(capituloId, apuId, "1.1", "APU-1", "u", new BigDecimal("3"), new BigDecimal("9.912"));

        recalculoService.recalcular(new Alcance.Version(presupuestoId));

        // Sin cronograma, ninguna actividad. El contrato es no-op aquí.
        assertEquals(0L, contarActividades(presupuestoId));
    }
}
