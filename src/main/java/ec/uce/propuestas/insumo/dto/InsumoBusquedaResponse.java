package ec.uce.propuestas.insumo.dto;

import ec.uce.propuestas.insumo.entity.TipoInsumo;

import java.math.BigDecimal;
import java.time.Instant;

/** InsumoResponse + fuente para el selector multi-fuente (P-16/P-21). */
public record InsumoBusquedaResponse(
        Long id,
        String codigo,
        TipoInsumo tipo,
        String descripcion,
        String unidad,
        BigDecimal precioUnitario,
        Instant fechaActualizacion,
        boolean desactualizado,
        String fuente,
        String baseNombre
) {}