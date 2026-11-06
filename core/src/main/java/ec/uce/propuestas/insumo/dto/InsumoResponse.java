package ec.uce.propuestas.insumo.dto;

import ec.uce.propuestas.insumo.entity.TipoInsumo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Plan 07 — el {@code id} público es el {@code publicId} UUIDv7 de la fila;
 * el {@code BIGINT} interno nunca aparece en el JSON.
 */
public record InsumoResponse(
        UUID id,
        String codigo,
        TipoInsumo tipo,
        String descripcion,
        String unidad,
        BigDecimal precioUnitario,
        Instant fechaActualizacion,
        boolean desactualizado) {}
