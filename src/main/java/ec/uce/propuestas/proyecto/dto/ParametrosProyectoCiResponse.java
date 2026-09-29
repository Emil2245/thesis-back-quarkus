package ec.uce.propuestas.proyecto.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** Project CI settings and the number of explicit APU CI overrides. */
public record ParametrosProyectoCiResponse(
        UUID proyectoId, BigDecimal porcentajeIndirecto, boolean ciIndividualHabilitado, long cantidadOverrides) {}
