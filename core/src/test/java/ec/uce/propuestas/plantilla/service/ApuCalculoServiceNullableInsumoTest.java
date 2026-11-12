package ec.uce.propuestas.plantilla.service;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.apu.dto.ApuCrearRequest;
import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.entity.ApuDetalle;
import ec.uce.propuestas.apu.entity.ApuSeccion;
import ec.uce.propuestas.apu.repository.ApuDetalleRepository;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.repository.ApuSeccionRepository;
import ec.uce.propuestas.apu.service.ApuCalculoService;
import ec.uce.propuestas.apu.service.ApuCrudService;
import ec.uce.propuestas.motor.ApuCalculado;
import ec.uce.propuestas.motor.SeccionTipo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Plan 04 (P-26) — El motor debe poder calcular filas con {@code insumoId=null}
 * (Plan 04 §D — "pendiente" de resolución). Esto valida la adaptación del
 * snapshot en {@link ApuCalculoService} sin tocar las fórmulas del motor.
 */
@QuarkusTest
class ApuCalculoServiceNullableInsumoTest {

    @Inject
    ApuCalculoService apuCalculoService;

    @Inject
    ApuCrudService apuCrudService;

    @Inject
    ApuRepository apuRepository;

    @Inject
    ApuSeccionRepository seccionRepository;

    @Inject
    ApuDetalleRepository detalleRepository;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, plantilla_apu, "
                    + "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    /**
     * Creamos una fila directamente con {@code insumoId=null} y
     * {@code tarifaJornal=0} (estilo "pendiente" del snapshot). El motor
     * debe calcular costo 0 sin lanzar NPE.
     */
    @Test
    @Transactional
    void recalcular_con_fila_pendiente_no_npea_y_costo_es_cero() throws Exception {
        // Sembrado mínimo: usuario, proyecto, presupuesto, base, parámetros
        long usuarioId;
        long proyectoId;
        long presupuestoId;
        try (Connection con = ds.getConnection()) {
            try (PreparedStatement ps =
                    con.prepareStatement("INSERT INTO usuario (nombre, email, password_hash, email_verificado, activo) "
                            + "VALUES ('u','u@e','x',TRUE,TRUE) RETURNING id")) {
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    usuarioId = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO proyecto (usuario_id, nombre_proyecto, anio, plazo_ejecucion, plazo_unidad, estado, direccion_institucional) "
                            + "VALUES (?, 'X', 2026, 4, 'MES', 'BORRADOR', 'UCE') RETURNING id")) {
                ps.setLong(1, usuarioId);
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    proyectoId = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO presupuesto (proyecto_id, version, es_vigente) VALUES (?, 1, TRUE) RETURNING id")) {
                ps.setLong(1, proyectoId);
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    presupuestoId = rs.getLong(1);
                }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO parametros_proyecto (proyecto_id, porcentaje_herramienta_menor, iva, moneda) "
                            + "VALUES (?, 0.05, 0.15, 'USD')")) {
                ps.setLong(1, proyectoId);
                ps.executeUpdate();
            }
        }

        // APU con secciones y HM auto-creado
        var apuResp =
                apuCrudService.crearComoRespuesta(presupuestoId, new ApuCrearRequest("APU-N", "Null insumo", "u"));
        Apu apu = apuRepository.findById(apuInternalId(apuResp.id(), ds));

        // Inyectamos una fila pendiente en MO: insumoId=null, tarifaJornal=0, cantidad=1, rendimiento=0.5
        ApuSeccion mo =
                seccionRepository.findByApuYTipo(apu.id, SeccionTipo.MANO_OBRA).orElseThrow();
        ApuDetalle d = new ApuDetalle();
        d.seccionId = mo.id;
        d.insumoId = null;
        d.descripcion = "MO-PENDIENTE";
        d.orden = 1;
        d.esHerramientaMenor = false;
        d.cantidad = new BigDecimal("1");
        d.rendimiento = new BigDecimal("0.5");
        d.unidad = "h";
        d.tarifaJornal = BigDecimal.ZERO;
        detalleRepository.persist(d);

        ApuCalculado recalc = apuCalculoService.recalcular(apu);
        assertNotNull(recalc, "El motor no debe lanzar NPE");
        // Costo de la fila pendiente: 1 × 0 × 0.5 = 0; subtotalN = 0
        assertEquals(
                0, recalc.subtotalN().compareTo(BigDecimal.ZERO), "Subtotal N = 0 con fila pendiente (override 0)");
    }

    private static long apuInternalId(java.util.UUID publicId, DataSource ds) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM apu WHERE public_id = ?")) {
            ps.setObject(1, publicId);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }
}
