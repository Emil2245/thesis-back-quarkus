package ec.uce.propuestas.apu.dto;

import org.openapitools.jackson.nullable.JsonNullable;

import java.math.BigDecimal;

/** PATCH de una fila. `precioOverride` presente = setea override; null = hereda. */
public record ApuDetallePatchRequest(
        JsonNullable<BigDecimal> cantidad,
        JsonNullable<BigDecimal> rendimiento,
        JsonNullable<BigDecimal> precioOverride
) {}