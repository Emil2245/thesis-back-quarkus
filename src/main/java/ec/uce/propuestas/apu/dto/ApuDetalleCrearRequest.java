package ec.uce.propuestas.apu.dto;

import ec.uce.propuestas.motor.SeccionTipo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Plan 07 — el {@code insumoId} es la identidad externa UUIDv7 del insumo
 * (columna {@code public_id}); nunca el {@code BIGINT} interno. La validación
 * de UUIDv7 la hace el resource antes de delegar al service; el service
 * resuelve UUID → BIGINT vía {@code findByPublicIdAndOwnerScope} y aplica la
 * semántica de "copia al usar" (N04 §A9).
 */
public record ApuDetalleCrearRequest(
        @NotNull SeccionTipo seccionTipo,
        @NotNull UUID insumoId,
        @NotNull @DecimalMin(value = "0.000001") BigDecimal cantidad,
        @DecimalMin(value = "0.000001") BigDecimal rendimiento) {}
