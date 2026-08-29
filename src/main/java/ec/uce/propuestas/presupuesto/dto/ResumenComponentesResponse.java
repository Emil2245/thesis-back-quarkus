package ec.uce.propuestas.presupuesto.dto;

import java.math.BigDecimal;
import java.util.Map;

public record ResumenComponentesResponse(
        Map<String, BigDecimal> porComponente,
        BigDecimal totalGeneral,
        BigDecimal ivaReferencial,
        BigDecimal totalConIva) {}
