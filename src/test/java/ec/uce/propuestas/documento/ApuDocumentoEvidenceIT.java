package ec.uce.propuestas.documento;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.documento.exportacion.*;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.io.ByteArrayInputStream;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import javax.sql.DataSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/** Isolated full Flyway seed evidence; never connects to the running application database. */
@QuarkusTest
@TestProfile(ApuDocumentoEvidenceIT.SeedProfile.class)
class ApuDocumentoEvidenceIT {
    public static final class SeedProfile implements io.quarkus.test.junit.QuarkusTestProfile {}

    @Inject
    DataSource ds;

    @Inject
    CapturaDocumentoService captura;

    @Inject
    ApuDescargaService descarga;

    @Inject
    jakarta.persistence.EntityManager em;

    @Inject
    jakarta.transaction.TransactionManager tm;

    private static final UUID ID = UUID.fromString("0192f6c4-7c8a-7abc-8000-000000001202");
    private static final Path DIRECTORY = Path.of("build/apu05-evidence");

    private static long heap() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    private static void compare(StringBuilder drift, String path, BigDecimal stored, BigDecimal canonical) {
        if (stored == null ? canonical != null : canonical == null || stored.compareTo(canonical) != 0)
            drift.append(path)
                    .append(" persisted=")
                    .append(stored)
                    .append(" canonical=")
                    .append(canonical)
                    .append('\n');
    }

