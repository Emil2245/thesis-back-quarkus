package ec.uce.propuestas.apu.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Resumen de un APU en el listado (07-api-contract.md Apéndice B). */
public record ApuResumenResponse(
        UUID id,
        String codigo,
        String descripcion,
        String unidad,
        BigDecimal costoDirecto,
        BigDecimal costoTotal,
        boolean vinculado) {}
