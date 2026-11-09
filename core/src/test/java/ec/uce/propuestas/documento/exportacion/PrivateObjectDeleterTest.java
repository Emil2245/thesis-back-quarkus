package ec.uce.propuestas.documento.exportacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

class PrivateObjectDeleterTest {
    private static final UUID OPERATION = UUID.fromString("01900000-0000-7000-8000-000000000017");
    private static final ExportCleanupLeaseService.Claim CLAIM =
            new ExportCleanupLeaseService.Claim(OPERATION, 1, "exports/" + OPERATION);

    @Test
    void deletesOnlyClaimedExportKeyFromConfiguredBucket() {
        S3Client s3 = mock(S3Client.class);
        new PrivateObjectDeleter(s3, "isolated-private-bucket").delete(CLAIM);
        verify(s3)
                .deleteObject(
                        argThat((DeleteObjectRequest request) -> "isolated-private-bucket".equals(request.bucket())
                                && CLAIM.objectKey().equals(request.key())));
        verifyNoMoreInteractions(s3);
        assertThrows(
                IllegalArgumentException.class,
                () -> new PrivateObjectDeleter(s3, "isolated-private-bucket")
                        .delete(new ExportCleanupLeaseService.Claim(OPERATION, 1, "exports/other")));
        verifyNoMoreInteractions(s3);
        assertThrows(IllegalStateException.class, () -> new PrivateObjectDeleter(s3, null).delete(CLAIM));
    }

    @Test
    void workerMarksOnlyAfterDeleteAndReleasesClaimAfterS3Failure() {
        var leases = mock(ExportCleanupLeaseService.class);
        var deleter = mock(PrivateObjectDeleter.class);
        var worker = new ExportCleanupWorker(leases, deleter);
        when(leases.claimNext()).thenReturn(Optional.of(CLAIM));
        assertEquals(true, worker.runOne());
        var ordered = inOrder(leases, deleter);
        ordered.verify(leases).claimNext();
        ordered.verify(deleter).delete(CLAIM);
        ordered.verify(leases).markDeleted(OPERATION, 1);
        verify(leases, never()).release(any(), anyLong());

        reset(leases, deleter);
        when(leases.claimNext()).thenReturn(Optional.of(CLAIM));
        doThrow(SdkClientException.create("S3 unavailable")).when(deleter).delete(CLAIM);
        assertThrows(SdkClientException.class, worker::runOne);
        verify(leases).release(OPERATION, 1);
        verify(leases, never()).markDeleted(any(), anyLong());
    }
}
