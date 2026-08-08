package ec.uce.propuestas.proyecto.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ParametrosProyectoEditarRequest(
        @NotNull @DecimalMin("0.0000") @DecimalMax("0.2000") BigDecimal porcentajeHerramientaMenor,
        @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal porcentajeIndirecto,
        @NotNull @DecimalMin("0.0000") @DecimalMax("0.3000") BigDecimal iva,
        @Size(max = 10) String moneda
) {}