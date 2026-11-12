package ec.uce.propuestas.documento;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.documento.exportacion.CapturaDocumentoService;
import ec.uce.propuestas.documento.exportacion.DocumentoContratoMapper;
import ec.uce.propuestas.documento.exportacion.SnapshotDocumento;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
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
    jakarta.persistence.EntityManager em;

    @Inject
    jakarta.transaction.TransactionManager tm;

    static void rubros(List<SnapshotDocumento.Capitulo> chapters, List<SnapshotDocumento.Rubro> rows) {
        for (var chapter : chapters) {
            rubros(chapter.hijos(), rows);
            rows.addAll(chapter.rubros());
        }
    }

    static void contractRubros(List<DocumentProjectionV1.Capitulo> chapters, List<DocumentProjectionV1.Rubro> rows) {
        for (var chapter : chapters) {
            contractRubros(chapter.hijos(), rows);
            rows.addAll(chapter.rubros());
        }
    }

    /** Rowwise item|code multiplicity and exact quantity/PU/PT text the renderers print. */
    static void contractRubros(DocumentProjectionV1 contract, List<SnapshotDocumento.Rubro> captured) {
        var projected = new ArrayList<DocumentProjectionV1.Rubro>();
        contractRubros(contract.snapshot().capitulos(), projected);
        var found = new java.util.HashMap<String, Integer>();
        for (var row : projected) {
            var expected = captured.stream()
                    .filter(r -> r.id().equals(row.id()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(expected.item(), row.item());
            assertEquals(expected.codigo(), row.codigo());
            assertEquals(expected.cantidad().toPlainString(), row.cantidad().toPlainString(), row.codigo());
            assertEquals(
                    expected.precioUnitario().toPlainString(),
                    row.precioUnitario().toPlainString(),
                    row.codigo());
            assertEquals(
                    expected.precioTotal().toPlainString(), row.precioTotal().toPlainString(), row.codigo());
            found.merge(row.item() + "|" + row.codigo(), 1, Integer::sum);
        }
        var expectedCounts = new java.util.HashMap<String, Integer>();
        for (var row : captured) expectedCounts.merge(row.item() + "|" + row.codigo(), 1, Integer::sum);
        assertEquals(expectedCounts, found, "Rowwise code multiplicity, not substring parity");
    }

    static long heap() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    @Test
    void capturaSeedYProyectaContratoFueraDeTransaccion() throws Exception {
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
            // Stage 05: Core no longer renders. Each format's capture is mapped to the immutable contract sent to
            // Documentos; the rendered XLSX/PDF cells are asserted by the Documentos renderer tests.
            for (String name : List.of("seed-xlsx", "seed-portrait-pdf", "seed-landscape-pdf")) {
                boolean pdf = name.endsWith("pdf");
                var options = PresupuestoDocumentoFixture.opciones(pdf ? "pdf" : "xlsx", name.contains("landscape"));
                before = stats.getPrepareStatementCount();
                start = System.nanoTime();
                var captured = captura.capturar(id, caller, "presupuesto", options);
                long serviceMs = (System.nanoTime() - start) / 1_000_000;
                long serviceQueries = stats.getPrepareStatementCount() - before;
                assertTrue(
                        captured.preflight().exportable(), captured.preflight().toString());
                before = stats.getPrepareStatementCount();
                var contract = DocumentoContratoMapper.proyectar(captured, "presupuesto", options);
                long mapQueries = stats.getPrepareStatementCount() - before;
                Files.writeString(directory.resolve(name + "-projection.txt"), contract.toString());
                manifest.append(name)
                        .append(" captureElapsedMs=")
                        .append(serviceMs)
                        .append(" captureQueries=")
                        .append(serviceQueries)
                        .append(" mapQueries=")
                        .append(mapQueries)
                        .append('\n');
                Files.writeString(directory.resolve("manifest.txt"), manifest);
                assertEquals(0, mapQueries, "Contract mapping after capture transaction ends");
                assertEquals(captureQueries, serviceQueries, "Each format capture issues the same fixed SQL");
                assertEquals(jakarta.transaction.Status.STATUS_NO_TRANSACTION, tm.getStatus());
                assertEquals(id, contract.snapshot().presupuestoId());
                contractRubros(contract, rows);
                assertEquals(
                        snapshot.firmantes().stream()
                                .map(SnapshotDocumento.Firmante::nombre)
                                .toList(),
                        contract.snapshot().firmantes().stream()
                                .map(DocumentProjectionV1.Firmante::nombre)
                                .toList());
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
