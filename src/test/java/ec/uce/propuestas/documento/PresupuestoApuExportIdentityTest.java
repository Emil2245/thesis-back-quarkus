package ec.uce.propuestas.documento;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ec.uce.propuestas.documento.exportacion.PreflightDocumentoService;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import java.util.List;
import org.junit.jupiter.api.Test;

class PresupuestoApuExportIdentityTest {
    @Test
    void sameCodeDoesNotMergeDifferentApusAndRepeatedIdentityIsUnique() {
        Rubro first = new Rubro();
        first.apuId = 10L;
        first.codigo = "SAME";
        Rubro second = new Rubro();
        second.apuId = 20L;
        second.codigo = "SAME";
        assertEquals(List.of(10L, 20L), PreflightDocumentoService.apuIdentidades(List.of(first, second, first)));
    }
}
