package ec.uce.propuestas.proyecto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ValorReferenciaRequest(
        @NotBlank(message = "valor-requerido") @Size(max = 100)
        String valor,

        @NotBlank(message = "descripcion-requerida") String descripcion,

        @NotBlank(message = "fuente-requerida") @Size(max = 200)
        String fuente) {}
