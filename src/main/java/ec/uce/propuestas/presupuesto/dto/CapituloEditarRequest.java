package ec.uce.propuestas.presupuesto.dto;

import jakarta.validation.constraints.NotBlank;

public record CapituloEditarRequest(@NotBlank String descripcion) {}
