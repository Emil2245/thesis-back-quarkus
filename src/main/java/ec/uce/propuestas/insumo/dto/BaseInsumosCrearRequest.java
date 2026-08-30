package ec.uce.propuestas.insumo.dto;

import jakarta.validation.constraints.NotBlank;

public record BaseInsumosCrearRequest(@NotBlank String nombre) {}
