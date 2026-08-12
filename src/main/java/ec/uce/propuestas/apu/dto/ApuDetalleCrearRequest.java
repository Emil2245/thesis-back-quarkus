package ec.uce.propuestas.apu.dto;

import ec.uce.propuestas.motor.SeccionTipo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ApuDetalleCrearRequest(
        @NotNull SeccionTipo seccionTipo,
        @NotNull Long insumoId,
        @NotNull @DecimalMin(value = "0.000001") BigDecimal cantidad,
        @DecimalMin(value = "0.000001") BigDecimal rendimiento) {}