    private static void artifact(String name, byte[] bytes, StringBuilder manifest, Set<UUID> identities)
            throws Exception {
        Files.write(DIRECTORY.resolve(name), bytes);
        manifest.append(name)
                .append(" bytes=")
                .append(bytes.length)
                .append(" sha256=")
                .append(HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        if (name.endsWith(".pdf")) {
            try (var pdf = Loader.loadPDF(bytes)) {
                manifest.append(" pages=").append(pdf.getNumberOfPages());
                var stripper = new PDFTextStripper();
                for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
                    stripper.setStartPage(i);
                    stripper.setEndPage(i);
                    String text = stripper.getText(pdf);
                    if (name.contains("apus"))
                        assertTrue(
                                identities.stream().anyMatch(id -> text.contains(id.toString())),
                                name + " identity page " + i);
                    else if (i == 1) assertTrue(text.contains(ID.toString()), name + " first-page budget identity");
                    var box = pdf.getPage(i - 1).getMediaBox();
                    assertTrue(box.getWidth() > 0 && box.getHeight() > 0, "Valid page boxes, not glyph bounds");
                }
            }
        } else if (name.endsWith(".xlsx")) {
            try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                manifest.append(" sheets=").append(workbook.getNumberOfSheets());
                assertTrue(workbook.getNumberOfSheets() > 0);
                assertTrue(workbook.getExternalLinksTable().isEmpty());
            }
        }
        manifest.append('\n');
    }

    private record NumberCell(int column, String value) {}

    private static void number(List<NumberCell> cells, int column, BigDecimal value) {
        if (value != null) cells.add(new NumberCell(column, value.toPlainString()));
    }

    // The oracle is captured persisted data, NOT a newly computed source financial history.
    private static List<NumberCell> expected(ApuDocumentoProyeccion.Analisis a) {
        var cells = new ArrayList<NumberCell>();
        for (var b : a.bloques()) {
            boolean hourly = b.tipo() == ec.uce.propuestas.motor.SeccionTipo.EQUIPO
                    || b.tipo() == ec.uce.propuestas.motor.SeccionTipo.MANO_OBRA;
            for (var row : b.filas()) {
                var d = row.detalle();
                number(cells, hourly ? 1 : 2, d.cantidad());
                number(cells, hourly ? 2 : 3, d.precioEfectivo());
                if (hourly) {
                    number(cells, 3, d.costoHora());
                    number(cells, 4, d.rendimiento());
                }
                number(cells, 5, d.costo());
            }
            if (b.mostrarSubtotal()) number(cells, 5, b.subtotal());
        }
        for (var s : a.pie().subtotales()) number(cells, 5, s.valor());
        number(cells, 5, a.pie().costoDirecto());
        number(cells, 4, a.pie().porcentajeIndirecto());
        number(cells, 5, a.pie().costoIndirecto());
        number(cells, 5, a.pie().costoTotal());
        number(cells, 5, a.pie().valorOfertado());
        return cells;
    }

    private static void xlsxParity(
            byte[] bytes, ApuDocumentoProyeccion.Documento d, boolean stacked, StringBuilder manifest)
            throws Exception {
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(stacked ? 1 : d.apus().size(), wb.getNumberOfSheets());
            var all = new ArrayList<NumberCell>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                var actual = new ArrayList<NumberCell>();
                for (var row : wb.getSheetAt(i))
                    for (var cell : row) {
                        assertNotEquals(org.apache.poi.ss.usermodel.CellType.FORMULA, cell.getCellType());
                        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC)
                            actual.add(new NumberCell(
                                    cell.getColumnIndex(),
                                    ((org.apache.poi.xssf.usermodel.XSSFCell) cell).getRawValue()));
                    }
                if (stacked) all.addAll(actual);
                else
                    assertEquals(
                            expected(d.apus().get(i)),
                            actual,
                            "UUID " + d.apus().get(i).id());
                manifest.append("xlsx mapping layout=")
                        .append(stacked ? "apilado" : "pestanas")
                        .append(" sheetIndex=")
                        .append(i)
                        .append(" sheetName=")
                        .append(wb.getSheetName(i))
                        .append(" UUIDs=")
                        .append(
                                stacked
                                        ? d.apus().stream()
                                                .map(ApuDocumentoProyeccion.Analisis::id)
                                                .toList()
                                        : List.of(d.apus().get(i).id()))
                        .append('\n');
            }
            if (stacked)
                assertEquals(
                        d.apus().stream().flatMap(a -> expected(a).stream()).toList(),
                        all,
                        "Ordered exact OOXML numeric values, columns and multiplicity");
        }
    }

    private static void pdfFooterParity(byte[] bytes, ApuDocumentoProyeccion.Documento document) throws Exception {
        try (var pdf = Loader.loadPDF(bytes)) {
            var textById = new LinkedHashMap<UUID, StringBuilder>();
            var stripper = new PDFTextStripper();
            for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(pdf);
                var id = document.apus().stream()
                        .map(ApuDocumentoProyeccion.Analisis::id)
                        .filter(uuid -> text.contains("APU " + uuid))
                        .findFirst()
                        .orElseThrow();
                textById.computeIfAbsent(id, ignored -> new StringBuilder()).append(text);
            }
            assertEquals(
                    document.apus().stream()
                            .map(ApuDocumentoProyeccion.Analisis::id)
                            .toList(),
                    new ArrayList<>(textById.keySet()));
            for (var a : document.apus()) {
                String text = textById.get(a.id()).toString();
                var values = List.of(
                        a.pie().costoDirecto(),
                        a.pie().costoIndirecto(),
                        a.pie().costoTotal(),
                        a.pie().valorOfertado());
                var labels = List.of("Costo directo", "Costo indirecto", "Costo total", "Valor ofertado");
                for (int i = 0; i < labels.size(); i++) {
                    String money = values.get(i)
                            .setScale(document.display().precision(), java.math.RoundingMode.HALF_UP)
                            .toPlainString();
                    String label = labels.get(i);
                    assertTrue(
                            text.lines().anyMatch(line -> line.startsWith(label) && line.endsWith(money)),
                            a.id() + " PDF labeled footer " + label + " " + money);
                }
            }
        }
    }

    private static void representative(ApuDocumentoProyeccion.Documento d, StringBuilder manifest) throws Exception {
        var comparator = Comparator.comparingInt((ApuDocumentoProyeccion.Analisis a) ->
                a.bloques().stream().mapToInt(b -> b.filas().size()).sum());
        var small = d.apus().stream().min(comparator).orElseThrow();
        var large = d.apus().stream().max(comparator).orElseThrow();
        var selected = small.id().equals(large.id()) ? List.of(small) : List.of(small, large);
        var slice = new ApuDocumentoProyeccion.Documento(
                d.presupuestoId(), d.version(), d.proyecto(), d.parametros(), d.display(), d.firmantes(), selected);
        manifest.append("Bounded visual scope ONLY representative immutable slice UUIDs=")
                .append(selected.stream()
                        .map(ApuDocumentoProyeccion.Analisis::id)
                        .toList())
                .append('\n');
        for (String layout : List.of("pestanas", "apilado")) {
            var bytes = ApuXlsxWriter.renderizar(slice, new OpcionesDocumento("xlsx", Map.of("layout", layout)));
            xlsxParity(bytes, slice, layout.equals("apilado"), manifest);
            artifact("representative-" + layout + ".xlsx", bytes, manifest, Set.of());
        }
        artifact(
                "representative-apus.pdf",
                ApuPdfWriter.renderizar(slice, new OpcionesDocumento("pdf", Map.of())),
                manifest,
                selected.stream()
                        .map(ApuDocumentoProyeccion.Analisis::id)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    private static void extremes(StringBuilder manifest) throws Exception {
        var base = ApuDocumentoProyeccion.proyectar(ApuDocumentoFixture.snapshot(true, true, new BigDecimal("0.18")));
        String longText = "Descripción institucional ñ á Ω ".repeat(150) + " LONGENDING";
        var analyses = new ArrayList<ApuDocumentoProyeccion.Analisis>();
        for (int n = 0; n < 3; n++) {
            var a = base.apus().getFirst();
            var source = a.bloques().stream()
                    .filter(b -> !b.filas().isEmpty())
                    .findFirst()
                    .orElseThrow();
            var original = source.filas().getFirst().detalle();
            var rows = new ArrayList<ApuDocumentoProyeccion.Fila>();
            for (int i = 0; i < 125; i++) {
                var detail = new SnapshotDocumento.Detalle(
                        UUID.nameUUIDFromBytes(
                                ("extreme-" + n + "-" + i).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                        (short) i,
                        false,
                        "Fila ñ " + i + (i == 124 ? " ROWENDING" : ""),
                        "m²",
                        original.cantidad(),
                        original.rendimiento(),
                        original.tarifaJornal(),
                        original.precioUnitarioTarifa(),
                        new BigDecimal("-9.000001"),
                        new BigDecimal("-10.000002"),
                        new BigDecimal("-12.000003"),
                        null);
                rows.add(new ApuDocumentoProyeccion.Fila(detail, detail.descripcion(), null));
            }
            var block = new ApuDocumentoProyeccion.Bloque(
                    source.tipo(),
                    source.etiqueta(),
                    source.ordenCapturado(),
                    rows,
                    new BigDecimal("-1500.000375"),
                    true);
            var pie = new ApuDocumentoProyeccion.Pie(
                    List.of(),
                    new BigDecimal("0.18"),
                    new BigDecimal("-1500.000375"),
                    new BigDecimal("-270.000068"),
                    new BigDecimal("-1770.000443"),
                    new BigDecimal("-1770.000443"),
                    longText + " FOOTERENDING");
            analyses.add(new ApuDocumentoProyeccion.Analisis(
                    UUID.nameUUIDFromBytes(("extreme-apu-" + n).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    longText + n + " CODEENDING",
                    longText + n + " CODEENDING",
                    longText,
                    longText + " UNITENDING",
                    "",
                    longText,
                    List.of(block),
                    pie,
                    a.diagnostico()));
        }
        var p = base.proyecto();
        var project = new SnapshotDocumento.Proyecto(
                p.id(),
                longText,
                longText,
                longText,
                p.anio(),
                p.fechaInicio(),
                p.plazoEjecucion(),
                p.plazoUnidad(),
                p.estado(),
                longText + " INSTITUTIONENDING",
                longText);
        var signers = List.of(new SnapshotDocumento.Firmante(
                UUID.nameUUIDFromBytes("signer".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                longText + " SIGNERENDING",
                longText,
                "TECNICO",
                (short) 1));
        manifest.append(
                "Synthetic extremes ONLY document stress, no canonical financial parity: 3 APUs, 125 rows each, signed negatives, precisions 0/2. Images NOT inspected.\n");
        for (int precision : List.of(0, 2)) {
            var document = new ApuDocumentoProyeccion.Documento(
                    base.presupuestoId(),
                    base.version(),
                    project,
                    base.parametros(),
                    new SnapshotDocumento.Display(precision, 4),
                    signers,
                    analyses);
            for (String layout : List.of("pestanas", "apilado")) {
                var bytes = ApuXlsxWriter.renderizar(document, new OpcionesDocumento("xlsx", Map.of("layout", layout)));
                xlsxParity(bytes, document, layout.equals("apilado"), manifest);
                artifact("extreme-p" + precision + "-" + layout + ".xlsx", bytes, manifest, Set.of());
            }
            artifact(
                    "extreme-p" + precision + "-apus.pdf",
                    ApuPdfWriter.renderizar(document, new OpcionesDocumento("pdf", Map.of())),
                    manifest,
                    analyses.stream()
                            .map(ApuDocumentoProyeccion.Analisis::id)
                            .collect(java.util.stream.Collectors.toSet()));
        }
    }

    @Test
    void fullSeedCapturePureOutputsAndServiceMeasurements() throws Exception {
        Files.createDirectories(DIRECTORY);
        long owner;
        int count;
        try (var connection = ds.getConnection();
                var query = connection.prepareStatement(
                        "select pr.usuario_id,(select count(*) from rubro r join capitulo ca on ca.id=r.capitulo_id where ca.presupuesto_id=p.id) from presupuesto p join proyecto pr on pr.id=p.proyecto_id where p.public_id=? and pr.nombre_proyecto='Cetro Médico Tulcán'")) {
            query.setObject(1, ID);
            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                owner = result.getLong(1);
                count = result.getInt(2);
                assertFalse(result.next());
            }
        }
        var statistics = em.getEntityManagerFactory()
                .unwrap(org.hibernate.SessionFactory.class)
                .getStatistics();
        boolean enabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        var manifest = new StringBuilder(
                "APU05 evidence harness only. No visual acceptance. Times are observations, not thresholds. Heap samples and lifetime JMX peaks are NOT task allocations. Lock duration unmeasured. Budget identity only first page (observed writer contract); APU identity every page. 1/32 SQL baselines pending. References not financially adopted.\n");
        var drift = new StringBuilder();
        try {
            long before = statistics.getPrepareStatementCount();
            long start = System.nanoTime();
            long heapBefore = heap();
            var snapshot = captura.capturar(ID, owner, "apus", new OpcionesDocumento("pdf", Map.of()));
            long elapsed = System.nanoTime() - start;
            long queries = statistics.getPrepareStatementCount() - before;
            assertEquals(jakarta.transaction.Status.STATUS_NO_TRANSACTION, tm.getStatus());
            assertTrue(snapshot.preflight().exportable(), snapshot.preflight().toString());
            Files.writeString(DIRECTORY.resolve("seed-snapshot.txt"), snapshot.toString());
            var document = ApuDocumentoProyeccion.proyectar(snapshot);
            for (var analysis : document.apus()) {
                var stored = snapshot.apus().stream()
                        .filter(a -> a.id().equals(analysis.id()))
                        .findFirst()
                        .orElseThrow();
                assertEquals(stored.costoDirecto(), analysis.pie().costoDirecto());
                assertEquals(stored.costoIndirecto(), analysis.pie().costoIndirecto());
                assertEquals(stored.costoTotal(), analysis.pie().costoTotal());
                assertEquals(stored.costoTotal(), analysis.pie().valorOfertado());
                for (var block : analysis.bloques()) {
                    var section = stored.secciones().stream()
                            .filter(s -> s.tipo() == block.tipo())
                            .findFirst();
                    assertEquals(
                            section.map(SnapshotDocumento.Seccion::subtotal).orElse(BigDecimal.ZERO), block.subtotal());
                    for (var row : block.filas())
                        assertEquals(
                                section.orElseThrow().detalles().stream()
                                        .filter(v -> v.id().equals(row.detalle().id()))
                                        .findFirst()
                                        .orElseThrow(),
                                row.detalle());
                }
            }
            var rows = new ArrayList<SnapshotDocumento.Rubro>();
            PresupuestoDocumentoEvidenceIT.rubros(snapshot.capitulos(), rows);
            manifest.append("uuid=")
                    .append(ID)
                    .append(" precision=")
                    .append(snapshot.display())
                    .append(" persistedRubros=")
                    .append(count)
                    .append(" capturedRubros=")
                    .append(rows.size())
                    .append(" uniqueAPUs=")
                    .append(document.apus().size())
                    .append(" captureQueries=")
                    .append(queries)
                    .append(" captureElapsedNs=")
                    .append(elapsed)
                    .append(" heapBefore=")
                    .append(heapBefore)
                    .append(" heapAfter=")
                    .append(heap())
                    .append('\n');
            int rounded6Matches = 0;
            int rounded6Differences = 0;
            int displayDifferences = 0;
            var displayApuIds = new LinkedHashSet<UUID>();
            BigDecimal maxCtDifference = BigDecimal.ZERO;
            for (var apu : snapshot.apus()) {
                var storedValues = List.of(apu.costoDirecto(), apu.costoIndirecto(), apu.costoTotal());
                var computedValues = List.of(
                        apu.calculado().costoDirecto(),
                        apu.calculado().costoIndirecto(),
                        apu.calculado().costoTotal());
                for (int i = 0; i < 3; i++)
                    if (storedValues.get(i).compareTo(computedValues.get(i)) != 0) {
                        if (storedValues
                                        .get(i)
                                        .compareTo(computedValues.get(i).setScale(6, java.math.RoundingMode.HALF_UP))
                                == 0) rounded6Matches++;
                        else rounded6Differences++;
                        if (storedValues
                                        .get(i)
                                        .setScale(2, java.math.RoundingMode.HALF_UP)
                                        .compareTo(computedValues.get(i).setScale(2, java.math.RoundingMode.HALF_UP))
                                != 0) {
                            displayDifferences++;
                            displayApuIds.add(apu.id());
                        }
                    }
                maxCtDifference = maxCtDifference.max(
                        apu.costoTotal().subtract(apu.calculado().costoTotal()).abs());
                String path = "apu[" + apu.id() + "]";
                compare(
                        drift,
                        path + ".costoDirecto",
                        apu.costoDirecto(),
                        apu.calculado().costoDirecto());
                compare(
                        drift,
                        path + ".costoIndirecto",
                        apu.costoIndirecto(),
                        apu.calculado().costoIndirecto());
                compare(
                        drift,
                        path + ".costoTotal",
                        apu.costoTotal(),
                        apu.calculado().costoTotal());
                manifest.append(path)
                        .append(" storedCT=")
                        .append(apu.costoTotal())
                        .append(" canonicalCT=")
                        .append(apu.calculado().costoTotal())
                        .append(" CIoverride=")
                        .append(apu.porcentajeIndirecto())
                        .append('\n');
                for (var section : apu.secciones())
                    for (var detail : section.detalles())
                        manifest.append(path)
                                .append(".detalle[")
                                .append(detail.id())
                                .append("] ")
                                .append(detail)
                                .append('\n');
            }
            manifest.append("rawDriftRounded6Matches=")
                    .append(rounded6Matches)
                    .append(" rawDriftRounded6Differences=")
                    .append(rounded6Differences)
                    .append(" p2DifferentFields=")
                    .append(displayDifferences)
                    .append(" p2DifferentAPUs=")
                    .append(displayApuIds.size())
                    .append(" p2DifferentUUIDs=")
                    .append(displayApuIds)
                    .append(" maxAbsCTDifference=")
                    .append(maxCtDifference)
                    .append('\n');
            for (var row : rows) {
                var apu = snapshot.apus().stream()
                        .filter(a -> a.id().equals(row.apuId()))
                        .findFirst()
                        .orElseThrow();
                manifest.append("boundary rubro=")
                        .append(row.id())
                        .append(" PU=")
                        .append(row.precioUnitario())
                        .append(" APUCT=")
                        .append(apu.costoTotal())
                        .append(" difference=")
                        .append(apu.costoTotal().subtract(row.precioUnitario()))
                        .append('\n');
            }
            for (String name : List.of("seed-pestanas.xlsx", "seed-apilado.xlsx", "seed-apus.pdf")) {
                var options = name.endsWith("pdf")
                        ? new OpcionesDocumento("pdf", Map.of())
                        : new OpcionesDocumento(
                                "xlsx", Map.of("layout", name.contains("apilado") ? "apilado" : "pestanas"));
                before = statistics.getPrepareStatementCount();
                start = System.nanoTime();
                byte[] bytes = name.endsWith("pdf")
                        ? ApuPdfWriter.renderizar(document, options)
                        : ApuXlsxWriter.renderizar(document, options);
                manifest.append(name)
                        .append(" pureElapsedNs=")
                        .append(System.nanoTime() - start)
                        .append(" pureQueries=")
                        .append(statistics.getPrepareStatementCount() - before)
                        .append(" heapSample=")
                        .append(heap())
                        .append('\n');
                assertEquals(0, statistics.getPrepareStatementCount() - before);
                artifact(
                        name,
                        bytes,
                        manifest,
                        document.apus().stream()
                                .map(ApuDocumentoProyeccion.Analisis::id)
                                .collect(java.util.stream.Collectors.toSet()));
                if (!name.endsWith("pdf")) xlsxParity(bytes, document, name.contains("apilado"), manifest);
                else pdfFooterParity(bytes, document);
                before = statistics.getPrepareStatementCount();
                start = System.nanoTime();
                var output = descarga.generar(ID, owner, options);
                manifest.append(name)
                        .append(" serviceElapsedNs=")
                        .append(System.nanoTime() - start)
                        .append(" serviceQueries=")
                        .append(statistics.getPrepareStatementCount() - before)
                        .append('\n');
                assertNotNull(output.archivo(), output.preflight().toString());
                assertEquals(16, statistics.getPrepareStatementCount() - before);
                assertEquals(jakarta.transaction.Status.STATUS_NO_TRANSACTION, tm.getStatus());
            }
            for (String name : List.of("seed-budget.xlsx", "seed-budget-portrait.pdf", "seed-budget-landscape.pdf")) {
                var options = PresupuestoDocumentoFixture.opciones(
                        name.endsWith("pdf") ? "pdf" : "xlsx", name.contains("landscape"));
                before = statistics.getPrepareStatementCount();
                byte[] bytes = name.endsWith("pdf")
                        ? PresupuestoPdfWriter.renderizar(snapshot, options)
                        : PresupuestoXlsxWriter.renderizar(snapshot, options);
                assertEquals(0, statistics.getPrepareStatementCount() - before);
                artifact(name, bytes, manifest, Set.of(ID));
            }
            assertEquals(count, rows.size());
            assertEquals(16, queries);
            Files.writeString(DIRECTORY.resolve("parity-diagnostics.txt"), drift.toString());
            manifest.append("canonicalTotalDriftCount=")
                    .append(drift.toString().lines().count())
                    .append('\n');
            // V014 intentionally preserved source APU totals without recalculation. Raw drift is
            // retained verbatim above; it is NOT an export failure and NOT global financial parity.
            manifest.append(
                    "Financial history: V014 source-preserved; raw canonical differences are diagnostics, never normalized away. Fresh calculation-produced NUMERIC(14,6) parity remains pending.\n");
            representative(document, manifest);
            extremes(manifest);
        } finally {
            for (var pool : ManagementFactory.getMemoryPoolMXBeans())
                if (pool.getPeakUsage() != null)
                    manifest.append("JMX lifetime peak NOT task allocation: ")
                            .append(pool.getName())
                            .append(" used=")
                            .append(pool.getPeakUsage().getUsed())
                            .append('\n');
            Files.writeString(DIRECTORY.resolve("manifest.txt"), manifest);
            statistics.setStatisticsEnabled(enabled);
        }
    }
}
