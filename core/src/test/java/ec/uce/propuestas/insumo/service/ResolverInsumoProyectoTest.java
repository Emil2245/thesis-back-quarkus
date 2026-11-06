package ec.uce.propuestas.insumo.service;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.entity.TipoBase;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests focalizados para {@link ResolverInsumoProyectoService} (N04 §A9 — copia al
 * usar). Se insertan las bases e insumos directamente vía SQL para forzar los
 * tres tipos (CENTRAL/PERSONAL/PROYECTO) sin depender del resto del API.
 */
@QuarkusTest
class ResolverInsumoProyectoTest {

    @Inject
    DataSource ds;

    @Inject
    EntityManager em;

    @Inject
    ResolverInsumoProyectoService resolver;

    @Inject
    InsumoRepository insumoRepository;

    @BeforeEach
    void reset() throws Exception {
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, "
                    + "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    // ------------------------------------------------------------------------
    // Helpers de sembrado
    // ------------------------------------------------------------------------

    private record Usuarios(long duenoA, long duenoB, long central) {}

    private Usuarios sembrarUsuarios() throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO usuario (nombre, email, password_hash, email_verificado, activo) "
                                + "VALUES (?, ?, ?, TRUE, TRUE) RETURNING id")) {
            ps.setString(1, "Dueño A");
            ps.setString(2, "a@ex.com");
            ps.setString(3, "x");
            long a = executeInsertReturning(ps);
            ps.setString(1, "Dueño B");
            ps.setString(2, "b@ex.com");
            ps.setString(3, "x");
            long b = executeInsertReturning(ps);
            ps.setString(1, "Central");
            ps.setString(2, "c@ex.com");
            ps.setString(3, "x");
            long c = executeInsertReturning(ps);
            return new Usuarios(a, b, c);
        }
    }

    private long executeInsertReturning(PreparedStatement ps) throws Exception {
        try (ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private long sembrarProyecto(long usuarioId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO proyecto (usuario_id, nombre_proyecto, anio, direccion_institucional, estado) "
                                + "VALUES (?, ?, 2026, 'GAD', 'BORRADOR') RETURNING id")) {
            ps.setLong(1, usuarioId);
            ps.setString(2, "Proyecto " + usuarioId);
            return executeInsertReturning(ps);
        }
    }

    private long sembrarBase(TipoBase tipo, Long usuarioId, Long proyectoId, String nombre) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO base_insumos (nombre, tipo, usuario_id, proyecto_id, archivada) "
                                + "VALUES (?, ?, ?, ?, FALSE) RETURNING id")) {
            ps.setString(1, nombre);
            ps.setString(2, tipo.name());
            if (usuarioId == null) {
                ps.setNull(3, java.sql.Types.BIGINT);
            } else {
                ps.setLong(3, usuarioId);
            }
            if (proyectoId == null) {
                ps.setNull(4, java.sql.Types.BIGINT);
            } else {
                ps.setLong(4, proyectoId);
            }
            return executeInsertReturning(ps);
        }
    }

    private long sembrarInsumo(long baseId, String codigo, TipoInsumo tipo, String unidad, BigDecimal precio)
            throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO insumo (base_id, codigo, tipo, descripcion, unidad, precio_unitario) "
                                + "VALUES (?, ?, ?, ?, ?, ?) RETURNING id")) {
            ps.setLong(1, baseId);
            ps.setString(2, codigo);
            ps.setString(3, tipo.name());
            ps.setString(4, "desc " + codigo);
            ps.setString(5, unidad);
            ps.setBigDecimal(6, precio);
            return executeInsertReturning(ps);
        }
    }

    private long contarInsumos(long baseId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT count(*) FROM insumo WHERE base_id = ?")) {
            ps.setLong(1, baseId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long baseIdDeProyecto(long proyectoId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM base_insumos WHERE proyecto_id = ?")) {
            ps.setLong(1, proyectoId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "El proyecto debe tener una base PROYECTO");
                return rs.getLong(1);
            }
        }
    }

    private long contarBasesDeProyecto(long proyectoId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps =
                        con.prepareStatement("SELECT count(*) FROM base_insumos WHERE proyecto_id = ?")) {
            ps.setLong(1, proyectoId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getLong(1);
            }
        }
    }

    // ------------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------------

    /** CENTRAL → copia a PROYECTO con `publicId` fresco distinto del origen. */
    @Test
    void TC_RES_01_central_se_copia_a_proyecto() throws Exception {
        Usuarios u = sembrarUsuarios();
        long baseCentral = sembrarBase(TipoBase.CENTRAL, null, null, "Central APU");
        long insumoCentral = sembrarInsumo(baseCentral, "MAT-1", TipoInsumo.MATERIAL, "kg", new BigDecimal("2.50"));
        long proyecto = sembrarProyecto(u.duenoA());

        Insumo result = resolver.materializarOReusar(insumoCentral, proyecto);

        assertNotEquals(insumoCentral, result.id, "ID interno debe ser nuevo (BIGINT)");
        assertNotNull(result.publicId, "publicId generado por BD");
        assertEquals("MAT-1", result.codigo);
        assertEquals(TipoInsumo.MATERIAL, result.tipo);
        assertEquals("kg", result.unidad);
        assertEquals(0, result.precioUnitario.compareTo(new BigDecimal("2.50")));
        assertEquals(baseIdDeProyecto(proyecto), result.baseId, "El destino es la base PROYECTO");

        Insumo origen = insumoRepository.findById(insumoCentral);
        assertNotEquals(origen.publicId, result.publicId, "publicId de la copia ≠ publicId del origen");
        assertEquals(1, contarInsumos(baseIdDeProyecto(proyecto)), "PROYECTO tiene 1 insumo");
        assertEquals(1, contarInsumos(baseCentral), "CENTRAL sigue intacta");
    }

    /** PERSONAL del dueño del proyecto → copia a PROYECTO. */
    @Test
    void TC_RES_02_personal_del_dueno_se_copia_a_proyecto() throws Exception {
        Usuarios u = sembrarUsuarios();
        long basePersonal = sembrarBase(TipoBase.PERSONAL, u.duenoA(), null, "Personal A");
        long insumoPersonal = sembrarInsumo(basePersonal, "EQ-1", TipoInsumo.EQUIPO, "h", new BigDecimal("5.00"));
        long proyecto = sembrarProyecto(u.duenoA());

        Insumo result = resolver.materializarOReusar(insumoPersonal, proyecto);

        assertNotEquals(insumoPersonal, result.id);
        assertEquals(baseIdDeProyecto(proyecto), result.baseId);
        assertEquals("EQ-1", result.codigo);
        assertEquals(TipoInsumo.EQUIPO, result.tipo);
        assertEquals("h", result.unidad);
    }

    /** PERSONAL de OTRO dueño → 404 no-encontrado. */
    @Test
    void TC_RES_03_personal_de_otro_dueno_devuelve_404() throws Exception {
        Usuarios u = sembrarUsuarios();
        long basePersonalAjena = sembrarBase(TipoBase.PERSONAL, u.duenoB(), null, "Personal B");
        long insumoAjeno = sembrarInsumo(basePersonalAjena, "MO-1", TipoInsumo.MANO_OBRA, "h", new BigDecimal("3.50"));
        long proyecto = sembrarProyecto(u.duenoA());

        ec.uce.propuestas.common.ProblemaException ex = assertThrows(
                ec.uce.propuestas.common.ProblemaException.class,
                () -> resolver.materializarOReusar(insumoAjeno, proyecto));
        assertEquals(404, ex.getResponse().getStatus());
        assertEquals("no-encontrado", errorCodigo(ex));

        assertEquals(0, contarBasesDeProyecto(proyecto), "El rechazo no debe crear una base PROYECTO");
        assertEquals(1, contarInsumos(basePersonalAjena), "PERSONAL ajeno intacto");
    }

    /** PROYECTO de OTRO proyecto → 404 no-encontrado. */
    @Test
    void TC_RES_04_proyecto_de_otro_proyecto_devuelve_404() throws Exception {
        Usuarios u = sembrarUsuarios();
        long proyectoOrigen = sembrarProyecto(u.duenoA());
        long baseOrigen = sembrarBase(TipoBase.PROYECTO, null, proyectoOrigen, "PROYECTO origen");
        long insumoOrigen = sembrarInsumo(baseOrigen, "MO-2", TipoInsumo.MANO_OBRA, "h", new BigDecimal("4.00"));
        long proyectoDestino = sembrarProyecto(u.duenoA());

        ec.uce.propuestas.common.ProblemaException ex = assertThrows(
                ec.uce.propuestas.common.ProblemaException.class,
                () -> resolver.materializarOReusar(insumoOrigen, proyectoDestino));
        assertEquals(404, ex.getResponse().getStatus());
        assertEquals("no-encontrado", errorCodigo(ex));

        assertEquals(0, contarBasesDeProyecto(proyectoDestino), "El rechazo no debe crear una base PROYECTO");
    }

    /** PROYECTO del mismo proyecto → reusa el mismo BIGINT, sin duplicar. */
    @Test
    void TC_RES_05_proyecto_del_mismo_proyecto_reusa_sin_duplicar() throws Exception {
        Usuarios u = sembrarUsuarios();
        long proyecto = sembrarProyecto(u.duenoA());
        long baseProyecto = sembrarBase(TipoBase.PROYECTO, null, proyecto, "PROYECTO propio");
        long insumo = sembrarInsumo(baseProyecto, "MAT-2", TipoInsumo.MATERIAL, "kg", new BigDecimal("1.20"));

        Insumo result = resolver.materializarOReusar(insumo, proyecto);

        assertEquals(insumo, result.id, "mismo BIGINT");
        assertEquals(1, contarInsumos(baseProyecto), "no se duplica");
    }

    /** CENTRAL → segunda llamada con mismo codigo reusa la copia. */
    @Test
    void TC_RES_06_central_repetido_reusa_por_codigo() throws Exception {
        Usuarios u = sembrarUsuarios();
        long baseCentral = sembrarBase(TipoBase.CENTRAL, null, null, "Central APU");
        long insumoCentral = sembrarInsumo(baseCentral, "MAT-3", TipoInsumo.MATERIAL, "kg", new BigDecimal("2.50"));
        long proyecto = sembrarProyecto(u.duenoA());

        Insumo primero = resolver.materializarOReusar(insumoCentral, proyecto);
        Insumo segundo = resolver.materializarOReusar(insumoCentral, proyecto);

        assertEquals(primero.id, segundo.id, "dedup por (base_id, codigo)");
        assertEquals(1, contarInsumos(baseIdDeProyecto(proyecto)));
    }

    /** PERSONAL → segunda llamada con mismo codigo reusa la copia. */
    @Test
    void TC_RES_07_personal_repetido_reusa_por_codigo() throws Exception {
        Usuarios u = sembrarUsuarios();
        long basePersonal = sembrarBase(TipoBase.PERSONAL, u.duenoA(), null, "Personal A");
        long insumoPersonal = sembrarInsumo(basePersonal, "EQ-3", TipoInsumo.EQUIPO, "h", new BigDecimal("5.00"));
        long proyecto = sembrarProyecto(u.duenoA());

        Insumo primero = resolver.materializarOReusar(insumoPersonal, proyecto);
        Insumo segundo = resolver.materializarOReusar(insumoPersonal, proyecto);

        assertEquals(primero.id, segundo.id);
        assertEquals(1, contarInsumos(baseIdDeProyecto(proyecto)));
    }

    /** Edición del insumo fuente (CENTRAL) NO se propaga al destino PROYECTO. */
    @Test
    void TC_RES_08_edicion_del_origen_no_se_propaga() throws Exception {
        Usuarios u = sembrarUsuarios();
        long baseCentral = sembrarBase(TipoBase.CENTRAL, null, null, "Central APU");
        long insumoCentral = sembrarInsumo(baseCentral, "MAT-4", TipoInsumo.MATERIAL, "kg", new BigDecimal("2.50"));
        long proyecto = sembrarProyecto(u.duenoA());

        Insumo copia = resolver.materializarOReusar(insumoCentral, proyecto);

        // edito el insumo CENTRAL directamente vía SQL
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE insumo SET precio_unitario = ? WHERE id = ?")) {
            ps.setBigDecimal(1, new BigDecimal("9.99"));
            ps.setLong(2, insumoCentral);
            ps.executeUpdate();
        }
        em.clear();

        Insumo copiaRecargada = insumoRepository.findById(copia.id);
        assertEquals(
                0,
                copiaRecargada.precioUnitario.compareTo(new BigDecimal("2.50")),
                "precio de la copia NO cambia con la edición del origen");
    }

    /** Copia CENTRAL con código que ya existe en destino (D-07) → destino gana. */
    @Test
    void TC_RES_09_d07_codigo_existente_en_destino_se_conserva() throws Exception {
        Usuarios u = sembrarUsuarios();
        long proyecto = sembrarProyecto(u.duenoA());
        long baseProyecto = sembrarBase(TipoBase.PROYECTO, null, proyecto, "PROYECTO propio");
        long insumoDestino = sembrarInsumo(baseProyecto, "MAT-5", TipoInsumo.MATERIAL, "kg", new BigDecimal("1.00"));

        long baseCentral = sembrarBase(TipoBase.CENTRAL, null, null, "Central APU");
        long insumoCentral = sembrarInsumo(baseCentral, "MAT-5", TipoInsumo.MATERIAL, "kg", new BigDecimal("9.99"));

        Insumo result = resolver.materializarOReusar(insumoCentral, proyecto);

        assertEquals(insumoDestino, result.id, "dedup devuelve la fila existente (D-07)");
        assertEquals(0, result.precioUnitario.compareTo(new BigDecimal("1.00")), "precio del destino intacto");

        assertEquals(1, contarInsumos(baseProyecto), "PROYECTO sigue con 1 fila");
        assertEquals(1, contarInsumos(baseCentral), "CENTRAL intacto (no se eliminó la fuente)");
    }

    private static String errorCodigo(ec.uce.propuestas.common.ProblemaException ex) {
        return ((ec.uce.propuestas.common.ErrorPayload) ex.getResponse().getEntity()).codigo();
    }
}
