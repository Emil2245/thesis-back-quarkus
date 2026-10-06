package ec.uce.propuestas.documento.exportacion;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.motor.ApuCalculado;
import ec.uce.propuestas.motor.FilaCalculada;
import ec.uce.propuestas.motor.SeccionTipo;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentoSnapshotTest {
    @Test
    void copiaRecursivaNoRetieneListasDelConstructor() {
        var rubros = new ArrayList<SnapshotDocumento.Rubro>();
        var hijos = new ArrayList<SnapshotDocumento.Capitulo>();
        var hijo = new SnapshotDocumento.Capitulo(null, "1.1", "Hijo", (short) 1, BigDecimal.ZERO, hijos, rubros);
        hijos.add(hijo);
        var raiz = new SnapshotDocumento.Capitulo(null, "1", "Raíz", (short) 1, BigDecimal.ZERO, hijos, rubros);
        hijos.clear();
        rubros.add(new SnapshotDocumento.Rubro(
                null, null, "1.1.1", "R", "R", "u", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE));
        assertEquals(1, raiz.hijos().size());
        assertTrue(raiz.rubros().isEmpty());
        assertTrue(hijo.hijos().isEmpty());
        assertTrue(hijo.rubros().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> raiz.hijos().clear());
        assertThrows(UnsupportedOperationException.class, () -> hijo.rubros().clear());
        var filas = new ArrayList<FilaCalculada>();
        filas.add(new FilaCalculada(
                SeccionTipo.MATERIAL,
                false,
                BigDecimal.ONE,
                null,
                new BigDecimal("1.000000000000000001"),
                null,
                new BigDecimal("1.000000000000000001")));
        var canonical = new ApuCalculado(
                "A",
                filas,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ONE);
        var secciones = new ArrayList<SnapshotDocumento.Seccion>();
        var apu = new SnapshotDocumento.Apu(
                null, "A", "A", "u", null, null, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE, canonical, secciones);
        filas.clear();
        secciones.add(new SnapshotDocumento.Seccion(SeccionTipo.MATERIAL, (short) 1, BigDecimal.ONE, List.of()));
        assertTrue(apu.secciones().isEmpty());
        assertEquals(
                new BigDecimal("1.000000000000000001"),
                apu.calculado().filas().getFirst().costoFila());
        assertThrows(
                UnsupportedOperationException.class,
                () -> apu.calculado().filas().clear());
    }
}
