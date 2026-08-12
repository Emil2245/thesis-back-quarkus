package ec.uce.propuestas.apu.dto;

import java.math.BigDecimal;

/** Resumen de un APU en el listado (07-api-contract.md Apéndice B). */
public record ApuResumenResponse(
        Long id,
        String codigo,
        String descripcion,
        String unidad,
        boolean esAuxiliar,
        BigDecimal costoDirecto,
        BigDecimal costoTotal,
        boolean vinculado) {}
