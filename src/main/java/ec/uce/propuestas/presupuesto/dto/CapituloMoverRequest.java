package ec.uce.propuestas.presupuesto.dto;

import jakarta.validation.constraints.NotNull;

public record CapituloMoverRequest(Long parentId, @NotNull Short orden) {}
