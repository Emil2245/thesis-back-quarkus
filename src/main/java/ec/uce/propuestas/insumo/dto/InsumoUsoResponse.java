package ec.uce.propuestas.insumo.dto;

/** P-18: dónde se usa un insumo (bloque M/N/O/P / heredado vs override). */
public record InsumoUsoResponse(
        Long apuId,
        String codigo,
        String descripcion,
        String bloque,
        boolean override
) {}