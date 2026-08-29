package ec.uce.propuestas.presupuesto.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record DescuentoGlobalRequest(
        @NotNull @DecimalMin("0.0000") @DecimalMax("0.5000") BigDecimal porcentaje) {}
