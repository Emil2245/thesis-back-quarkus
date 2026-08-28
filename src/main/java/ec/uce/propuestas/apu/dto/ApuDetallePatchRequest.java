package ec.uce.propuestas.apu.dto;

import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * PATCH de una fila. {@code precioOverride} presente = setea override; null = hereda.
 *
 * <p>Plan 014 T4 — {@code @Digits(integer=8, fraction=2)} valida la precisión
 * del override monetario. La restricción de la BD ({@code NUMERIC(14,6)}
 * ⇒ 8 dígitos enteros como máximo) limita el integer a 8; usar
 * {@code integer=12} aceptaría valores que la BD rechazaría.
 *
 * <p>{@code JsonNullable<BigDecimal>} preserva las semánticas omit/null/present.
 * La anotación se aplica al componente del record. La dependencia
 * {@code jackson-databind-nullable} registra un {@code ValueExtractor}
 * Jakarta marcado {@code @UnwrapByDefault}, por lo que Bean Validation
 * valida el {@link BigDecimal} presente sin alterar omit/null.
 */
public record ApuDetallePatchRequest(
        JsonNullable<BigDecimal> cantidad,
        JsonNullable<BigDecimal> rendimiento,
        @Digits(integer = 8, fraction = 2) JsonNullable<BigDecimal> precioOverride) {}
