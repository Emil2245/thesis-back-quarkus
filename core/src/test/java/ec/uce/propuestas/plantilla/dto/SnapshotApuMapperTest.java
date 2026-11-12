package ec.uce.propuestas.plantilla.dto;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.plantilla.service.SnapshotApuMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Plan 04 (P-26) — Pruebas del mapper JSONB.
 *
 * <p><b>Snapshot price-free (Plan 04 §1):</b> el writer NUNCA persiste
 * precios efectivos, IDs de insumo ni links al APU origen — el compilador
 * impide que se introduzca un campo de precio en {@link
 * ec.uce.propuestas.plantilla.service.SnapshotApuMapper.SnapshotFila} o en
 * {@link ec.uce.propuestas.plantilla.service.SnapshotApuMapper.SnapshotLinea}.
 * El reader ignora silenciosamente los campos heredados del seed V004
 * ({@code precioOverride}, {@code tarifaJornal}, {@code precioUnitarioTarifa},
 * {@code costo} y demás).
 */
@QuarkusTest
class SnapshotApuMapperTest {

    @Inject
    SnapshotApuMapper mapper;

    @Test
    void writer_nunca_persiste_precios_ni_ids() {
        List<SnapshotApuMapper.SnapshotBloque> bloques = List.of(
                new SnapshotApuMapper.SnapshotBloque(
                        "EQUIPO", List.of(new SnapshotApuMapper.SnapshotFila("EQUIPO", true, null, null, null))),
                new SnapshotApuMapper.SnapshotBloque(
                        "MANO_OBRA",
                        List.of(new SnapshotApuMapper.SnapshotFila(
                                "MANO_OBRA", false, "MO-001", new BigDecimal("1"), new BigDecimal("0.5")))));

        String json = mapper.escribir(bloques);

        assertFalse(json.contains("precioOverride"));
        assertFalse(json.contains("precioUnitario"));
        assertFalse(json.contains("insumoId"));
        assertFalse(json.contains("apuId"));
        assertFalse(json.contains("costo"));
        assertFalse(json.contains("tarifaJornal"));
        assertTrue(json.contains("\"esHerramientaMenor\":true"));
        assertTrue(json.contains("\"insumoCodigo\""));
    }

    @Test
    void writer_nunca_persiste_override_aunque_sea_explicito() {
        // Antes (decisión errónea N04 §8): el writer emitía precioOverride cuando
        // era no-null. Ahora (Plan 04 §1, snapshot price-free): el compilador no
        // permite fijar un precio en SnapshotFila, pero si alguna vez alguien
        // modificara el writer para aceptar override, la verificación del JSON
        // afirma que NO se persiste.
        List<SnapshotApuMapper.SnapshotBloque> bloques = List.of(new SnapshotApuMapper.SnapshotBloque(
                "MATERIAL",
                List.of(new SnapshotApuMapper.SnapshotFila("MATERIAL", false, "MA-1", new BigDecimal("2"), null))));

        String json = mapper.escribir(bloques);
        assertFalse(json.contains("precioOverride"), "Snapshot price-free: nunca se persiste ningún campo de precio");
        assertFalse(json.contains("\"costo\""));
        assertFalse(json.contains("tarifaJornal"));
        assertFalse(json.contains("precioUnitarioTarifa"));
    }

    @Test
    void reader_tolera_campos_extra_v004_y_ignora_precios() {
        // El reader IGNORA silenciosamente precioOverride, tarifaJornal,
        // precioUnitarioTarifa, costo y demás campos heredados del workbook V004.
        String json = "{\"secciones\":["
                + "{\"tipo\":\"EQUIPO\",\"lineas\":[{\"esHerramientaMenor\":true,"
                + "\"tarifaJornal\":\"99.99\",\"costo\":\"999\"}]},"
                + "{\"tipo\":\"MANO_OBRA\",\"lineas\":[{\"insumoCodigo\":\"MO-001\","
                + "\"cantidad\":\"0.1\",\"rendimiento\":\"0.5\",\"precioOverride\":\"5.55\","
                + "\"tarifaJornal\":\"5.55\",\"costo\":\"0.5\",\"orden\":2,\"descripcion\":\"heredado\","
                + "\"seccionTipo\":\"MANO_OBRA\",\"tipoInsumo\":\"MANO_OBRA\","
                + "\"publicId\":\"0192f6c4-7c8a-7000-8000-000000abcdef\"}]},"
                + "{\"tipo\":\"MATERIAL\",\"lineas\":[]},{\"tipo\":\"TRANSPORTE\",\"lineas\":[]}]}";

        // No lanza
        List<SnapshotApuMapper.SnapshotBloque> bloques = mapper.leer(json);
        assertEquals(4, bloques.size());

        // HM en EQUIPO, MO con insumoCodigo preservado y SIN campo de precio.
        List<SnapshotApuMapper.SnapshotLinea> lineas = mapper.lineasNoHm(json);
        assertEquals(1, lineas.size());
        SnapshotApuMapper.SnapshotLinea fila = lineas.get(0);
        assertEquals("MO-001", fila.insumoCodigo());
        assertEquals(0, new BigDecimal("0.1").compareTo(fila.cantidad()));
        assertEquals(0, new BigDecimal("0.5").compareTo(fila.rendimiento()));
        // SnapshotLinea ya no expone precioOverride (campo eliminado en el
        // record). Si el reader lo tolerara y lo expusiera, este test fallaría
        // en compilación.
    }

    @Test
    void reader_acepta_snapshot_sin_secciones() {
        assertEquals(0, mapper.leer("{}").size());
        assertEquals(0, mapper.leer("{\"secciones\":[]}").size());
    }

    @Test
    void reader_round_trip_writer() {
        List<SnapshotApuMapper.SnapshotBloque> bloques = List.of(
                new SnapshotApuMapper.SnapshotBloque(
                        "EQUIPO", List.of(new SnapshotApuMapper.SnapshotFila("EQUIPO", true, null, null, null))),
                new SnapshotApuMapper.SnapshotBloque(
                        "MANO_OBRA",
                        List.of(
                                new SnapshotApuMapper.SnapshotFila(
                                        "MANO_OBRA", false, null, new BigDecimal("1"), new BigDecimal("1")),
                                new SnapshotApuMapper.SnapshotFila(
                                        "MANO_OBRA", false, null, new BigDecimal("2"), new BigDecimal("0.5")))),
                new SnapshotApuMapper.SnapshotBloque(
                        "MATERIAL",
                        List.of(new SnapshotApuMapper.SnapshotFila(
                                "MATERIAL", false, null, new BigDecimal("5"), null))),
                new SnapshotApuMapper.SnapshotBloque("TRANSPORTE", List.of()));

        String json = mapper.escribir(bloques);
        List<SnapshotApuMapper.SnapshotBloque> reloaded = mapper.leer(json);

        assertEquals(4, reloaded.size());
        assertEquals(2, reloaded.get(1).lineas().size(), "MO con 2 filas");
        assertEquals(1, reloaded.get(2).lineas().size(), "MATERIAL con 1 fila");
        assertEquals(0, reloaded.get(3).lineas().size(), "TRANSPORTE vacío");
    }
}
