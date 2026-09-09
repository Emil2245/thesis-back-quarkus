package ec.uce.propuestas.plantilla.dto;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.plantilla.service.SnapshotProyectoMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Plan 06 (P-46, N04 §A8) — Pruebas del mapper JSONB de plantillas de proyecto.
 *
 * <p><b>Snapshot price-free y estructural (Plan 06 §1):</b> el writer NUNCA
 * persiste precios efectivos, IDs, cantidades de obra (rubros.cantidad), ni
 * log/history. Sólo los campos estructurales y los coeficientes de APU
 * estructural ({@code cantidad} / {@code rendimiento} de filas APU). El
 * compilador impide fijar precios/IDs/cantidades de obra en los records del
 * mapper.
 *
 * <p><b>Cabecera reutilizable (P-46 §1, N04 §A8):</b> el bloque
 * {@code cabecera} arrastra codigo, descripcion, anio, fechaInicio,
 * plazoEjecucion, plazoUnidad, direccionInstitucional, subdireccionInstitucional,
 * tituloEt1 y tituloEt2 — excluyendo IDs, owner, estado, logo, nombre del
 * proyecto (autoritativo del request al aplicar), lineage y timestamps.
 *
 * <p><b>Tolerancia hacia atrás (seed V004):</b> el reader IGNORA
 * silenciosamente los campos heredados del seed V004 (keys como
 * {@code apus[].codigo} dentro de capítulos, o campos de precio). El reader
 * también tolera la forma mínima {@code {"capitulos":[{"item":"1"}]}}.
 */
@QuarkusTest
class SnapshotProyectoMapperTest {

    @Inject
    SnapshotProyectoMapper mapper;

