package ec.uce.propuestas.proyecto.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Mutación de los parámetros globales (ruta {@code /proyectos/parametros-sistema}).
 * Los rangos de HM/CI/IVA/descuento son a su vez la fuente autoritativa para
 * {@code PUT /proyectos/{id}/parametros}, así que deben satisfacer
 * {@code min <= max} y permanecer en el universo {@code [0, 1]}.
 */
public record ParametrosSistemaEditarRequest(
        @NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal porcentajeHerramientaMenor,
        @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal porcentajeIndirecto,
        @NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal iva,
        @NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal rangoHmMin,
        @NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal rangoHmMax,
        @NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal rangoCiMin,
        @NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal rangoCiMax,
        @NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal rangoDescuentoMin,
        @NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal rangoDescuentoMax,
        @NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal rangoIvaMin,
        @NotNull @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal rangoIvaMax,
        @Size(max = 10) String moneda) {}
