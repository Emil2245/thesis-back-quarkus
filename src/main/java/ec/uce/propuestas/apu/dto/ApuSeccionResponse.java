package ec.uce.propuestas.apu.dto;

import ec.uce.propuestas.motor.SeccionTipo;
import java.math.BigDecimal;
import java.util.List;

/** Sección de un APU con sus filas, agrupadas en el orden fijo M/N/O/P. */
public record ApuSeccionResponse(
        SeccionTipo tipo, Short orden, BigDecimal subtotal, List<ApuDetalleResponse> detalles) {}
