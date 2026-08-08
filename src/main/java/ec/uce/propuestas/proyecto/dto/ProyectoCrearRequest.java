package ec.uce.propuestas.proyecto.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ProyectoCrearRequest(
        @NotBlank String nombreProyecto,
        @Size(max = 50) String codigo,
        String descripcion,
        @NotNull @Min(2000) @Max(2200) Short anio,
        LocalDate fechaInicio,
        @NotNull @Min(1) Short plazoEjecucion,
        @NotNull String plazoUnidad,
        @NotBlank @Size(max = 200) String direccionInstitucional,
        @Size(max = 200) String subdireccionInstitucional
) {}