package ec.uce.propuestas.documento.exportacion;

import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import java.util.Objects;

/** Named, build-time-selected entry point for a separate one-shot cleanup workload. */
@QuarkusMain(name = "cleanup")
public class ExportCleanupCommand implements QuarkusApplication {
    private static final int MAX_CLAIMS_PER_RUN = 100;

    @Inject
    ExportCleanupWorker worker;

    public ExportCleanupCommand() {}

    ExportCleanupCommand(ExportCleanupWorker worker) {
        this.worker = Objects.requireNonNull(worker, "worker");
    }

    @Override
    public int run(String... args) {
        if (args == null || args.length != 0) return 2;
        try {
            for (int count = 0; count < MAX_CLAIMS_PER_RUN; count++) {
                if (!worker.runOne()) return 0;
            }
            return 0;
        } catch (RuntimeException failed) {
            // A nonzero exit lets the scheduled workload retry; no object key or capability is logged.
            return 1;
        }
    }
}