    @Test
    void writer_nunca_persiste_precios_ni_ids_ni_cantidades_de_obra() {
        var filas = List.of(
                new SnapshotProyectoMapper.SnapshotFilaApu("EQUIPO", true, null, null, null),
                new SnapshotProyectoMapper.SnapshotFilaApu(
                        "MANO_OBRA", false, "MO-001", new BigDecimal("0.100000"), new BigDecimal("0.100000")));
        var apu = new SnapshotProyectoMapper.SnapshotApuEstructural("RP-001", "Replanteo y nivelación", "m2", filas);
        var rubro = new SnapshotProyectoMapper.SnapshotRubro("1.1.1", "RP-001", "Replanteo y nivelación", "m2", apu);
        var hijo = new SnapshotProyectoMapper.SnapshotCapitulo(
                "1.1", "MOVIMIENTO DE TIERRAS", 1, "1", List.of(), List.of(rubro));
        var raiz = new SnapshotProyectoMapper.SnapshotCapitulo(
                "1", "OBRAS PRELIMINARES", 1, null, List.of(hijo), List.of());
        SnapshotProyectoMapper.Snapshot snap = new SnapshotProyectoMapper.Snapshot(
                new SnapshotProyectoMapper.SnapshotCabecera(
                        "P-001",
                        "Demo cabecera",
                        (short) 2026,
                        LocalDate.parse("2026-01-15"),
                        (short) 4,
                        "MES",
                        "UCE",
                        "Subdirección demo",
                        "ESPECIFICACIONES TÉCNICAS",
                        "BORRADOR-ET"),
                new SnapshotProyectoMapper.SnapshotParametros(
                        new BigDecimal("0.0500"),
                        new BigDecimal("0.1800"),
                        new BigDecimal("0.1500"),
                        "USD",
                        true,
                        true,
                        true,
                        false,
                        false,
                        false,
                        "Este precio no incluye IVA",
                        "AUTOGENERADO"),
                new SnapshotProyectoMapper.SnapshotTitulos("ESPECIFICACIONES TÉCNICAS", "BORRADOR-ET"),
                List.of(raiz));

        String json = mapper.escribir(snap);

        // Precios efectivos / derivados / IDs / cronograma / firmantes / log
        assertFalse(json.contains("precioOverride"), "Sin override");
        assertFalse(json.contains("precioUnitario"), "Sin precio unitario");
        assertFalse(json.contains("precioTotal"), "Sin precio total");
        assertFalse(json.contains("costoDirecto"), "Sin costo directo");
        assertFalse(json.contains("costoIndirecto"), "Sin costo indirecto");
        assertFalse(json.contains("costoTotal"), "Sin costo total");
        assertFalse(json.contains("tarifaJornal"), "Sin tarifa jornal");
        assertFalse(json.contains("precioUnitarioTarifa"), "Sin precio unitario tarifa");
        // IDs (no hay forma de fijarlos en el writer — el compilador lo impide)
        assertFalse(json.contains("insumoId"), "Sin IDs de insumo");
        assertFalse(json.contains("apuId"), "Sin IDs de APU");
        assertFalse(json.contains("rubroId"), "Sin IDs de rubro");
        assertFalse(json.contains("proyectoId"), "Sin IDs de proyecto");
        // Cantidades de obra (rubros.cantidad) — NUNCA se persisten
        assertFalse(json.contains("\"cantidadObra\""), "Sin cantidad de obra explícita");
        // Cronograma / firmantes / log
        assertFalse(json.contains("cronograma"), "Sin cronograma");
        assertFalse(json.contains("firmante"), "Sin firmantes");
        assertFalse(json.contains("actividad"), "Sin actividades");
        assertFalse(json.contains("log"), "Sin log");
        // Cabecera — tampoco arrastra owner / estado / logo / lineage / nombre
        assertFalse(json.contains("\"usuarioId\""), "Cabecera NO arrastra owner");
        assertFalse(json.contains("\"estado\""), "Cabecera NO arrastra estado");
        assertFalse(json.contains("\"logo\""), "Cabecera NO arrastra logo");
        assertFalse(json.contains("\"nombreProyecto\""), "Cabecera NO arrastra nombre del proyecto");
        assertFalse(json.contains("\"plantillaProyectoOrigenId\""), "Cabecera NO arrastra lineage");
        assertFalse(json.contains("\"createdAt\""), "Cabecera NO arrastra timestamps");

        // Cabecera reusable SÍ se preserva
        assertTrue(json.contains("\"codigo\":\"P-001\""), "codigo de cabecera persiste");
        assertTrue(json.contains("\"anio\":2026"), "anio de cabecera persiste");
        assertTrue(json.contains("\"plazoEjecucion\":4"), "plazoEjecucion de cabecera persiste");
        assertTrue(json.contains("\"plazoUnidad\":\"MES\""), "plazoUnidad de cabecera persiste");
        assertTrue(json.contains("\"direccionInstitucional\":\"UCE\""));
        assertTrue(json.contains("\"tituloEt1\":\"ESPECIFICACIONES TÉCNICAS\""));

        // Coeficientes APU estructural SÍ se preservan (no son rubro.cantidad)
        assertTrue(json.contains("\"insumoCodigo\":\"MO-001\""));
        assertTrue(json.contains("\"cantidad\":\"0.100000\""), "cantidad de FILA APU persiste");
        assertTrue(json.contains("\"rendimiento\":\"0.100000\""));
        assertTrue(json.contains("\"esHerramientaMenor\":true"));
    }

