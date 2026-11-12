package ec.uce.propuestas.cronograma.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.cronograma.export.CronogramaExportPreflightService.SnapshotCompleto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CronogramaCaptureServiceTest {
    private static final UUID BUDGET = UUID.fromString("019a0000-0000-7000-8000-000000000001");

    @Test
    void blockedCaptureNeverBuildsProjectionOrRevision() {
        var preflight = Mockito.mock(CronogramaExportPreflightService.class);
        var projections = Mockito.mock(CronogramaProyeccionExportService.class);
        var snapshot = snapshot(BigDecimal.ZERO, BigDecimal.ZERO);
        when(preflight.cargarSnapshot(BUDGET, 1L)).thenReturn(snapshot);
        var capture = service(preflight, projections).capture(BUDGET, 1L, FormatoExportacion.XLSX, null);

        assertFalse(capture.preflight().exportable());
        assertNull(capture.projection());
        assertNull(capture.revisionHash());
        verifyNoInteractions(projections);
    }

    @Test
    void confirmedValuesAndWarningBelongToSameCaptureRevision() {
        var preflight = Mockito.mock(CronogramaExportPreflightService.class);
        var projections = Mockito.mock(CronogramaProyeccionExportService.class);
        var snapshot = snapshot(new BigDecimal("12.340000"), new BigDecimal("100.0000"));
        when(preflight.cargarSnapshot(BUDGET, 1L)).thenReturn(snapshot);
        when(preflight.esStale(snapshot)).thenReturn(false, false, true);
        var sheet = new HojaCronograma.ProyeccionXlsx(
                "P1", "Project", 2025, "SEMANA", 1, LocalDate.of(2025, 1, 1), List.of());
        when(projections.construirProyeccionCompleta(snapshot))
                .thenReturn(ec.uce.propuestas.cronograma.dto.ProyeccionExportacion.de(sheet));
        var service = service(preflight, projections);
        var first = service.capture(BUDGET, 1L, FormatoExportacion.XLSX, null);
        var same = service.capture(BUDGET, 1L, FormatoExportacion.XLSX, null);
        var stale = service.capture(BUDGET, 1L, FormatoExportacion.XLSX, null);

        assertNotNull(first.projection());
        assertEquals(first.revisionHash(), same.revisionHash());
        org.junit.jupiter.api.Assertions.assertNotEquals(first.revisionHash(), stale.revisionHash());
        assertEquals(List.of(), first.preflight().advertencias());
        assertEquals(1, stale.preflight().advertencias().size());
        assertEquals("xlsx", first.projection().format());
    }

    private static SnapshotCompleto snapshot(BigDecimal total, BigDecimal advance) {
        return new SnapshotCompleto(
                null, null, null, total, advance, List.of(), false, false, false, LocalDate.of(2025, 1, 1));
    }

    private static CronogramaCaptureService service(
            CronogramaExportPreflightService preflight, CronogramaProyeccionExportService projections) {
        var service = new CronogramaCaptureService();
        service.preflight = preflight;
        service.projections = projections;
        service.json = new ObjectMapper().findAndRegisterModules();
        return service;
    }
}
