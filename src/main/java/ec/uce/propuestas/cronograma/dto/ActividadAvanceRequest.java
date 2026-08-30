package ec.uce.propuestas.cronograma.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Map;

public record ActividadAvanceRequest(@NotNull Map<String, BigDecimal> avancePorPeriodo) {}
