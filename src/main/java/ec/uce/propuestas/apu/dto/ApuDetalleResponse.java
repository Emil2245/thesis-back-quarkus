package ec.uce.propuestas.apu.dto;

import java.math.BigDecimal;

/** Fila de una sección de APU (07-api-contract.md Apéndice B). */
public record ApuDetalleResponse(
        Long id,
        Short orden,
        String descripcion,
        boolean esHerramientaMenor,
        Long insumoId,
        Long apuAuxiliarId,
        BigDecimal cantidad,
        BigDecimal rendimiento,
        String unidad,
        BigDecimal precioEfectivo,
        boolean precioHeredado,
        BigDecimal costoHora,
        BigDecimal costo
) {}