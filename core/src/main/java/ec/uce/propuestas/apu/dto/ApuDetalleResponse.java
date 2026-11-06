package ec.uce.propuestas.apu.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Fila de una sección de APU (07-api-contract.md Apéndice B).
 *
 * <p>Plan 07 — el {@code insumoId} es la identidad externa UUIDv7 del insumo
 * (columna {@code public_id}); nunca el {@code BIGINT} interno.</p>
 */
public record ApuDetalleResponse(
        UUID id,
        Short orden,
        String descripcion,
        boolean esHerramientaMenor,
        UUID insumoId,
        BigDecimal cantidad,
        BigDecimal rendimiento,
        String unidad,
        BigDecimal precioEfectivo,
        boolean precioHeredado,
        BigDecimal costoHora,
        BigDecimal costo) {}
