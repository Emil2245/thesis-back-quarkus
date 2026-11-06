package ec.uce.propuestas.documento;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.documento.exportacion.CapturaDocumentoService;
import ec.uce.propuestas.documento.exportacion.OpcionesDocumento;
import ec.uce.propuestas.documento.exportacion.SnapshotDocumento;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionManager;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(PresupuestoDescargaConcurrencyIT.Profile.class)
class PresupuestoDescargaConcurrencyIT {
    public static class Profile implements QuarkusTestProfile {
        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(CaptureSpy.class);
        }
    }

    /** Test-only CDI alternative: real capture pipeline, explicit equivalent REQUIRES_NEW boundary. */
    @Alternative
    @ApplicationScoped
    public static class CaptureSpy extends CapturaDocumentoService {
        @Inject
        TransactionManager tm;

        static final AtomicInteger calls = new AtomicInteger();
        static volatile CountDownLatch captured;
        static volatile CountDownLatch resume;
        static volatile boolean poisonBlocked;
        static volatile SnapshotDocumento frozen;
        static volatile int statusAfterCapture;

        @Override
        @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NOT_SUPPORTED)
        public SnapshotDocumento capturar(UUID id, Long caller, String documento, OpcionesDocumento opciones) {
            calls.incrementAndGet();
            var snapshot =
                    QuarkusTransaction.requiringNew().call(() -> super.capturar(id, caller, documento, opciones));
            try {
                statusAfterCapture = tm.getStatus();
                assertEquals(Status.STATUS_NO_TRANSACTION, statusAfterCapture);
                frozen = snapshot;
                var signal = captured;
                if (signal != null) {
                    signal.countDown();
                    assertTrue(resume.await(20, TimeUnit.SECONDS), "Commit did not finish");
                }
            } catch (Exception e) {
                throw new AssertionError(e);
            }
            if (poisonBlocked && !snapshot.preflight().exportable()) {
                // Either real writer would dereference these missing presentation fields.
                // A 409 with no exception therefore proves the render branch was not invoked.
                return new SnapshotDocumento(
                        snapshot.presupuestoId(),
                        snapshot.version(),
                        snapshot.vigente(),
                        snapshot.notas(),
                        null,
                        null,
                        snapshot.parametros(),
                        null,
                        snapshot.firmantes(),
                        snapshot.capitulos(),
                        snapshot.apus(),
                        snapshot.preflight());
            }
            return snapshot;
        }
    }

    /** Runs inside the JTA interceptor: observes the production service, including its render. */
    @jakarta.interceptor.Interceptor
    @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NOT_SUPPORTED)
    @jakarta.annotation.Priority(jakarta.interceptor.Interceptor.Priority.PLATFORM_BEFORE + 300)
    public static class TransactionProbe {
        @Inject
        TransactionManager tm;

        static final AtomicInteger completedWithoutTransaction = new AtomicInteger();

        @jakarta.interceptor.AroundInvoke
        Object observe(jakarta.interceptor.InvocationContext context) throws Exception {
            if (!(context.getTarget() instanceof PresupuestoDescargaService)) return context.proceed();
            assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus(), "Before capture/render");
            Object result = context.proceed();
            assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus(), "After real render");
            completedWithoutTransaction.incrementAndGet();
            return result;
        }
    }

    @Inject
    DataSource ds;

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    PresupuestoDescargaService descarga;

    @Inject
    TransactionManager tm;

    static String text(byte[] bytes) throws Exception {
        try (var wb = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            StringBuilder text = new StringBuilder();
            for (var row : wb.getSheetAt(0))
                for (var cell : row) text.append(cell).append('\n');
            return text.toString();
        }
    }

    @Test
    void blockersNeverInvokeEitherRealWriter() throws Exception {
        CaptureSpy.poisonBlocked = true;
        try {
            var empty = PresupuestoExportResourceIT.fixture(ds, mailbox, false);
            PresupuestoExportResourceIT.blocked(empty, "presupuesto-vacio");
            var p32 = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
            try (var c = ds.getConnection()) {
                PresupuestoExportResourceIT.sql(
                        c,
                        "update rubro set precio_unitario=0 where capitulo_id in (select id from capitulo where presupuesto_id=?)",
                        p32.budget());
            }
            PresupuestoExportResourceIT.blocked(p32, "presupuesto-pu-cero");
            for (String format : new String[] {"xlsx", "pdf"}) {
                var result = descarga.generar(empty.id(), empty.caller(), new OpcionesDocumento(format, Map.of()));
                assertNull(result.archivo(), "No file or bytes before gate");
            }
        } finally {
            CaptureSpy.poisonBlocked = false;
        }
    }

    @Test
    void committedChangesBetweenCaptureAndRenderNeverMixAndFrozenBytesSurvive() throws Exception {
        var f = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
        var options = new OpcionesDocumento("xlsx", Map.of());
        CaptureSpy.calls.set(0);
        TransactionProbe.completedWithoutTransaction.set(0);
        CaptureSpy.captured = new CountDownLatch(1);
        CaptureSpy.resume = new CountDownLatch(1);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = executor.submit(() -> descarga.generar(f.id(), f.caller(), options));
            try {
                assertTrue(CaptureSpy.captured.await(20, TimeUnit.SECONDS));
                try (var c = ds.getConnection()) {
                    c.setAutoCommit(false);
                    PresupuestoExportResourceIT.sql(
                            c, "update proyecto set nombre_proyecto='Proyecto posterior' where id=?", f.project());
                    PresupuestoExportResourceIT.sql(
                            c, "update firmante set nombre='Responsable posterior' where proyecto_id=?", f.project());
                    PresupuestoExportResourceIT.sql(
                            c,
                            "update rubro set cantidad=2.5,precio_unitario=20.12,precio_total=50.300000 where capitulo_id in (select id from capitulo where presupuesto_id=?)",
                            f.budget());
                    PresupuestoExportResourceIT.sql(
                            c, "update capitulo set total=50.300000 where presupuesto_id=?", f.budget());
                    PresupuestoExportResourceIT.sql(c, "update presupuesto set total=50.300000 where id=?", f.budget());
                    c.commit();
                }
            } finally {
                CaptureSpy.resume.countDown();
            }
            var old = pending.get(30, TimeUnit.SECONDS);
            assertEquals(1, CaptureSpy.calls.get(), "One capture per service request");
            assertEquals(Status.STATUS_NO_TRANSACTION, CaptureSpy.statusAfterCapture);
            assertEquals(Status.STATUS_NO_TRANSACTION, tm.getStatus());
            var frozen = CaptureSpy.frozen;
            String before = text(old.archivo().bytes());
            assertTrue(before.contains("Proyecto anterior"));
            assertTrue(before.contains("Responsable anterior"));
            assertTrue(before.contains("1.2345"));
            assertTrue(before.contains("12.34"));
            assertFalse(before.contains("posterior"));
            // Re-rendering the captured value after the commit cannot query current domain data.
            assertEquals(before, text(PresupuestoXlsxWriter.renderizar(frozen, options)));
            CaptureSpy.captured = null;
            var next = QuarkusTransaction.requiringNew().call(() -> {
                assertEquals(Status.STATUS_ACTIVE, tm.getStatus());
                var result = descarga.generar(f.id(), f.caller(), options);
                assertEquals(Status.STATUS_ACTIVE, tm.getStatus(), "Caller transaction resumed only after render");
                return result;
            });
            assertEquals(2, CaptureSpy.calls.get());
            assertEquals(2, TransactionProbe.completedWithoutTransaction.get());
            String after = text(next.archivo().bytes());
            assertTrue(after.contains("Proyecto posterior"));
            assertTrue(after.contains("Responsable posterior"));
            assertTrue(after.contains("2.5"));
            assertTrue(after.contains("20.12"));
            assertFalse(after.contains("Proyecto anterior"));
            assertFalse(after.contains("Responsable anterior"));
            assertTrue(next.preflight().warnings().stream()
                    .anyMatch(w -> w.codigo().equals("cronograma-desactualizado")));
            assertEquals(before, text(old.archivo().bytes()), "Already produced file stays unchanged");
        } finally {
            CaptureSpy.captured = null;
            CaptureSpy.resume = null;
            CaptureSpy.poisonBlocked = false;
        }
    }
}
