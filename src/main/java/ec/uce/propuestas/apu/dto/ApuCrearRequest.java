package ec.uce.propuestas.apu.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApuCrearRequest(
        @Size(max = 20) String codigo,
        @NotBlank String descripcion,
        @NotBlank @Size(max = 10) String unidad) {}
