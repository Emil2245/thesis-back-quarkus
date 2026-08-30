package ec.uce.propuestas.cronograma.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CronogramaResponse(
        Long id,
        Long presupuestoId,
        String unidadTiempo,
        short numeroPeriodos,
        BigDecimal totalGeneral,
        BigDecimal totalGeneralRevisado,
        Instant fechaRevision,
        boolean desactualizado,
        List<ActividadResponse> actividades) {}
