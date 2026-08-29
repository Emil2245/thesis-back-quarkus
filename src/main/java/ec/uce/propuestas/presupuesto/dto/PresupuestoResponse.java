package ec.uce.propuestas.presupuesto.dto;

import java.math.BigDecimal;
import java.util.List;

public record PresupuestoResponse(
        Long presupuestoId,
        Short version,
        boolean esVigente,
        BigDecimal totalGeneral,
        List<CapituloResponse> capitulos) {}
