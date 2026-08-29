package ec.uce.propuestas.presupuesto.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PresupuestoVersionResponse(
        Long presupuestoId,
        Short version,
        boolean esVigente,
        Long origenId,
        String notas,
        Instant fechaCreacion,
        BigDecimal totalGeneral) {}
