package ec.uce.propuestas.apu.dto;

import java.math.BigDecimal;
import java.util.List;

/** Detalle completo de un APU (07-api-contract.md Apéndice B). */
public record ApuResponse(
        Long id,
        String codigo,
        String descripcion,
        String unidad,
        boolean esAuxiliar,
        BigDecimal costoDirecto,
        BigDecimal costoIndirecto,
        BigDecimal costoTotal,
        BigDecimal porcentajeIndirecto,
        BigDecimal porcentajeIndirectoEfectivo,
        BigDecimal porcentajeDescuento,
        List<ApuSeccionResponse> secciones
) {}