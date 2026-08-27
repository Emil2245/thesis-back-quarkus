package ec.uce.propuestas.apu.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Detalle completo de un APU (07-api-contract.md Apéndice B). */
public record ApuResponse(
        UUID id,
        String codigo,
        String descripcion,
        String unidad,
        BigDecimal costoDirecto,
        BigDecimal costoIndirecto,
        BigDecimal costoTotal,
        BigDecimal porcentajeIndirecto,
        BigDecimal porcentajeIndirectoEfectivo,
        BigDecimal porcentajeDescuento,
        List<ApuSeccionResponse> secciones) {}
