package ec.uce.propuestas.proyecto.dto;

import ec.uce.propuestas.proyecto.entity.RolFirmante;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FirmanteCrearRequest(
        @NotBlank @Size(max = 200) String nombre,
        @NotBlank @Size(max = 300) String cargo,
        @NotNull RolFirmante rol,
        @NotNull @Min(1) @Max(32767) Short orden
) {}