package ec.uce.propuestas.cronograma.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CronogramaCrearRequest(
        @NotBlank String unidadTiempo, @Positive short numeroPeriodos) {}
