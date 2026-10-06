package ec.uce.propuestas.documento;

import static ec.uce.propuestas.documento.PresupuestoDocumentoFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.documento.exportacion.ApuDocumentoProyeccion;
import ec.uce.propuestas.documento.exportacion.SnapshotDocumento;
import ec.uce.propuestas.motor.SeccionTipo;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApuDocumentoProyeccionTest {
    @Test
    void seleccionPorIdentidadEnOrdenNaturalSinCatalogoNiCodigoDeRubro() {
        var s = ApuDocumentoFixture.snapshot(true, true, decimal("0.2500"));
        var d = ApuDocumentoProyeccion.proyectar(s);
        assertEquals(s.presupuestoId(), d.presupuestoId());
        assertEquals((short) 7, d.version());
        assertEquals(List.of(id(21), id(20)), d.apus().stream().map(a -> a.id()).toList());
        assertEquals(
                List.of("1 " + ApuDocumentoFixture.TEXTO, "2 " + ApuDocumentoFixture.TEXTO),
                d.apus().stream().map(a -> a.etiquetaCodigo()).toList());
        assertEquals(ApuDocumentoFixture.TEXTO, d.apus().getFirst().codigo());
        assertEquals(s.proyecto(), d.proyecto());
        assertEquals(s.display(), d.display());
        assertEquals(s.firmantes(), d.firmantes());
        assertThrows(UnsupportedOperationException.class, () -> d.apus().clear());
    }

    @Test
    void bloquesFijosDetallesOrdenUuidHmEnPosicionRealYValoresPersistidos() {
        var s = ApuDocumentoFixture.snapshot(true, true, decimal("0.2500"));
        var a = ApuDocumentoProyeccion.proyectar(s).apus().get(1);
        assertEquals(
                List.of(SeccionTipo.EQUIPO, SeccionTipo.MANO_OBRA, SeccionTipo.MATERIAL, SeccionTipo.TRANSPORTE),
                a.bloques().stream().map(b -> b.tipo()).toList());
        var m = a.bloques().getFirst();
        assertEquals("Equipos y herramientas (M)", m.etiqueta());
        assertEquals((short) 99, m.ordenCapturado());
        assertEquals(
                List.of(id(31), id(32), id(33)),
                m.filas().stream().map(f -> f.detalle().id()).toList());
        var hm = m.filas().get(2);
        assertEquals("Herramienta Menor 7.300%MO", hm.descripcion());
        assertEquals(decimal("0.07300"), hm.porcentajeHerramientaMenor());
        assertEquals(decimal("12.000000003"), hm.detalle().costo());
        assertEquals(decimal("10.000000002"), m.filas().getFirst().detalle().costoHora());
        assertEquals(decimal("9.000000001"), m.filas().getFirst().detalle().precioEfectivo());
        assertEquals(decimal("2.000001"), m.filas().getFirst().detalle().cantidad());
        assertEquals(decimal("0.000123456"), m.filas().getFirst().detalle().rendimiento());
        assertEquals(decimal("11.123456789"), m.subtotal());
        assertEquals(java.math.BigDecimal.ZERO, a.bloques().get(1).subtotal());
        assertTrue(m.mostrarSubtotal());
        assertEquals(4, a.pie().subtotales().size());
        assertEquals(decimal("33.123456789"), a.pie().costoDirecto());
        assertEquals(decimal("4.987654321"), a.pie().costoIndirecto());
        assertEquals(decimal(EXACTO), a.pie().costoTotal());
        assertEquals(a.pie().costoTotal(), a.pie().valorOfertado());
        assertNotEquals(a.diagnostico().costoTotal(), a.pie().costoTotal());
        assertEquals(s.apus().getFirst().calculado(), a.diagnostico());
        assertThrows(UnsupportedOperationException.class, () -> m.filas().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> a.pie().subtotales().clear());
    }

    @Test
    void togglesApagadosTextoInerteYCiGlobalAunqueOverrideCapturado() {
        var s = ApuDocumentoFixture.snapshot(false, false, decimal("0.2500"));
        var a = ApuDocumentoProyeccion.proyectar(s).apus().get(1);
        assertEquals(2, a.bloques().size());
        assertEquals("Equipos y herramientas", a.bloques().getFirst().etiqueta());
        assertFalse(a.bloques().getFirst().mostrarSubtotal());
        assertTrue(a.pie().subtotales().isEmpty());
        assertNull(a.nombreProyectoHeader());
        assertEquals(a.codigo(), a.etiquetaCodigo());
        assertEquals(ApuDocumentoFixture.TEXTO, a.descripcion());
        assertEquals(ApuDocumentoFixture.TEXTO, a.pie().mensaje());
        assertEquals(decimal("0.1800"), a.pie().porcentajeIndirecto());
    }

    @Test
    void ciIndividualSoloHabilitadoYNullHereda() {
        var d = ApuDocumentoProyeccion.proyectar(ApuDocumentoFixture.snapshot(true, true, decimal("0.2500")));
        assertEquals(decimal("0.1800"), d.apus().getFirst().pie().porcentajeIndirecto());
        assertEquals(decimal("0.2500"), d.apus().get(1).pie().porcentajeIndirecto());
        assertEquals(d.proyecto().nombre(), d.apus().getFirst().nombreProyectoHeader());
    }

    @Test
    void vacioNoSeRellenaYReferenciaFaltanteFallaSinSustituir() {
        var s = ApuDocumentoFixture.snapshot(true, true, null);
        var vacio = copia(s, List.of(), s.apus());
        assertTrue(ApuDocumentoProyeccion.proyectar(vacio).apus().isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> ApuDocumentoProyeccion.proyectar(copia(s, s.capitulos(), List.of())));
    }

    @Test
    void empateDeItemUsaUuidYNoOrdenDeLista() {
        var s = ApuDocumentoFixture.snapshot(true, true, null);
        var r1 = new SnapshotDocumento.Rubro(
                id(6), id(20), "1.2", "otro", "", "u", decimal("1"), decimal("1"), decimal("1"));
        var r2 = new SnapshotDocumento.Rubro(
                id(5), id(21), "1.2", "otro", "", "u", decimal("1"), decimal("1"), decimal("1"));
        var c = new SnapshotDocumento.Capitulo(id(11), "1", "", (short) 1, s.total(), List.of(), List.of(r1, r2));
        assertEquals(
                List.of(id(21), id(20)),
                ApuDocumentoProyeccion.proyectar(copia(s, List.of(c), s.apus())).apus().stream()
                        .map(a -> a.id())
                        .toList());
    }

    @Test
    void identidadDuplicadaConContenidoDiferenteNoSeResuelvePorPosicion() {
        var s = ApuDocumentoFixture.snapshot(true, true, null);
        assertThrows(
                IllegalArgumentException.class,
                () -> ApuDocumentoProyeccion.proyectar(copia(
                        s,
                        s.capitulos(),
                        List.of(ApuDocumentoFixture.apu(20, null), ApuDocumentoFixture.apu(20, decimal("0.3"))))));
    }

    private static SnapshotDocumento copia(
            SnapshotDocumento s, List<SnapshotDocumento.Capitulo> cs, List<SnapshotDocumento.Apu> as) {
        return new SnapshotDocumento(
                s.presupuestoId(),
                s.version(),
                s.vigente(),
                s.notas(),
                s.total(),
                s.proyecto(),
                s.parametros(),
                s.display(),
                s.firmantes(),
                cs,
                as,
                s.preflight());
    }
}
