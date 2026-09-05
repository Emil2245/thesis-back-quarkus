package ec.uce.propuestas.cronograma.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read model canónico congelado por Plan 026. Todos los campos calculados son solo lectura. */
public record CronogramaResponse(
        UUID id,
        UUID presupuestoId,
        String unidadTiempo,
        int numeroPeriodos,
        String totalGeneral,
        String totalGeneralRevisado,
        Instant fechaRevision,
        String estadoDistribucion,
        boolean desactualizado,
        String avanceFinal,
        List<ActividadCronogramaResponse> actividades,
        List<String> avancePorPeriodo,
        List<String> avanceAcumulado) {}
