package ec.uce.propuestas.proyecto.dto;

import ec.uce.propuestas.proyecto.entity.RolFirmante;
import jakarta.validation.constraints.*;

public record FirmanteCrearRequest(
        @NotBlank @Size(max = 200) String nombre,
        @NotBlank @Size(max = 300) String cargo,
        @NotNull RolFirmante rol,
        @NotNull @Min(1) @Max(32767) Short orden) {
}
