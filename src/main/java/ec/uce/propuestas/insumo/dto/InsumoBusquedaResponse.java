package ec.uce.propuestas.insumo.dto;

import ec.uce.propuestas.insumo.entity.TipoInsumo;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * InsumoResponse + fuente para el selector multi-fuente (P-16/P-21).
 *
 * <p>Plan 07 — el {@code id} público es el {@code publicId} UUIDv7; nunca el
 * {@code BIGINT} interno.</p>
 */
public record InsumoBusquedaResponse(
        UUID id,
        String codigo,
        TipoInsumo tipo,
        String descripcion,
        String unidad,
        BigDecimal precioUnitario,
        Instant fechaActualizacion,
        boolean desactualizado,
        String fuente,
        String baseNombre) {}
