package ec.uce.propuestas.insumo.dto;

import jakarta.validation.constraints.NotNull;

public record CopiarBaseRequest(
        @NotNull String fuenteTipo, // CENTRAL | PROYECTO
        Long baseId,
        Long proyectoId) {}
