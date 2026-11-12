package ec.uce.propuestas.cronograma.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.cronograma.export.CronogramaExportPreflightService.BloqueosCalculador;
import ec.uce.propuestas.cronograma.export.CronogramaExportPreflightService.ResultadoBloqueos;
import ec.uce.propuestas.documento.exportacion.ExportFingerprint;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Core owns preflight and domain calculations; no HTTP or S3 operation runs under this transaction. */
@ApplicationScoped
public class CronogramaCaptureService {
    @Inject
    CronogramaExportPreflightService preflight;

    @Inject
    CronogramaProyeccionExportService projections;

    @Inject
    ObjectMapper json;

    public record Capture(
            ResultadoBloqueos preflight, boolean stale, CronogramaProjectionV1 projection, String revisionHash) {}

    @Transactional
    public Capture capture(UUID budgetId, Long ownerId, FormatoExportacion format, PapelCronograma paper) {
        if (format == null
                || (format == FormatoExportacion.PDF && paper == null)
                || (format != FormatoExportacion.PDF && paper != null)) {
            throw new IllegalArgumentException("Invalid cronograma format or paper");
        }
        var snapshot = preflight.cargarSnapshot(budgetId, ownerId);
        boolean stale = preflight.esStale(snapshot);
        var result = BloqueosCalculador.evaluar(snapshot.comun(), format, stale);
        if (!result.exportable()) return new Capture(result, stale, null, null);

        CronogramaProjectionV1 projection = format == FormatoExportacion.MSPDI
                ? CronogramaProjectionMapper.fromMspdi(projections.construirProyeccionMspdi(snapshot))
                : CronogramaProjectionMapper.fromSheet(
                        projections.construirProyeccionCompleta(snapshot),
                        format.token(),
                        format == FormatoExportacion.PDF ? paper.name().toLowerCase(Locale.ROOT) : null);
        // Domain values and the captured warning state share one lock and one transaction.
        String revision = ExportFingerprint.hash(
                json, Map.of("budgetId", budgetId.toString(), "projection", projection, "stale", stale));
        return new Capture(result, stale, projection, revision);
    }
}
