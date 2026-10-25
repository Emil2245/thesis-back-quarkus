package ec.uce.propuestas.recalculo;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

@QuarkusTest
class RecalculoCodigoRepetidoIT {

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

    /**
     * Plan 033: dos rubros con el mismo {@code codigo} ligados a APU distintos
     * deben recibir cada uno el PU de su propio APU, y Sigma precio_total debe
     * igualar el total del presupuesto.
     */
    @Test
    void TC_P33_dos_rubros_con_el_mismo_codigo_reciben_cada_uno_el_precio_de_su_apu() throws Exception {
        long usuarioId = insertarUsuario();
        long proyectoId = insertarProyecto(usuarioId);
        long presupuestoId = insertarPresupuesto(proyectoId);
        insertarParametros(proyectoId);
        long baseId = insertarBaseProyecto(proyectoId);
        long insumoId = insertarInsumo(baseId, "MO-001", "MANO_OBRA", "h", new BigDecimal("4.00"));
        long apuA = insertarApu(presupuestoId, "APU-A");
        insertarEquipoConHm(apuA, 1);
        insertarDetalleMo(insertarSeccion(apuA, "MANO_OBRA", 2), insumoId, new BigDecimal("2"), BigDecimal.ONE, null);
        long apuB = insertarApu(presupuestoId, "APU-B");
        insertarEquipoConHm(apuB, 1);
        insertarDetalleMo(insertarSeccion(apuB, "MANO_OBRA", 2), insumoId, new BigDecimal("5"), BigDecimal.ONE, null);
        long capituloId = insertarCapitulo(presupuestoId, null, "1", "Capitulo 1", 1);
        long rubroA = insertarRubro(capituloId, apuA, "1.1", "DUP", "u", new BigDecimal("3"));
        long rubroB = insertarRubro(capituloId, apuB, "1.2", "DUP", "u", new BigDecimal("2"));

        recalculoService.recalcular(new Alcance.Version(presupuestoId));

        // APU-A: 2 x 4 = 8 -> CD 8.4 -> CT 9.912 -> PU 9.91; APU-B: 5 x 4 = 20 -> CD 21 -> CT 24.78 -> PU 24.78
        BigDecimal puA = new BigDecimal("9.91");
        BigDecimal puB = new BigDecimal("24.78");
        assertEquals(0, puA.compareTo(leerRubroPrecioUnitario(rubroA)), "PU rubro A");
        assertEquals(0, puB.compareTo(leerRubroPrecioUnitario(rubroB)), "PU rubro B");
        BigDecimal suma = leerRubroPrecioTotal(rubroA).add(leerRubroPrecioTotal(rubroB));
        assertEquals(0, new BigDecimal("79.29").compareTo(suma), "3 x 9.91 + 2 x 24.78");
        assertEquals(0, suma.compareTo(leerPresupuestoTotal(presupuestoId)), "sum PT == total presupuesto");
    }
}
