package ec.uce.propuestas.documento;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.documento.exportacion.ApuDocumentoProyeccion;
import ec.uce.propuestas.documento.exportacion.OpcionesDocumento;
import ec.uce.propuestas.documento.exportacion.SnapshotDocumento;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** CHK-26/27 (plan 034): identidad del proyecto y bloque de firmantes en cada APU. */
class ApuIdentidadFirmasTest {
    private static ApuDocumentoProyeccion.Documento documento() {
        var base = ApuConciliadoFixture.snapshot(
                List.of(
                        ApuConciliadoFixture.apu(20, ApuConciliadoFixture.entradasConHm(), false),
                        ApuConciliadoFixture.apu(21, ApuConciliadoFixture.entradasSinHm(), false)),
                true);
        var firmantes = List.of(
                new SnapshotDocumento.Firmante(
                        PresupuestoDocumentoFixture.id(51), "Ana Dos", "Revisora", "APROBADO", (short) 2),
                new SnapshotDocumento.Firmante(
                        PresupuestoDocumentoFixture.id(52), "Beto Uno", "Aprobador", "APROBADO", (short) 1),
                new SnapshotDocumento.Firmante(
                        PresupuestoDocumentoFixture.id(53), "Carla", "Responsable", "CONSOLIDADO", (short) 1));
        var s = new SnapshotDocumento(
                base.presupuestoId(),
                base.version(),
                base.vigente(),
                base.notas(),
                base.total(),
                base.proyecto(),
                base.parametros(),
                base.display(),
                firmantes,
                base.capitulos(),
                base.apus(),
                base.preflight());
        return ApuDocumentoProyeccion.proyectar(s);
    }

    private static final List<String> FIRMAS = List.of(
            "CONSOLIDADO POR:",
            "Carla\nResponsable",
            "FIRMA:",
            "APROBADO POR:",
            "Beto Uno\nAprobador",
            "FIRMA:",
            "Ana Dos\nRevisora",
            "FIRMA:");

    @Test
    void xlsxCadaHojaTieneIdentidadYFirmasEnOrden() throws Exception {
        var d = documento();
        byte[] bytes = ApuXlsxWriter.renderizar(d, new OpcionesDocumento("xlsx", Map.of("layout", "pestanas")));
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(2, wb.getNumberOfSheets());
            for (var sheet : wb) {
                var col = new ArrayList<String>();
                for (var row : sheet)
                    if (row.getCell(0) != null && row.getCell(0).getCellType() == CellType.STRING)
                        col.add(row.getCell(0).getStringCellValue());
                // Las filas de identificación técnica se repiten al paginar; no forman parte del bloque.
                col.removeIf(x -> x.startsWith("APU UUID: ")
                        || x.startsWith("APU código: ")
                        || x.startsWith("APU descripción: "));
                assertTrue(col.contains("PROYECTO: " + d.proyecto().nombre()), col.toString());
                assertTrue(col.contains("Año: " + d.proyecto().anio()));
                assertTrue(col.contains(d.proyecto().direccionInstitucional()));
                int desde = col.indexOf("CONSOLIDADO POR:");
                assertTrue(desde > col.indexOf("Este precio no incluye IVA"));
                assertEquals(FIRMAS, col.subList(desde, col.size()));
            }
        }
    }

    @Test
    void pdfCadaApuTieneIdentidadYFirmasEnOrden() throws Exception {
        var d = documento();
        try (var pdf = Loader.loadPDF(ApuPdfWriter.renderizar(d, new OpcionesDocumento("pdf", Map.of())))) {
            String text = new PDFTextStripper().getText(pdf);
            assertEquals(2, count(text, "PROYECTO: " + d.proyecto().nombre()));
            assertEquals(2, count(text, "Año: " + d.proyecto().anio()));
            assertEquals(2, count(text, "CONSOLIDADO POR:"));
            assertEquals(2, count(text, "APROBADO POR:"));
            assertEquals(6, count(text, "FIRMA:"));
            assertTrue(text.indexOf("CONSOLIDADO POR:") < text.indexOf("Carla"));
            assertTrue(text.indexOf("Carla") < text.indexOf("APROBADO POR:"));
            assertTrue(text.indexOf("Beto Uno") < text.indexOf("Ana Dos"));
        }
    }

    private static int count(String text, String what) {
        int n = 0;
        for (int i = text.indexOf(what); i >= 0; i = text.indexOf(what, i + 1)) n++;
        return n;
    }
}
