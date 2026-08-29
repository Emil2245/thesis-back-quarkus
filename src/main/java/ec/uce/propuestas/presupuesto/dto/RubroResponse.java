package ec.uce.propuestas.presupuesto.dto;

import java.math.BigDecimal;
import java.util.List;

public record RubroResponse(
        Long id,
        String item,
        String codigo,
        String descripcion,
        String unidad,
        BigDecimal cantidad,
        BigDecimal precioUnitario,
        BigDecimal precioTotal,
        Long apuId,
        List<String> alertas) {}
