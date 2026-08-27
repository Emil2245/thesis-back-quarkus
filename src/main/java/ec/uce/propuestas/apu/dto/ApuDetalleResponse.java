package ec.uce.propuestas.apu.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Fila de una sección de APU (07-api-contract.md Apéndice B). */
public record ApuDetalleResponse(
        UUID id,
        Short orden,
        String descripcion,
        boolean esHerramientaMenor,
        Long insumoId,
        BigDecimal cantidad,
        BigDecimal rendimiento,
        String unidad,
        BigDecimal precioEfectivo,
        boolean precioHeredado,
        BigDecimal costoHora,
        BigDecimal costo) {}
