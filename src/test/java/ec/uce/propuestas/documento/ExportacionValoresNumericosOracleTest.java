package ec.uce.propuestas.documento;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ec.uce.propuestas.documento.exportacion.ApuDocumentoProyeccion;
import ec.uce.propuestas.documento.exportacion.OpcionesDocumento;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Oráculo "valores sin cambio" (plan 034): registra, en orden, cada celda numérica exacta (valor
 * OOXML) de los XLSX de APU y presupuesto. Los cambios de rotulado, estructura y firmas del plan
 * 034 solo agregan texto; la secuencia numérica debe permanecer idéntica.
 */
class ExportacionValoresNumericosOracleTest {
    static List<String> numericas(byte[] xlsx) throws Exception {
        var out = new ArrayList<String>();
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            for (var sheet : wb) {
                for (var row : sheet) {
                    for (var cell : row) {
                        if (cell.getCellType() == CellType.NUMERIC) {
                            out.add(((org.apache.poi.xssf.usermodel.XSSFCell) cell)
                                    .getCTCell()
                                    .getV());
                        }
                    }
                }
            }
        }
        return out;
    }

    private static byte[] apuXlsx(ec.uce.propuestas.documento.exportacion.SnapshotDocumento s) {
        return ApuXlsxWriter.renderizar(
                ApuDocumentoProyeccion.proyectar(s), new OpcionesDocumento("xlsx", Map.of("layout", "pestanas")));
    }

    @Test
    void apuFixtureHostilValoresPersistidos() throws Exception {
        var s = ApuDocumentoFixture.snapshot(true, true, null);
        assertEquals(GOLDEN_APU_HOSTIL, String.join(",", numericas(apuXlsx(s))));
    }

    @Test
    void apuConciliadoConHm() throws Exception {
        var s = ApuConciliadoFixture.snapshot(
                List.of(
                        ApuConciliadoFixture.apu(20, ApuConciliadoFixture.entradasConHm(), false),
                        ApuConciliadoFixture.apu(21, ApuConciliadoFixture.entradasConHm(), false)),
                true);
        assertEquals(GOLDEN_APU_CONCILIADO, String.join(",", numericas(apuXlsx(s))));
    }

    @Test
    void presupuestoValoresPersistidos() throws Exception {
        var s = ApuDocumentoFixture.snapshot(true, true, null);
        assertEquals(
                GOLDEN_PRESUPUESTO,
                String.join(
                        ",",
                        numericas(PresupuestoXlsxWriter.renderizar(
                                s, PresupuestoDocumentoFixture.opciones("xlsx", false)))));
    }

    static final String GOLDEN_APU_HOSTIL =
            "2.000001,9.000000001,10.000000002,0.000123456,12.000000003,2.000001,9.000000001,10.000000002,0.000123456,12.000000003,2.000001,9.000000001,10.000000002,0.000123456,12.000000003,11.123456789,0,2.000001,9.000000001,12.000000003,22.000000,0,11.123456789,0,22.000000,0,33.123456789,0.1800,4.987654321,123.123456789012345678901234567890,123.123456789012345678901234567890,2.000001,9.000000001,10.000000002,0.000123456,12.000000003,2.000001,9.000000001,10.000000002,0.000123456,12.000000003,2.000001,9.000000001,10.000000002,0.000123456,12.000000003,11.123456789,0,2.000001,9.000000001,12.000000003,22.000000,0,11.123456789,0,22.000000,0,33.123456789,0.1800,4.987654321,123.123456789012345678901234567890,123.123456789012345678901234567890";
    static final String GOLDEN_APU_CONCILIADO =
            "5.00,0.000000,0.289875,1,15.00,15.000000,0.33,4.950000,5.239875,1,4.75,4.750000,0.33,1.567500,2,4.23,8.460000,0.5,4.230000,5.797500,3.5,1.2345,4.320750,4.320750,0.5,2.00,1.000000,1.000000,5.239875,5.797500,4.320750,1.000000,16.358125,0.1800,2.944463,19.302588,19.302588,5.00,0.000000,0.289875,1,15.00,15.000000,0.33,4.950000,5.239875,1,4.75,4.750000,0.33,1.567500,2,4.23,8.460000,0.5,4.230000,5.797500,3.5,1.2345,4.320750,4.320750,0.5,2.00,1.000000,1.000000,5.239875,5.797500,4.320750,1.000000,16.358125,0.1800,2.944463,19.302588,19.302588";
    static final String GOLDEN_PRESUPUESTO =
            "4321.987654,4321.987654,1,1.23,1.23,1,1.23,1.23,1,1.23,1.23,1,1.23,1.23,4321.987654";
}
