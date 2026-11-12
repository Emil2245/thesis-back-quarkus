package ec.uce.propuestas.documento;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.documento.exportacion.*;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.sql.DataSource;
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

    private record NumberCell(int column, String value) {}

    private static void number(List<NumberCell> cells, int column, BigDecimal value) {
        if (value != null) cells.add(new NumberCell(column, value.toPlainString()));
    }

    // The oracle is the projected printed value: persisted data, except per-row costo/costoHora that come from the
    // captured engine result when the detail matches it (plan 034). NOT a newly computed source financial history.
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
                    number(cells, 3, row.costoHora());
                    number(cells, 4, d.rendimiento());
                }
                number(cells, 5, row.costo());
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

    private static List<NumberCell> expected(DocumentProjectionV1.ApuView a) {
        var cells = new ArrayList<NumberCell>();
        for (var b : a.bloques()) {
            boolean hourly = b.tipo() == DocumentProjectionV1.SeccionTipo.EQUIPO
                    || b.tipo() == DocumentProjectionV1.SeccionTipo.MANO_OBRA;
            for (var row : b.filas()) {
                var d = row.detalle();
                number(cells, hourly ? 1 : 2, d.cantidad());
                number(cells, hourly ? 2 : 3, d.precioEfectivo());
                if (hourly) {
                    number(cells, 3, row.costoHora());
                    number(cells, 4, d.rendimiento());
                }
                number(cells, 5, row.costo());
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

    /**
     * Stage 05: Core no longer renders. Every printed APU number, in print order and column, must cross the
     * Documentos boundary unchanged; the Documentos renderer tests assert the OOXML/PDF cells from this contract.
     */
    private static void contractParity(DocumentProjectionV1 contract, ApuDocumentoProyeccion.Documento d) {
        var views = contract.snapshot().apuRender();
        assertEquals(
                d.apus().stream().map(ApuDocumentoProyeccion.Analisis::id).toList(),
                views.stream().map(DocumentProjectionV1.ApuView::id).toList());
        for (int i = 0; i < views.size(); i++)
            assertEquals(
                    expected(d.apus().get(i)),
                    expected(views.get(i)),
                    "UUID " + views.get(i).id());
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
                "APU05 evidence harness only. Stage 05: projection parity only, rendering asserted by Documentos. No visual acceptance. Times are observations, not thresholds. Heap samples and lifetime JMX peaks are NOT task allocations. Lock duration unmeasured. Budget identity only first page (observed writer contract); APU identity every page. 1/32 SQL baselines pending. References not financially adopted.\n");
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
                    for (var row : block.filas()) {
                        var persistido = section.flatMap(s -> s.detalles().stream()
                                .filter(v -> v.id().equals(row.detalle().id()))
                                .findFirst());
                        assertEquals(persistido.orElseThrow(), row.detalle());
                    }
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
            for (String name : List.of("seed-pestanas", "seed-apilado", "seed-apus-pdf")) {
                var options = name.endsWith("pdf")
                        ? new OpcionesDocumento("pdf", Map.of())
                        : new OpcionesDocumento(
                                "xlsx", Map.of("layout", name.contains("apilado") ? "apilado" : "pestanas"));
                before = statistics.getPrepareStatementCount();
                start = System.nanoTime();
                var captured = captura.capturar(ID, owner, "apus", options);
                long captureQueries = statistics.getPrepareStatementCount() - before;
                manifest.append(name)
                        .append(" captureElapsedNs=")
                        .append(System.nanoTime() - start)
                        .append(" captureQueries=")
                        .append(captureQueries)
                        .append('\n');
                assertTrue(
                        captured.preflight().exportable(), captured.preflight().toString());
                assertEquals(16, captureQueries);
                assertEquals(jakarta.transaction.Status.STATUS_NO_TRANSACTION, tm.getStatus());
                var contract = DocumentoContratoMapper.proyectar(captured, "apus", options);
                Files.writeString(DIRECTORY.resolve(name + "-projection.txt"), contract.toString());
                contractParity(contract, document);
            }
            for (String name : List.of("seed-budget-xlsx", "seed-budget-portrait", "seed-budget-landscape")) {
                var options = PresupuestoDocumentoFixture.opciones(
                        name.endsWith("xlsx") ? "xlsx" : "pdf", name.contains("landscape"));
                var captured = captura.capturar(ID, owner, "presupuesto", options);
                var contract = DocumentoContratoMapper.proyectar(captured, "presupuesto", options);
                Files.writeString(DIRECTORY.resolve(name + "-projection.txt"), contract.toString());
                assertEquals(ID, contract.snapshot().presupuestoId(), name + " budget identity");
                PresupuestoDocumentoEvidenceIT.contractRubros(contract, rows);
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
