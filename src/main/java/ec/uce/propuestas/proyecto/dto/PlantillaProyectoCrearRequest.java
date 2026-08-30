package ec.uce.propuestas.proyecto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PlantillaProyectoCrearRequest(
        @NotBlank String nombre,
        String descripcion,
        @NotNull Long proyectoId) {}
