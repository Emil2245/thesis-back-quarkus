package ec.uce.propuestas.documento.exportacion;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Objects;

/** One bounded delete attempt: S3 confirmation must precede the DB marker. */
@ApplicationScoped
public class ExportCleanupWorker {
    @Inject
    ExportCleanupLeaseService leases;

    @Inject
    PrivateObjectDeleter deleter;

    public ExportCleanupWorker() {}

    ExportCleanupWorker(ExportCleanupLeaseService leases, PrivateObjectDeleter deleter) {
        this.leases = Objects.requireNonNull(leases, "leases");
        this.deleter = Objects.requireNonNull(deleter, "deleter");
    }

    public boolean runOne() {
        var next = leases.claimNext();
        if (next.isEmpty()) return false;
        var claim = next.orElseThrow();
        try {
            deleter.delete(claim);
            leases.markDeleted(claim.operationId(), claim.fence());
            return true;
        } catch (RuntimeException failure) {
            try {
                leases.release(claim.operationId(), claim.fence());
            } catch (RuntimeException releaseFailure) {
                failure.addSuppressed(releaseFailure);
            }
            throw failure;
        }
    }
}