    @Test
    void writer_round_trip_reader_recupera_estructura() {
        SnapshotProyectoMapper.SnapshotCapitulo sub = new SnapshotProyectoMapper.SnapshotCapitulo(
                "1.1",
                "Subtítulo",
                1,
                "1",
                List.of(),
                List.of(new SnapshotProyectoMapper.SnapshotRubro(
                        "1.1.1",
                        "RP-001",
                        "Replanteo",
                        "m2",
                        new SnapshotProyectoMapper.SnapshotApuEstructural(
                                "RP-001",
                                "Replanteo",
                                "m2",
                                List.of(
                                        new SnapshotProyectoMapper.SnapshotFilaApu("EQUIPO", true, null, null, null),
                                        new SnapshotProyectoMapper.SnapshotFilaApu(
                                                "MANO_OBRA", false, "MO-001", BigDecimal.ONE, BigDecimal.ONE))))));
        SnapshotProyectoMapper.Snapshot snap = new SnapshotProyectoMapper.Snapshot(
                new SnapshotProyectoMapper.SnapshotCabecera(
                        "P-001", "Demo", (short) 2026, null, (short) 6, "SEMANA", "UCE", null, "ET-1", "ET-2"),
                new SnapshotProyectoMapper.SnapshotParametros(
                        new BigDecimal("0.0500"),
                        null,
                        new BigDecimal("0.1500"),
                        "USD",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null),
                null,
                List.of(new SnapshotProyectoMapper.SnapshotCapitulo("1", "OBRAS", 1, null, List.of(sub), List.of())));

        String json = mapper.escribir(snap);
        SnapshotProyectoMapper.Snapshot reloaded = mapper.leer(json);

        assertNotNull(reloaded.parametros(), "parametros preservados");
        assertEquals(new BigDecimal("0.0500"), reloaded.parametros().porcentajeHerramientaMenor());
        assertEquals(new BigDecimal("0.1500"), reloaded.parametros().iva());
        assertEquals("USD", reloaded.parametros().moneda());

        // Cabecera round-trip
        assertNotNull(reloaded.cabecera(), "cabecera preservada");
        assertEquals("P-001", reloaded.cabecera().codigo());
        assertEquals("Demo", reloaded.cabecera().descripcion());
        assertEquals((short) 2026, reloaded.cabecera().anio());
        assertNull(reloaded.cabecera().fechaInicio());
        assertEquals((short) 6, reloaded.cabecera().plazoEjecucion());
        assertEquals("SEMANA", reloaded.cabecera().plazoUnidad());
        assertEquals("UCE", reloaded.cabecera().direccionInstitucional());
        assertNull(reloaded.cabecera().subdireccionInstitucional());
        assertEquals("ET-1", reloaded.cabecera().tituloEt1());
        assertEquals("ET-2", reloaded.cabecera().tituloEt2());

        assertNotNull(reloaded.capitulos());
        assertEquals(1, reloaded.capitulos().size());
        SnapshotProyectoMapper.SnapshotCapitulo root = reloaded.capitulos().get(0);
        assertEquals("1", root.item());
        assertEquals("OBRAS", root.descripcion());
        assertNull(root.parentItem());

        assertEquals(1, root.hijos().size());
        SnapshotProyectoMapper.SnapshotCapitulo child = root.hijos().get(0);
        assertEquals("1.1", child.item());
        assertEquals("1", child.parentItem());

        assertEquals(1, child.rubros().size());
        SnapshotProyectoMapper.SnapshotRubro rubro = child.rubros().get(0);
        assertEquals("1.1.1", rubro.item());
        assertEquals("RP-001", rubro.codigo());
        assertEquals("m2", rubro.unidad());
        assertNotNull(rubro.apu());
        assertEquals("RP-001", rubro.apu().codigo());
        assertEquals(2, rubro.apu().filas().size());
        assertTrue(rubro.apu().filas().get(0).esHerramientaMenor());
        assertEquals("MO-001", rubro.apu().filas().get(1).insumoCodigo());
    }

    @Test
    void reader_tolera_seed_v004_minimo_sin_lanzar() {
        // El seed V004 inserta:
        //   {"capitulos":[{"item":"1","apus":[{"codigo":"REP-TR-001"}]}]}
        // Forma heredada (pre-V006) — el reader debe tolerarla y no lanzar.
        String legacyV004 = "{\"capitulos\":[{\"item\":\"1\",\"apus\":[{\"codigo\":\"REP-TR-001\"}]}]}";
        SnapshotProyectoMapper.Snapshot snap = mapper.leer(legacyV004);

        assertNotNull(snap);
        assertNull(snap.cabecera(), "V004 mínimo no trae cabecera");
        assertNull(snap.parametros(), "V004 mínimo no trae parametros");
        assertNull(snap.titulos(), "V004 mínimo no trae titulos");
        assertNotNull(snap.capitulos());
        assertEquals(1, snap.capitulos().size());
        SnapshotProyectoMapper.SnapshotCapitulo cap = snap.capitulos().get(0);
        assertEquals("1", cap.item());
        // El reader ignora la clave legacy `apus` (no canónica) — no la rompe.
        assertTrue(cap.rubros().isEmpty(), "apus legacy no se interpreta como rubros");
    }

