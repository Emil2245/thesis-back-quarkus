package ec.uce.propuestas.apu.service;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.apu.dto.ApuCrearRequest;
import ec.uce.propuestas.apu.dto.ApuDetalleCrearRequest;
import ec.uce.propuestas.apu.dto.ApuResponse;
import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.entity.ApuDetalle;
import ec.uce.propuestas.apu.entity.ApuSeccion;
import ec.uce.propuestas.apu.repository.ApuDetalleRepository;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.repository.ApuSeccionRepository;
import ec.uce.propuestas.motor.SeccionTipo;
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
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Write-through del cálculo de un APU (RNF-02 a nivel APU). Reproduce las
 * fórmulas DM §16 con un caso conocido: HM 5 % × 8.99 (subtotal N) = 0.4495.
 */
@QuarkusTest
class ApuCalculoServiceIT {

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @Inject
    ApuCrudService apuService;

    @Inject
    ApuRepository apuRepository;

    @Inject
    ApuSeccionRepository seccionRepository;

    @Inject
    ApuDetalleRepository detalleRepository;

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
                                "nombreProyecto", "Pozo APU",
                                "anio", (short) 2026,
                                "plazoEjecucion", (short) 4,
                                "plazoUnidad", "MES",
                                "direccionInstitucional", "GAD"))
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

    @Test
    void writeThrough_hm5pct_por_subtotalN_y_ci_heredado() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "apu-calc@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        Long insumoMo = crearInsumo(token, proyectoId, "MO-001", "MANO_OBRA", "Armador", "h", 8.99);
        Long insumoMat = crearInsumo(token, proyectoId, "MA-001", "MATERIAL", "Cemento", "kg", 1.5);

        ApuResponse apu = apuService.crearComoRespuesta(presupuestoId, new ApuCrearRequest("APU-CALC", "Pozo de agua", "m³"));
        Long apuId = internalId(apu.id());

        apu = apuService.agregarDetalle(
                apuId,
                new ApuDetalleCrearRequest(SeccionTipo.MANO_OBRA, insumoMo, new BigDecimal("1"), new BigDecimal("1")));
        apu = apuService.agregarDetalle(
                apuId, new ApuDetalleCrearRequest(SeccionTipo.MATERIAL, insumoMat, new BigDecimal("2"), null));

        // subtotalN = 1 × 8.99 × 1 = 8.99; HM = 0.05 × 8.99 = 0.4495
        assertEquals(
                0, apu.costoDirecto().compareTo(new BigDecimal("12.4395")), "CD = M(0.4495) + N(8.99) + O(3.0) + P(0)");
        assertEquals(
                0, apu.costoIndirecto().compareTo(BigDecimal.ZERO.setScale(6)), "CI: %CI hereda NULL del proyecto → 0");
        assertEquals(0, apu.costoTotal().compareTo(new BigDecimal("12.4395")));

        // write-through a BD
        Apu persistido = apuRepository.findById(apuId);
        assertEquals(
                0,
                persistido.costoDirecto.compareTo(new BigDecimal("12.439500")),
                "escala 6 en BD: 0.449500 + 8.990000 + 3.000000");
        assertEquals(0, persistido.costoTotal.compareTo(new BigDecimal("12.439500")));

        // subtotales por sección en BD
        List<ApuSeccion> secciones = seccionRepository.listarDeApu(apuId);
        assertEquals(0, seccion(secciones, SeccionTipo.EQUIPO).subtotal.compareTo(new BigDecimal("0.449500")));
        assertEquals(0, seccion(secciones, SeccionTipo.MANO_OBRA).subtotal.compareTo(new BigDecimal("8.990000")));
        assertEquals(0, seccion(secciones, SeccionTipo.MATERIAL).subtotal.compareTo(new BigDecimal("3.000000")));
        assertEquals(0, seccion(secciones, SeccionTipo.TRANSPORTE).subtotal.compareTo(BigDecimal.ZERO));

        // fila HM: costo = 0.449500, descripción regenerada, cantidad NULL
        ApuSeccion equipo = seccion(secciones, SeccionTipo.EQUIPO);
        List<ApuDetalle> filasEquipo = detalleRepository.listarDeSeccion(equipo.id);
        assertEquals(1, filasEquipo.size());
        ApuDetalle hm = filasEquipo.get(0);
        assertTrue(hm.esHerramientaMenor);
        assertNull(hm.cantidad);
        assertNull(hm.precioUnitarioTarifa);
        assertEquals("Herramienta Menor 5%MO", hm.descripcion);
        assertEquals(0, hm.costo.compareTo(new BigDecimal("0.449500")));

        // fila MO: costo_hora = 8.990000, costo = 8.990000
        ApuSeccion mo = seccion(secciones, SeccionTipo.MANO_OBRA);
        ApuDetalle filaMo = detalleRepository.listarDeSeccion(mo.id).get(0);
        assertEquals(0, filaMo.costoHora.compareTo(new BigDecimal("8.990000")));
        assertEquals(0, filaMo.costo.compareTo(new BigDecimal("8.990000")));
        assertNotNull(filaMo.insumoId);
        assertEquals("h", filaMo.unidad);

        // fila MATERIAL: costo = 2 × 1.5 = 3.000000, sin costo_hora ni rendimiento
        ApuSeccion mat = seccion(secciones, SeccionTipo.MATERIAL);
        ApuDetalle filaMat = detalleRepository.listarDeSeccion(mat.id).get(0);
        assertEquals(0, filaMat.costo.compareTo(new BigDecimal("3.000000")));
        assertNull(filaMat.rendimiento);
    }

    @Test
    void writeThrough_mutar_cantidad_actualiza_total_y_persiste() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "apu-calc2@ex.com");
        Long proyectoId = crearProyecto(token);
        Long presupuestoId = insertarPresupuesto(proyectoId);
        Long insumoMo = crearInsumo(token, proyectoId, "MO-002", "MANO_OBRA", "Maestro", "h", 4.0);

        ApuResponse apu = apuService.crearComoRespuesta(presupuestoId, new ApuCrearRequest("APU-CALC-2", "Fila sistema", "u"));
        Long apuId = internalId(apu.id());
        apu = apuService.agregarDetalle(
                apuId,
                new ApuDetalleCrearRequest(
                        SeccionTipo.MANO_OBRA, insumoMo, new BigDecimal("2"), new BigDecimal("0.5")));

        // CD = HM(0.05 × 4.0) + N(2 × 4 × 0.5 = 4.0) = 0.2 + 4.0 = 4.2
        assertEquals(0, apu.costoTotal().compareTo(new BigDecimal("4.2")));

        assertEquals(0, apuRepository.findById(apuId).costoTotal.compareTo(new BigDecimal("4.200000")));
    }

    /**
     * Resuelve el {@code BIGINT} interno de un APU a partir de su UUID público.
     * Los services y repositorios consumen el id interno; el contrato expone
     * {@code publicId} (OpenSpec WU-03). Solo se usa desde tests de integración
     * para pasar de la respuesta pública a la API tipada en {@code Long}.
     */
    private Long internalId(UUID publicId) throws Exception {
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("SELECT id FROM apu WHERE public_id = ?")) {
            ps.setObject(1, publicId);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "El APU público debe resolver a un id interno");
                return rs.getLong(1);
            }
        }
    }

    private static ApuSeccion seccion(List<ApuSeccion> secciones, SeccionTipo tipo) {
        return secciones.stream().filter(s -> s.tipo == tipo).findFirst().orElseThrow();
    }
}
