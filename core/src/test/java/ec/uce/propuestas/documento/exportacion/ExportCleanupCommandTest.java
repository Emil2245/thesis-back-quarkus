package ec.uce.propuestas.documento.exportacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

class ExportCleanupCommandTest {
    @Test
    void exitsAfterBoundedClaimsOrEmptyQueueAndRejectsArguments() throws Exception {
        var worker = mock(ExportCleanupWorker.class);
        var command = new ExportCleanupCommand(worker);
        assertEquals(0, command.run());
        verify(worker, times(1)).runOne();
        reset(worker);
        when(worker.runOne()).thenReturn(true, true, false);
        assertEquals(0, command.run());
        verify(worker, times(3)).runOne();
        reset(worker);
        when(worker.runOne()).thenReturn(true);
        assertEquals(0, command.run());
        verify(worker, times(100)).runOne();
        reset(worker);
        assertEquals(2, command.run("unexpected"));
        verifyNoInteractions(worker);
    }

    @Test
    void failedDeleteExitsNonzeroSoTheJobCanRetryLater() throws Exception {
        var worker = mock(ExportCleanupWorker.class);
        when(worker.runOne()).thenThrow(new IllegalStateException("S3 unavailable"));
        assertEquals(1, new ExportCleanupCommand(worker).run());
        verify(worker, times(1)).runOne();
    }
}