    @Test
    void reader_tolera_campos_extra_conocidos_y_desconocidos() {
        // El reader IGNORA silenciosamente campos extra del V004 (precios,
        // IDs, log) y de cualquier consumidor externo.
        String json = "{\"cabecera\":{\"codigo\":\"P-X\",\"anio\":2026,\"plazoEjecucion\":4,"
                + "\"plazoUnidad\":\"MES\",\"direccionInstitucional\":\"UCE\",\"_legacy\":42},"
                + "\"parametros\":{\"porcentajeHerramientaMenor\":\"0.0500\","
                + "\"porcentajeIndirecto\":\"0.1800\",\"iva\":\"0.1500\",\"moneda\":\"USD\","
                + "\"campoHeredadoX\":\"valor\","
                + "\"rango_hm_min\":\"0.0000\",\"rango_hm_max\":\"0.2000\"},"
                + "\"titulos\":{\"tituloEt1\":\"ESPECIFICACIONES\",\"tituloEt2\":\"BORRADOR\"},"
                + "\"capitulos\":[{\"item\":\"1\",\"descripcion\":\"X\",\"orden\":1,"
                + "\"parentItem\":null,\"hijos\":[],\"rubros\":[],\"_legacy_id\":42}],"
                + "\"_legacy_top\":\"value\",\"log_actividad\":[{\"evento\":\"x\"}]}";

        SnapshotProyectoMapper.Snapshot snap = mapper.leer(json);
        assertNotNull(snap.cabecera(), "cabecera preservada");
        assertEquals("P-X", snap.cabecera().codigo());
        assertEquals((short) 2026, snap.cabecera().anio());
        assertEquals("UCE", snap.cabecera().direccionInstitucional());
        assertNotNull(snap.parametros());
        assertEquals("USD", snap.parametros().moneda());
        assertEquals("BORRADOR", snap.titulos().tituloEt2());
        assertEquals(1, snap.capitulos().size());
    }

    @Test
    void reader_acepta_snapshot_vacio_o_solo_secciones() {
        assertNotNull(mapper.leer(null));
        assertNotNull(mapper.leer(""));
        assertEquals(0, mapper.leer("{}").capitulos().size());
        assertEquals(0, mapper.leer("{\"capitulos\":[]}").capitulos().size());
        // La cabecera null es backward-compatible con V004.
        assertNull(mapper.leer("{\"capitulos\":[]}").cabecera());
    }

    @Test
    void reader_ignora_cantidad_de_obra_en_rubros_y_filas() {
        // Aunque un consumidor externo inyectara "cantidad" en un rubro (legacy),
        // el reader no debe interpretar nada que no sea del snapshot canónico.
        String json = "{\"capitulos\":[{\"item\":\"1\",\"descripcion\":\"X\",\"orden\":1,\"parentItem\":null,"
                + "\"hijos\":[],"
                + "\"rubros\":[{\"item\":\"1.1\",\"codigo\":\"RP-001\",\"descripcion\":\"X\","
                + "\"unidad\":\"m2\",\"cantidad\":\"350.000000\",\"precioUnitario\":\"0.582950\","
                + "\"precioTotal\":\"207.760000\",\"apu\":{\"codigo\":\"RP-001\"}}]}]}";
        SnapshotProyectoMapper.Snapshot snap = mapper.leer(json);
        SnapshotProyectoMapper.SnapshotRubro rubro =
                snap.capitulos().get(0).rubros().get(0);
        // La `cantidad` del rubro (cantidad de obra) NO se persiste en
        // SnapshotRubro — el record no la expone. Al aplicar, el nuevo rubro
        // arranca con cantidad = 0 (pendiente), nunca con la cantidad original.
        assertEquals("1.1", rubro.item());
        assertEquals("RP-001", rubro.codigo());
        // El apu sólo expone codigo/descripcion/unidad/filas; precios heredados se ignoran.
        assertNotNull(rubro.apu());
        assertEquals("RP-001", rubro.apu().codigo());
    }

