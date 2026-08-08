package ec.uce.propuestas.insumo.dto;

import ec.uce.propuestas.insumo.entity.TipoInsumo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record InsumoCrearRequest(
        @NotBlank
        @Size(max = 50)
        String codigo,
        @NotNull
        TipoInsumo tipo,
        @NotBlank
        String descripcion,
        @Size(max = 10)
        String unidad,
        @NotNull
        @DecimalMin(value = "0.000001")
        BigDecimal precioUnitario
) {}