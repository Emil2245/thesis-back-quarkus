package ec.uce.propuestas.documento;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.documento.exportacion.CapturaDocumentoService;
import ec.uce.propuestas.documento.exportacion.OpcionesDocumento;
import ec.uce.propuestas.documento.exportacion.PreflightDocumento;
import ec.uce.propuestas.documento.exportacion.SnapshotDocumento;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DescargaRemotaTest {
    @Test
    void budgetUsesRemoteOnlyAfterExportableCapture() {
        var snapshot = PresupuestoDocumentoFixture.snapshot(2, 0);
        var events = new ArrayList<String>();
        var service = new PresupuestoDescargaService();
        service.captura = capture(snapshot, events);
        var options = PresupuestoDocumentoFixture.opciones("xlsx", false);
        var bytes = new byte[] {80, 75};
        service.renderer = (captured, document, selected) -> {
            assertSame(snapshot, captured);
            assertEquals("presupuesto", document);
            assertEquals(options, selected);
            events.add("render");
            return bytes;
        };

        var result = service.generar(snapshot.presupuestoId(), 1L, options);

        assertSame(bytes, result.archivo().bytes());
        assertEquals(ArchivoGenerado.XLSX_MEDIA_TYPE, result.archivo().mediaType());
        assertTrue(result.archivo().nombreArchivo().endsWith("-v7.xlsx"));
        assertEquals(List.of("capture", "render"), events);
    }

    @Test
    void apuUsesRemoteOnlyAfterExportableCapture() {
        var source = PresupuestoDocumentoFixture.snapshot(2, 0);
        var preflight = new PreflightDocumento(
                source.presupuestoId(), source.version(), "apus", "pdf", Map.of(), true, List.of(), List.of());
        var snapshot = withPreflight(source, preflight);
        var events = new ArrayList<String>();
        var service = new ApuDescargaService();
        service.captura = capture(snapshot, events);
        var options = new OpcionesDocumento("pdf", Map.of());
        var bytes = new byte[] {37, 80, 68, 70};
        service.renderer = (captured, document, selected) -> {
            assertSame(snapshot, captured);
            assertEquals("apus", document);
            assertEquals(options, selected);
            events.add("render");
            return bytes;
        };

        var result = service.generar(snapshot.presupuestoId(), 1L, options);

        assertSame(bytes, result.archivo().bytes());
        assertEquals(ArchivoGenerado.PDF_MEDIA_TYPE, result.archivo().mediaType());
        assertTrue(result.archivo().nombreArchivo().endsWith("-v7.pdf"));
        assertEquals(List.of("capture", "render"), events);
    }

    @Test
    void blockedCaptureNeverContactsDocumentos() {
        var source = PresupuestoDocumentoFixture.snapshot(2, 0);
        var preflight = new PreflightDocumento(
                source.presupuestoId(), source.version(), "presupuesto", "xlsx", Map.of(), false, List.of(), List.of());
        var snapshot = withPreflight(source, preflight);
        var events = new ArrayList<String>();
        var service = new PresupuestoDescargaService();
        service.captura = capture(snapshot, events);
        service.renderer = (captured, document, selected) -> {
            throw new AssertionError("A blocked capture must never be rendered");
        };

        var result = service.generar(snapshot.presupuestoId(), 1L, PresupuestoDocumentoFixture.opciones("xlsx", false));

        assertFalse(result.preflight().exportable());
        assertNull(result.archivo());
        assertEquals(List.of("capture"), events);
    }

    private static CapturaDocumentoService capture(SnapshotDocumento snapshot, List<String> events) {
        return new CapturaDocumentoService() {
            @Override
            public SnapshotDocumento capturar(UUID id, Long caller, String document, OpcionesDocumento options) {
                assertEquals(snapshot.presupuestoId(), id);
                assertEquals(1L, caller);
                assertEquals(snapshot.preflight().documento(), document);
                assertEquals(snapshot.preflight().formato(), options.formato());
                events.add("capture");
                return snapshot;
            }
        };
    }

    private static SnapshotDocumento withPreflight(SnapshotDocumento s, PreflightDocumento preflight) {
        return new SnapshotDocumento(
                s.presupuestoId(),
                s.version(),
                s.vigente(),
                s.notas(),
                s.total(),
                s.proyecto(),
                s.parametros(),
                s.display(),
                s.firmantes(),
                s.capitulos(),
                s.apus(),
                preflight);
    }
}