    @Test
    void writer_preserva_cabecera_con_todos_los_campos() {
        SnapshotProyectoMapper.SnapshotCabecera cabecera = new SnapshotProyectoMapper.SnapshotCabecera(
                "P-XYZ",
                "Descripción demo",
                (short) 2027,
                LocalDate.parse("2027-03-15"),
                (short) 8,
                "MES",
                "UCE - Dirección",
                "UCE - Subdirección",
                "ET-1 demo",
                "ET-2 demo");
        SnapshotProyectoMapper.Snapshot snap = new SnapshotProyectoMapper.Snapshot(cabecera, null, null, List.of());

        String json = mapper.escribir(snap);
        SnapshotProyectoMapper.Snapshot reloaded = mapper.leer(json);

        assertNotNull(reloaded.cabecera(), "cabecera persistida y recuperada");
        SnapshotProyectoMapper.SnapshotCabecera c = reloaded.cabecera();
        assertEquals("P-XYZ", c.codigo());
        assertEquals("Descripción demo", c.descripcion());
        assertEquals((short) 2027, c.anio());
        assertEquals(LocalDate.parse("2027-03-15"), c.fechaInicio());
        assertEquals((short) 8, c.plazoEjecucion());
        assertEquals("MES", c.plazoUnidad());
        assertEquals("UCE - Dirección", c.direccionInstitucional());
        assertEquals("UCE - Subdirección", c.subdireccionInstitucional());
        assertEquals("ET-1 demo", c.tituloEt1());
        assertEquals("ET-2 demo", c.tituloEt2());
    }

    @Test
    void reader_fallback_a_titulos_legacy_para_tituloEt() {
        // Snapshots previos a la cabecera canónica sólo tienen `titulos`.
        // El reader debe poblar `cabecera.tituloEt1/Et2` desde `titulos` para
        // que el nuevo proyecto no pierda la fila del export.
        String legacy =
                "{\"titulos\":{\"tituloEt1\":\"LEGACY-ET1\",\"tituloEt2\":\"LEGACY-ET2\"}," + "\"capitulos\":[]}";
        SnapshotProyectoMapper.Snapshot snap = mapper.leer(legacy);

        assertNotNull(snap.cabecera(), "cabecera se materializa desde titulos legacy");
        assertEquals("LEGACY-ET1", snap.cabecera().tituloEt1());
        assertEquals("LEGACY-ET2", snap.cabecera().tituloEt2());
    }

    @Test
    void reader_cabecera_gana_sobre_titulos_legacy() {
        // Si ambos bloques están presentes, cabecera es la fuente canónica.
        String ambos = "{\"cabecera\":{\"codigo\":\"P-001\",\"tituloEt1\":\"CANONICA\"},"
                + "\"titulos\":{\"tituloEt1\":\"LEGACY\",\"tituloEt2\":\"LEGACY-2\"},"
                + "\"capitulos\":[]}";
        SnapshotProyectoMapper.Snapshot snap = mapper.leer(ambos);

        assertNotNull(snap.cabecera());
        assertEquals("CANONICA", snap.cabecera().tituloEt1());
        // tituloEt2 no está en cabecera → cae al legacy.
        assertEquals("LEGACY-2", snap.cabecera().tituloEt2());
    }

    @Test
    void writer_cabecera_no_arrastra_owner_estado_logo_nombre_ni_lineage() {
        // El compilador impide fijar IDs/owner/estado/logo/lineage en
        // SnapshotCabecera; los nombres de campo JSON no deben aparecer en
        // el output. Esta es una defensa adicional por si alguien añadiera un
        // campo accidentalmente en el futuro.
        SnapshotProyectoMapper.SnapshotCabecera cabecera = new SnapshotProyectoMapper.SnapshotCabecera(
                "P-001", "X", (short) 2026, null, (short) 4, "MES", "UCE", null, null, null);
        SnapshotProyectoMapper.Snapshot snap = new SnapshotProyectoMapper.Snapshot(cabecera, null, null, List.of());
        String json = mapper.escribir(snap);

        // Campos prohibidos en cabecera.
        assertFalse(json.contains("\"usuarioId\""), "Sin owner");
        assertFalse(json.contains("\"estado\""), "Sin estado");
        assertFalse(json.contains("\"logo\""), "Sin logo binario");
        assertFalse(json.contains("\"nombreProyecto\""), "Sin nombre (autoritativo del request)");
        assertFalse(json.contains("\"plantillaProyectoOrigenId\""), "Sin lineage");
        assertFalse(json.contains("\"createdAt\""), "Sin timestamps");
        assertFalse(json.contains("\"updatedAt\""), "Sin timestamps");
        assertFalse(json.contains("\"fechaCreacion\""), "Sin timestamps");
    }
}
