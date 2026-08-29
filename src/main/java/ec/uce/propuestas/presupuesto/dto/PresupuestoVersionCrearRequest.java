package ec.uce.propuestas.presupuesto.dto;

import jakarta.validation.constraints.NotNull;

public record PresupuestoVersionCrearRequest(@NotNull Long origenId, String notas) {}
