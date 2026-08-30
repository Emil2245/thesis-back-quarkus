package ec.uce.propuestas.cronograma.dto;

import java.math.BigDecimal;
import java.util.Map;

public record ActividadResponse(
        Long id,
        Long rubroId,
        String item,
        String descripcion,
        BigDecimal precioTotal,
        BigDecimal pesoPonderado,
        Map<String, BigDecimal> avancePorPeriodo,
        BigDecimal desviacion) {}
