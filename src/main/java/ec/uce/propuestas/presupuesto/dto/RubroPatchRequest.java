package ec.uce.propuestas.presupuesto.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record RubroPatchRequest(
        @NotNull @DecimalMin(value = "0.000001") BigDecimal cantidad) {}
