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
@TestProfile(ApuDescargaConcurrencyIT.Profile.class)
class ApuDescargaConcurrencyIT {
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
        static volatile boolean capturePdf;
        static volatile SnapshotDocumento frozen;
        static volatile int statusAfterCapture;

        @Override
        @jakarta.transaction.Transactional(jakarta.transaction.Transactional.TxType.NOT_SUPPORTED)
        public SnapshotDocumento capturar(UUID id, Long caller, String documento, OpcionesDocumento opciones) {
            calls.incrementAndGet();
            assertEquals("apus", documento);
            var selectedOptions = capturePdf ? new OpcionesDocumento("pdf", Map.of()) : opciones;
            var snapshot = QuarkusTransaction.requiringNew()
                    .call(() -> super.capturar(id, caller, documento, selectedOptions));
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
                        null,
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
            if (!(context.getTarget() instanceof ApuDescargaService)) return context.proceed();
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
    ApuDescargaService descarga;

    @Inject
    TransactionManager tm;

    @Inject
    jakarta.persistence.EntityManager em;

    @Test
    void dirtyCallerPersistenceContextIsSuspendedAndCaptureUsesCommittedState() throws Exception {
        var f = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
        CaptureSpy.calls.set(0);
        tm.begin();
        try {
            var budget = em.find(ec.uce.propuestas.presupuesto.entity.Presupuesto.class, f.budget());
            budget.total = java.math.BigDecimal.ZERO;
            var result = descarga.generar(f.id(), f.caller(), new OpcionesDocumento("pdf", Map.of()));
            assertNotNull(result.archivo());
            assertEquals(ArchivoGenerado.PDF_MEDIA_TYPE, result.archivo().mediaType());
            assertEquals(Status.STATUS_ACTIVE, transactionStatus());
            assertEquals(java.math.BigDecimal.ZERO, budget.total, "Caller managed state is preserved");
        } finally {
            tm.rollback();
        }
        assertEquals(1, CaptureSpy.calls.get());
    }

    @Test
    void dispatchUsesCapturedOptionsRatherThanInput() throws Exception {
        var f = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
        CaptureSpy.calls.set(0);
        CaptureSpy.capturePdf = true;
        try {
            var result =
                    descarga.generar(f.id(), f.caller(), new OpcionesDocumento("xlsx", Map.of("layout", "apilado")));
            assertEquals("pdf", result.preflight().formato());
            assertEquals(ArchivoGenerado.PDF_MEDIA_TYPE, result.archivo().mediaType());
            assertTrue(result.archivo().nombreArchivo().endsWith(".pdf"));
            try (var doc = org.apache.pdfbox.Loader.loadPDF(result.archivo().bytes())) {
                assertTrue(doc.getNumberOfPages() > 0);
            }
            assertEquals(1, CaptureSpy.calls.get());
        } finally {
            CaptureSpy.capturePdf = false;
        }
    }

    private int transactionStatus() {
        try {
            return tm.getStatus();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

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
            ApuExportResourceIT.blocked(empty, "presupuesto-vacio");
            var p32 = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
            try (var c = ds.getConnection()) {
                PresupuestoExportResourceIT.sql(
                        c,
                        "update rubro set precio_unitario=0 where capitulo_id in (select id from capitulo where presupuesto_id=?)",
                        p32.budget());
            }
            ApuExportResourceIT.blocked(p32, "presupuesto-pu-cero");
            var poison = new SnapshotDocumento(
                    CaptureSpy.frozen.presupuestoId(),
                    CaptureSpy.frozen.version(),
                    CaptureSpy.frozen.vigente(),
                    CaptureSpy.frozen.notas(),
                    null,
                    null,
                    null,
                    null,
                    CaptureSpy.frozen.firmantes(),
                    CaptureSpy.frozen.capitulos(),
                    CaptureSpy.frozen.apus(),
                    CaptureSpy.frozen.preflight());
            assertThrows(
                    NullPointerException.class,
                    () -> ec.uce.propuestas.documento.exportacion.ApuDocumentoProyeccion.proyectar(poison),
                    "Blocked poison would fail even before a writer, if projection were invoked");
            for (String format : new String[] {"xlsx", "pdf"}) {
                var result = descarga.generar(empty.id(), empty.caller(), new OpcionesDocumento(format, Map.of()));
                assertNull(result.archivo(), "No file or bytes before gate");
                assertNull(descarga.generar(p32.id(), p32.caller(), new OpcionesDocumento(format, Map.of()))
                        .archivo());
            }
        } finally {
            CaptureSpy.poisonBlocked = false;
        }
    }

    @Test
    void committedChangesBetweenCaptureAndRenderNeverMixAndFrozenBytesSurvive() throws Exception {
        var f = PresupuestoExportResourceIT.fixture(ds, mailbox, true);
        try (var c = ds.getConnection()) {
            PresupuestoExportResourceIT.sql(
                    c,
                    "update parametros_proyecto set mostrar_nombre_proyecto_header=true,mensaje_footer='Footer anterior',porcentaje_indirecto=0.1,porcentaje_herramienta_menor=0.05 where proyecto_id=?",
                    f.project());
            PresupuestoExportResourceIT.sql(
                    c,
                    "with b as (insert into base_insumos(nombre,tipo,proyecto_id) values ('APU04','PROYECTO',?) returning id) insert into insumo(base_id,codigo,tipo,descripcion,unidad,precio_unitario) select id,'INPUT','MATERIAL','Material anterior','u',10 from b",
                    f.project());
            PresupuestoExportResourceIT.sql(
                    c,
                    "insert into apu_seccion(apu_id,tipo,subtotal,orden) select id,'MATERIAL',10,3 from apu where presupuesto_id=?",
                    f.budget());
            PresupuestoExportResourceIT.sql(
                    c,
                    "insert into apu_detalle(seccion_id,insumo_id,descripcion,orden,cantidad,unidad,costo) select s.id,i.id,'Material anterior',1,1,'u',10 from apu_seccion s join apu a on a.id=s.apu_id join insumo i on i.codigo='INPUT' join base_insumos b on b.id=i.base_id where a.presupuesto_id=? and b.proyecto_id=?",
                    f.budget(),
                    f.project());
            PresupuestoExportResourceIT.sql(
                    c,
                    "insert into apu_seccion(apu_id,tipo,subtotal,orden) select id,'EQUIPO',0.5,1 from apu where presupuesto_id=?",
                    f.budget());
            PresupuestoExportResourceIT.sql(
                    c,
                    "insert into apu_detalle(seccion_id,descripcion,orden,es_herramienta_menor,costo) select s.id,'HM',1,true,0.5 from apu_seccion s join apu a on a.id=s.apu_id where a.presupuesto_id=? and s.tipo='EQUIPO'",
                    f.budget());
            // Documentary fixtures deliberately distinguish persisted totals from diagnostic recalculation.
            PresupuestoExportResourceIT.sql(
                    c,
                    "update apu set costo_directo=10,costo_indirecto=1,costo_total=11 where presupuesto_id=?",
                    f.budget());
        }
        var options = new OpcionesDocumento("xlsx", Map.of("layout", "pestanas"));
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
                    PresupuestoExportResourceIT.sql(
                            c,
                            "update parametros_proyecto set mensaje_footer='Footer posterior',porcentaje_indirecto=0.2,porcentaje_herramienta_menor=0.07 where proyecto_id=?",
                            f.project());
                    PresupuestoExportResourceIT.sql(
                            c,
                            "update insumo set descripcion='Material posterior',precio_unitario=20 where base_id in (select id from base_insumos where proyecto_id=?)",
                            f.project());
                    PresupuestoExportResourceIT.sql(
                            c,
                            "update apu_detalle set descripcion='Material posterior',cantidad=2,costo=40 where seccion_id in (select s.id from apu_seccion s join apu a on a.id=s.apu_id where a.presupuesto_id=?)",
                            f.budget());
                    PresupuestoExportResourceIT.sql(
                            c,
                            "update apu_seccion set subtotal=40 where apu_id in (select id from apu where presupuesto_id=?)",
                            f.budget());
                    PresupuestoExportResourceIT.sql(
                            c,
                            "update apu_detalle set descripcion='HM',costo=2.8 where es_herramienta_menor=true and seccion_id in (select s.id from apu_seccion s join apu a on a.id=s.apu_id where a.presupuesto_id=?)",
                            f.budget());
                    PresupuestoExportResourceIT.sql(
                            c,
                            "update apu set costo_directo=40,costo_indirecto=8,costo_total=48 where presupuesto_id=?",
                            f.budget());
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
            assertTrue(before.contains("11.0"));
            assertTrue(before.contains("Footer anterior"));
            assertTrue(before.contains("Material anterior"));
            assertEquals(new java.math.BigDecimal("0.0500"), frozen.parametros().porcentajeHerramientaMenor());
            assertTrue(before.contains("Herramienta Menor 5.00%MO"));
            var oldDetail = frozen.apus().getFirst().secciones().stream()
                    .filter(s -> s.tipo() == ec.uce.propuestas.motor.SeccionTipo.MATERIAL)
                    .findFirst()
                    .orElseThrow()
                    .detalles()
                    .getFirst();
            assertEquals(0, oldDetail.insumo().precioUnitario().compareTo(new java.math.BigDecimal("10")));
            assertEquals(0, oldDetail.cantidad().compareTo(java.math.BigDecimal.ONE));
            assertEquals(0, oldDetail.costo().compareTo(new java.math.BigDecimal("10")));
            assertFalse(before.contains("20.12"));
            assertFalse(before.contains("posterior"));
            // Re-rendering the captured value after the commit cannot query current domain data.
            assertEquals(
                    before,
                    text(ApuXlsxWriter.renderizar(
                            ec.uce.propuestas.documento.exportacion.ApuDocumentoProyeccion.proyectar(frozen),
                            options)));
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
            assertTrue(after.contains("48.0"));
            assertTrue(after.contains("Footer posterior"));
            assertTrue(after.contains("Material posterior"));
            assertEquals(
                    new java.math.BigDecimal("0.0700"),
                    CaptureSpy.frozen.parametros().porcentajeHerramientaMenor());
            assertTrue(after.contains("Herramienta Menor 7.00%MO"));
            var newDetail = CaptureSpy.frozen.apus().getFirst().secciones().stream()
                    .filter(s -> s.tipo() == ec.uce.propuestas.motor.SeccionTipo.MATERIAL)
                    .findFirst()
                    .orElseThrow()
                    .detalles()
                    .getFirst();
            assertEquals(0, newDetail.insumo().precioUnitario().compareTo(new java.math.BigDecimal("20")));
            assertEquals(0, newDetail.cantidad().compareTo(new java.math.BigDecimal("2")));
            assertEquals(0, newDetail.costo().compareTo(new java.math.BigDecimal("40")));
            assertFalse(after.contains("20.12"));
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
