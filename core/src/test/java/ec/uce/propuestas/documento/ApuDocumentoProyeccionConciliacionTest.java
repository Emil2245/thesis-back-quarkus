package ec.uce.propuestas.documento;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.documento.exportacion.ApuDocumentoProyeccion;
import ec.uce.propuestas.motor.SeccionTipo;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CHK-05/06/07 (plan 034): filas impresas coherentes con el subtotal y una fila HM por APU.
 *
 * <p>Restaurado en la etapa 05 desde el retirado {@code ApuDocumentoConciliacionTest}: la
 * conciliación vive en Core ({@link ApuDocumentoProyeccion}) y viaja a Documentos ya resuelta.
 * El caso XLSX de aquel test pertenece ahora a los renderers de Documentos.
 */
class ApuDocumentoProyeccionConciliacionTest {
    private static ApuDocumentoProyeccion.Analisis unico(List<ApuConciliadoFixture.Entrada> entradas, boolean vencido) {
        var s = ApuConciliadoFixture.snapshot(List.of(ApuConciliadoFixture.apu(20, entradas, vencido)), true);
        return ApuDocumentoProyeccion.proyectar(s).apus().getFirst();
    }

    private static BigDecimal sumaFilas(ApuDocumentoProyeccion.Bloque b) {
        return b.filas().stream().map(ApuDocumentoProyeccion.Fila::costo).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void filasObsoletasSeImprimenConLosImportesDelMotorYSumanElSubtotal() {
        var a = unico(ApuConciliadoFixture.entradasConHm(), true);
        BigDecimal cd = BigDecimal.ZERO;
        for (var b : a.bloques()) {
            assertEquals(0, sumaFilas(b).compareTo(b.subtotal()), b.etiqueta());
            cd = cd.add(b.subtotal());
        }
        assertEquals(0, cd.compareTo(a.pie().costoDirecto()));
        // El importe persistido obsoleto (costo + 1.5) nunca se imprime.
        var m = a.bloques().getFirst();
        var equipo = m.filas().stream()
                .filter(f -> f.descripcion().equals("Equipo certificador"))
                .findFirst()
                .orElseThrow();
        assertEquals(0, equipo.costo().compareTo(new BigDecimal("4.95")));
        assertEquals(0, equipo.costoHora().compareTo(new BigDecimal("15")));
        assertEquals(0, equipo.detalle().costo().subtract(equipo.costo()).compareTo(new BigDecimal("1.5")));
    }

    @Test
    void apuSinFilaHmNoImprimeFilaHmSintetica() {
        var a = unico(ApuConciliadoFixture.entradasSinHm(), true);
        var m = a.bloques().getFirst();
        assertEquals(SeccionTipo.EQUIPO, m.tipo());
        assertTrue(m.filas().stream().noneMatch(f -> f.detalle().herramientaMenor()));
        assertEquals(1, m.filas().size());
        assertEquals(0, sumaFilas(m).compareTo(m.subtotal()));
    }

    @Test
    void apuConFilaHmPersistidaNoSintetizaOtra() {
        var a = unico(ApuConciliadoFixture.entradasConHm(), false);
        long hm = a.bloques().getFirst().filas().stream()
                .filter(f -> f.detalle().herramientaMenor())
                .count();
        assertEquals(1, hm);
        var fila = a.bloques().getFirst().filas().getFirst();
        // %HM × subtotal N, a escala 6: 0.05 × (1.5675 + 4.23)
        assertEquals(0, fila.costo().compareTo(new BigDecimal("0.289875")));
    }

    @Test
    void instantaneaNoConciliableConservaLosImportesPersistidos() {
        var s = ApuDocumentoFixture.snapshot(true, true, null);
        var d = ApuDocumentoProyeccion.proyectar(s);
        for (var a : d.apus())
            for (var b : a.bloques())
                for (var f : b.filas()) {
                    assertEquals(f.detalle().costo(), f.costo());
                    assertEquals(f.detalle().costoHora(), f.costoHora());
                }
        // Sin conciliación no se fabrica una fila HM nueva.
        assertEquals(3, d.apus().getFirst().bloques().getFirst().filas().size());
    }
}
