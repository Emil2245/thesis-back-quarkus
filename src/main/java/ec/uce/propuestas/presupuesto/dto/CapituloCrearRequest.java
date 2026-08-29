package ec.uce.propuestas.presupuesto.dto;

import jakarta.validation.constraints.NotBlank;

public record CapituloCrearRequest(@NotBlank String descripcion, Long parentId, Short orden) {}
