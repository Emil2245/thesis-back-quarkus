package ec.uce.propuestas.insumo.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Código y tipo son inmutables en la edición (clave de upsert D-06). */
public record InsumoEditarRequest(
        @NotBlank String descripcion,
        @Size(max = 10) String unidad,
        @NotNull @DecimalMin(value = "0.000001") BigDecimal precioUnitario) {}
