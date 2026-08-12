package ec.uce.propuestas.insumo.dto;

import ec.uce.propuestas.insumo.entity.TipoInsumo;
import java.math.BigDecimal;
import java.time.Instant;

public record InsumoResponse(
        Long id,
        String codigo,
        TipoInsumo tipo,
        String descripcion,
        String unidad,
        BigDecimal precioUnitario,
        Instant fechaActualizacion,
        boolean desactualizado) {}
