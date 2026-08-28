package ec.uce.propuestas.proyecto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.proyecto.dto.ParametrosProyectoEditarRequest;
import ec.uce.propuestas.proyecto.entity.EstadoProyecto;
import ec.uce.propuestas.proyecto.entity.PlazoUnidad;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.repository.ProyectoRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * WU-06 — Seam neutro de cambio de parámetros.
 *
 * <p>Verifica que {@link ParametrosProyectoService#actualizar} devuelve un
 * {@link ParametrosProyectoCambio} con flags de cambio %HM / %CI basados en
 * comparación numérica (escala-insensible, null-safe), expone el id interno
 * del proyecto y no introduce ningún tipo ni invocación de recálculo.
 */
@QuarkusTest
class ParametrosProyectoCambioTest {

    @Inject
    ParametrosProyectoService parametrosService;

    @Inject
    ProyectoRepository proyectoRepository;

    @Inject
    DataSource ds;

    @BeforeEach
    void reset() throws Exception {
        try (Connection con = ds.getConnection();
                Statement st = con.createStatement()) {
            st.execute("TRUNCATE TABLE apu_detalle, apu_seccion, apu, rubro, capitulo, presupuesto, "
                    + "insumo, base_insumos, parametros_proyecto, firmante, proyecto, "
                    + "token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
        // Relajar rangos para que las pruebas se concentren en la lógica del seam.
        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement("UPDATE parametros_sistema SET "
                        + "rango_hm_min = 0.0000, rango_hm_max = 1.0000, "
                        + "rango_ci_min = 0.0000, rango_ci_max = 1.0000, "
                        + "rango_iva_min = 0.0000, rango_iva_max = 1.0000 WHERE id = 1")) {
            ps.executeUpdate();
        }
    }

    private Long crearProyecto(Long usuarioId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Proyecto p = new Proyecto();
            p.usuarioId = usuarioId;
            p.nombreProyecto = "Cambio parametros";
            p.anio = (short) 2026;
            p.plazoEjecucion = (short) 6;
            p.plazoUnidad = PlazoUnidad.MES;
            p.estado = EstadoProyecto.BORRADOR;
            p.direccionInstitucional = "GAD";
            proyectoRepository.persist(p);
            return p.id;
        });
    }

    private ParametrosProyectoEditarRequest req(
            String hm, String ci, String iva, String moneda) {
        return new ParametrosProyectoEditarRequest(
                hm == null ? null : new BigDecimal(hm),
                ci == null ? null : new BigDecimal(ci),
                iva == null ? null : new BigDecimal(iva),
                moneda);
    }

    @Test
    void TC_WU06_primer_update_con_valores_iguales_marca_false_y_devuelve_id_interno() {
        Long usuarioId = 1L;
        Long proyectoId = crearProyecto(usuarioId);

        ParametrosProyectoCambio cambio =
                parametrosService.actualizar(usuarioId, proyectoId, req("0.0500", "0.1800", "0.1500", "USD"));

        assertEquals(proyectoId, cambio.proyectoId(), "proyectoId interno debe ser el Long resuelto");
        assertFalse(cambio.porcentajeHerramientaMenorCambio(), "%HM igual al default 0.0500 → no cambia");
        assertTrue(cambio.porcentajeIndirectoCambio(), "%CI pasa de null a 0.1800 → cambia");
        assertNotNull(cambio.parametros());
        assertEquals(proyectoId, cambio.parametros().proyectoId());
        assertEquals(new BigDecimal("0.0500"), cambio.parametros().porcentajeHerramientaMenor());
        assertEquals(new BigDecimal("0.1800"), cambio.parametros().porcentajeIndirecto());
    }

    @Test
    void TC_WU06_cambio_escala_insensible_marca_false() {
        Long usuarioId = 1L;
        Long proyectoId = crearProyecto(usuarioId);

        // Sembrar HM 0.0700 con CI 0.1800.
        parametrosService.actualizar(usuarioId, proyectoId, req("0.0700", "0.1800", "0.1500", "USD"));

        // Reenviar HM 0.07 (otra escala) y CI 0.1800 igual → numéricamente sin cambio.
        ParametrosProyectoCambio cambio =
                parametrosService.actualizar(usuarioId, proyectoId, req("0.07", "0.1800", "0.1500", "USD"));

        assertFalse(cambio.porcentajeHerramientaMenorCambio(),
                "0.0700 vs 0.07 son numéricamente iguales (escala-insensible)");
        assertFalse(cambio.porcentajeIndirectoCambio(), "%CI igual → no cambia");
        assertEquals(proyectoId, cambio.proyectoId());
    }

    @Test
    void TC_WU06_cambio_numerico_marca_true_para_ambos_flags() {
        Long usuarioId = 1L;
        Long proyectoId = crearProyecto(usuarioId);

        parametrosService.actualizar(usuarioId, proyectoId, req("0.0500", "0.1800", "0.1500", "USD"));

        ParametrosProyectoCambio cambio =
                parametrosService.actualizar(usuarioId, proyectoId, req("0.0700", "0.2500", "0.1500", "USD"));

        assertTrue(cambio.porcentajeHerramientaMenorCambio());
        assertTrue(cambio.porcentajeIndirectoCambio());
        assertEquals(proyectoId, cambio.proyectoId());
    }

    @Test
    void TC_WU06_limpiar_ci_marca_true_porque_no_es_null_a_null() {
        Long usuarioId = 1L;
        Long proyectoId = crearProyecto(usuarioId);

        // Sembrar CI con valor.
        parametrosService.actualizar(usuarioId, proyectoId, req("0.0500", "0.1800", "0.1500", "USD"));

        // Pasar CI a null → cambio real (null-safe).
        ParametrosProyectoCambio cambio =
                parametrosService.actualizar(usuarioId, proyectoId, req("0.0500", null, "0.1500", "USD"));

        assertTrue(cambio.porcentajeIndirectoCambio(), "0.1800 → null cuenta como cambio");
        assertFalse(cambio.porcentajeHerramientaMenorCambio(), "%HM sin tocar → no cambia");
    }

    @Test
    void TC_WU06_seam_neutral_no_importa_recalculo() throws Exception {
        Path servicio = Path.of(
                "src/main/java/ec/uce/propuestas/proyecto/service/ParametrosProyectoService.java");
        Path costura = Path.of(
                "src/main/java/ec/uce/propuestas/proyecto/service/ParametrosProyectoCambio.java");
        assertTrue(Files.exists(servicio), "Debe existir el source del servicio");
        assertTrue(Files.exists(costura), "Debe existir el source del seam");
        String fuenteServicio = Files.readString(servicio).toLowerCase();
        String fuenteCostura = Files.readString(costura).toLowerCase();
        assertFalse(fuenteServicio.contains("recalculo"),
                "ParametrosProyectoService no debe importar ni invocar nada de recálculo");
        assertFalse(fuenteCostura.contains("recalculo"),
                "ParametrosProyectoCambio (seam neutro) no debe importar nada de recálculo");
    }
}
