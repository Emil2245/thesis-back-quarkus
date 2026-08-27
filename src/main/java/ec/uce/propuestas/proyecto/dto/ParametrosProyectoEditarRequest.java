package ec.uce.propuestas.proyecto.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Edición de los parámetros de cálculo de un proyecto. Los rangos efectivos de
 * HM, CI e IVA se leen desde {@link ec.uce.propuestas.proyecto.entity.ParametrosSistema}
 * y la validación es responsabilidad del servicio (no Bean Validation hardcoded).
 */
public record ParametrosProyectoEditarRequest(
        @NotNull BigDecimal porcentajeHerramientaMenor,
        BigDecimal porcentajeIndirecto,
        @NotNull BigDecimal iva,
        @Size(max = 10) String moneda) {}
