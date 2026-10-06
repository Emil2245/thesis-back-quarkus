package ec.uce.propuestas.documento;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.documento.exportacion.CapturaDocumentoService;
import ec.uce.propuestas.documento.exportacion.SnapshotDocumento;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(PresupuestoDocumentoEvidenceIT.SeedProfile.class)
class PresupuestoDocumentoEvidenceIT {
    // Same empty, independent profile strategy as DocumentoSnapshotIT.SnapshotProfile;
    // that enclosing test is package-private in exportacion and cannot be referenced here.
    public static final class SeedProfile implements io.quarkus.test.junit.QuarkusTestProfile {}

    @Inject
    DataSource ds;

    @Inject
    CapturaDocumentoService captura;

    @Inject
    PresupuestoDescargaService descarga;

    @Inject
    jakarta.persistence.EntityManager em;

    @Inject
    jakarta.transaction.TransactionManager tm;

    static void rubros(List<SnapshotDocumento.Capitulo> chapters, List<SnapshotDocumento.Rubro> rows) {
        for (var chapter : chapters) {
            rubros(chapter.hijos(), rows);
            rows.addAll(chapter.rubros());
        }
    }

    static long heap() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    @Test
    void capturaSeedYGeneraDescargasRealesFueraDeTransaccion() throws Exception {
        var directory = Path.of("build/bud03-evidence");
        Files.createDirectories(directory);
        UUID id;
        long caller;
        int persistedCount;
        try (var c = ds.getConnection();
                var p = c.prepareStatement(
                        "select p.public_id,pr.usuario_id,(select count(*) from rubro r join capitulo ca on ca.id=r.capitulo_id where ca.presupuesto_id=p.id) from presupuesto p join proyecto pr on pr.id=p.proyecto_id where pr.nombre_proyecto='Cetro Médico Tulcán' and p.version=1")) {
            try (var r = p.executeQuery()) {
                assertTrue(r.next());
                id = r.getObject(1, UUID.class);
                caller = r.getLong(2);
                persistedCount = r.getInt(3);
                assertFalse(r.next());
            }
        }
        var stats = em.getEntityManagerFactory()
                .unwrap(org.hibernate.SessionFactory.class)
                .getStatistics();
        stats.setStatisticsEnabled(true);
        var manifest = new StringBuilder(
                "BUD03 verification only: elapsed times are observations, not latency budgets; heap samples and JMX pool peaks are not precise allocation measurements. No lock-duration measurement. No visual acceptance.\n");
        try {
            long beforeHeap = heap();
            long before = stats.getPrepareStatementCount();
            long start = System.nanoTime();
            var snapshot =
                    captura.capturar(id, caller, "presupuesto", PresupuestoDocumentoFixture.opciones("xlsx", false));
            long captureMs = (System.nanoTime() - start) / 1_000_000;
            long captureQueries = stats.getPrepareStatementCount() - before;
            long afterHeap = heap();
            assertEquals(jakarta.transaction.Status.STATUS_NO_TRANSACTION, tm.getStatus());
            Files.writeString(directory.resolve("seed-snapshot.txt"), snapshot.toString());
            var rows = new ArrayList<SnapshotDocumento.Rubro>();
            rubros(snapshot.capitulos(), rows);
            try (var c = ds.getConnection();
                    var p = c.prepareStatement(
                            "select r.public_id,r.precio_unitario from rubro r join capitulo ca on ca.id=r.capitulo_id join presupuesto p on p.id=ca.presupuesto_id where p.public_id=?")) {
                p.setObject(1, id);
                int matched = 0;
                try (var r = p.executeQuery()) {
                    while (r.next()) {
                        UUID rubroId = r.getObject(1, UUID.class);
                        var captured = rows.stream()
                                .filter(row -> row.id().equals(rubroId))
                                .findFirst()
                                .orElseThrow();
                        assertEquals(
                                r.getBigDecimal(2), captured.precioUnitario(), "Persisted PU and scale " + rubroId);
                        matched++;
                    }
                }
                assertEquals(rows.size(), matched);
            }
            manifest.append("uuid=")
                    .append(id)
                    .append(" version=")
                    .append(snapshot.version())
                    .append(" precision=")
                    .append(snapshot.display())
                    .append(" persistedSeedCount=")
                    .append(persistedCount)
                    .append(" capturedRubros=")
                    .append(rows.size())
                    .append(" captureQueries=")
                    .append(captureQueries)
                    .append(" captureElapsedMs=")
                    .append(captureMs)
                    .append(" heapBefore=")
                    .append(beforeHeap)
                    .append(" heapAfter=")
                    .append(afterHeap)
                    .append('\n');
            for (var row : rows)
                manifest.append("rubro=")
                        .append(row.id())
                        .append(" code=")
                        .append(row.codigo())
                        .append(" persistedPU=")
                        .append(row.precioUnitario().toPlainString())
                        .append(" scale=")
                        .append(row.precioUnitario().scale())
                        .append('\n');
            for (String name : List.of("seed.xlsx", "seed-portrait.pdf", "seed-landscape.pdf")) {
                boolean pdf = name.endsWith("pdf");
                var options = PresupuestoDocumentoFixture.opciones(pdf ? "pdf" : "xlsx", name.contains("landscape"));
                before = stats.getPrepareStatementCount();
                start = System.nanoTime();
                byte[] pure = pdf
                        ? PresupuestoPdfWriter.renderizar(snapshot, options)
                        : PresupuestoXlsxWriter.renderizar(snapshot, options);
                long renderMs = (System.nanoTime() - start) / 1_000_000;
                long renderQueries = stats.getPrepareStatementCount() - before;
                before = stats.getPrepareStatementCount();
                start = System.nanoTime();
                var result = descarga.generar(id, caller, options);
                long serviceMs = (System.nanoTime() - start) / 1_000_000;
                long serviceQueries = stats.getPrepareStatementCount() - before;
                assertNotNull(result.archivo(), result.preflight().toString());
                byte[] bytes = result.archivo().bytes();
                Files.write(directory.resolve(name), bytes);
                manifest.append(name)
                        .append(" pureRenderElapsedMs=")
                        .append(renderMs)
                        .append(" pureRenderQueries=")
                        .append(renderQueries)
                        .append(" serviceCaptureAndRenderElapsedMs=")
                        .append(serviceMs)
                        .append(" serviceQueries=")
                        .append(serviceQueries)
                        .append(" outputBytes=")
                        .append(bytes.length)
                        .append(" pureBytes=")
                        .append(pure.length)
                        .append(" heapSample=")
                        .append(heap())
                        .append('\n');
                Files.writeString(directory.resolve("manifest.txt"), manifest);
                assertEquals(0, renderQueries, "Pure render after capture transaction ends");
                assertEquals(
                        captureQueries,
                        serviceQueries,
                        "Actual service adds no SQL beyond capture; not a direct service render hook");
                assertEquals(jakarta.transaction.Status.STATUS_NO_TRANSACTION, tm.getStatus());
                if (pdf) {
                    try (var document = Loader.loadPDF(bytes)) {
                        String text = new PDFTextStripper().getText(document);
                        manifest.append(name)
                                .append(" pages=")
                                .append(document.getNumberOfPages())
                                .append('\n');
                        assertTrue(text.contains(id.toString()));
                        var numericCounts = new java.util.HashMap<String, Integer>();
                        var formatter = new org.apache.poi.ss.usermodel.DataFormatter(java.util.Locale.US);
                        try (var workbook = new XSSFWorkbook(
                                new ByteArrayInputStream(Files.readAllBytes(directory.resolve("seed.xlsx"))))) {
                            for (var row : workbook.getSheetAt(0))
                                for (var cell : row) {
                                    if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC)
                                        numericCounts.merge(formatter.formatCellValue(cell), 1, Integer::sum);
                                }
                        }
                        var pdfCounts = new java.util.HashMap<String, Integer>();
                        for (var page : document.getPages()) {
                            var area = new org.apache.pdfbox.text.PDFTextStripperByArea();
                            float width = page.getMediaBox().getWidth();
                            float numericLeft = 24 + (width - 48) * 5.7f / 9;
                            area.addRegion(
                                    "numbers",
                                    new java.awt.geom.Rectangle2D.Float(
                                            numericLeft,
                                            0,
                                            width - numericLeft,
                                            page.getMediaBox().getHeight()));
                            area.extractRegions(page);
                            var matcher = java.util.regex.Pattern.compile("(?<![\\w.])-?\\d+\\.\\d{2}(?![\\w.])")
                                    .matcher(area.getTextForRegion("numbers"));
                            while (matcher.find()) pdfCounts.merge(matcher.group(), 1, Integer::sum);
                        }
                        assertEquals(
                                numericCounts,
                                pdfCounts,
                                "Numeric value multiplicity PDF/XLSX (not a visual column-alignment check)");
                        for (var row : rows) assertTrue(text.contains(row.codigo()), "Missing code " + row.codigo());
                        for (var firmante : snapshot.firmantes()) assertTrue(text.contains(firmante.nombre()));
                    }
                } else {
                    try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                        var found = new java.util.HashMap<String, Integer>();
                        for (var row : wb.getSheetAt(0)) {
                            var code = row.getCell(1);
                            if (code == null || code.getCellType() != org.apache.poi.ss.usermodel.CellType.STRING)
                                continue;
                            String value = code.getStringCellValue();
                            String item = row.getCell(0).getStringCellValue();
                            var matching = rows.stream()
                                    .filter(r ->
                                            r.codigo().equals(value) && r.item().equals(item))
                                    .toList();
                            if (matching.isEmpty()) continue;
                            found.merge(item + "|" + value, 1, Integer::sum);
                            var expected = matching.getFirst();
                            for (int col = 4; col <= 6; col++) {
                                var number =
                                        switch (col) {
                                            case 4 -> expected.cantidad();
                                            case 5 -> expected.precioUnitario();
                                            default -> expected.precioTotal();
                                        };
                                var cell = (org.apache.poi.xssf.usermodel.XSSFCell) row.getCell(col);
                                assertEquals(org.apache.poi.ss.usermodel.CellType.NUMERIC, cell.getCellType());
                                assertEquals(number.toPlainString(), cell.getRawValue(), value + " column " + col);
                            }
                        }
                        var expectedCounts = new java.util.HashMap<String, Integer>();
                        for (var row : rows) expectedCounts.merge(row.item() + "|" + row.codigo(), 1, Integer::sum);
                        assertEquals(expectedCounts, found, "Rowwise code multiplicity, not substring parity");
                    }
                }
            }
            assertEquals(298, persistedCount);
            assertEquals(persistedCount, rows.size());
            assertEquals(16, captureQueries);
        } finally {
            for (var pool : ManagementFactory.getMemoryPoolMXBeans()) {
                var peak = pool.getPeakUsage();
                if (peak != null)
                    manifest.append("JMX pool lifetime peak (not reset, not task allocation): ")
                            .append(pool.getName())
                            .append(" used=")
                            .append(peak.getUsed())
                            .append('\n');
            }
            Files.writeString(directory.resolve("manifest.txt"), manifest);
            stats.setStatisticsEnabled(false);
        }
    }
}
