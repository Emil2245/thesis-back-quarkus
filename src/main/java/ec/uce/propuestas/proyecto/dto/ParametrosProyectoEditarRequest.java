package ec.uce.propuestas.proyecto.dto;

import ec.uce.propuestas.proyecto.entity.ModoCodigoRubro;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Edición de los parámetros de cálculo de un proyecto. Los rangos efectivos de
 * HM, CI e IVA se leen desde {@link ec.uce.propuestas.proyecto.entity.ParametrosSistema}
 * y la validación es responsabilidad del servicio (no Bean Validation hardcoded).
 * Los campos de Presentación y Codificación son opcionales: si se omiten (null) se
 * conserva el valor guardado.
 */
public record ParametrosProyectoEditarRequest(
        @NotNull BigDecimal porcentajeHerramientaMenor,
        BigDecimal porcentajeIndirecto,
        @NotNull BigDecimal iva,
        @Size(max = 10) String moneda,
        Boolean mostrarSeccionesVacias,
        Boolean sufijosSeccionActivos,
        Boolean mostrarSubtotalesSeccion,
        Boolean mostrarSubtotalesPie,
        Boolean mostrarNombreProyectoHeader,
        Boolean enumerarApus,
        String mensajeFooter,
        ModoCodigoRubro modoCodigoRubro) {

    /** Constructor corto: sin opciones de presentación (conservan el valor guardado). */
    public ParametrosProyectoEditarRequest(
            BigDecimal porcentajeHerramientaMenor, BigDecimal porcentajeIndirecto, BigDecimal iva, String moneda) {
        this(
                porcentajeHerramientaMenor,
                porcentajeIndirecto,
                iva,
                moneda,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
