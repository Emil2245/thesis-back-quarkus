package ec.uce.propuestas.apu.dto;

import java.math.BigDecimal;

/**
 * Parámetros efectivos aplicados al cálculo de un APU (dossier §B.8, P-27).
 *
 * <p>Contrato: {@code hm} viene del proyecto; {@code ciDefault} del proyecto
 * (puede ser null si el proyecto no definió %CI); {@code ciAplicado} =
 * COALESCE(apu.porcentajeIndirecto, proyecto.porcentajeIndirecto), 0 si ambos
 * son null; {@code descuento} = apu.porcentajeDescuento (0 si null).
 */
public record ApuCalculoParametros(BigDecimal hm, BigDecimal ciDefault, BigDecimal ciAplicado, BigDecimal descuento) {}
