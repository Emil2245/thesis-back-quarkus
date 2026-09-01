package ec.uce.propuestas.apu.dto;

import java.math.BigDecimal;

/**
 * Parámetros efectivos aplicados al cálculo de un APU (dossier §B.8, P-27).
 *
 * <p>Contrato: {@code hm} viene del proyecto; {@code ciDefault} del proyecto
 * (puede ser null si el proyecto no definió %CI); {@code ciAplicado} =
 * COALESCE(apu.porcentajeIndirecto, proyecto.porcentajeIndirecto), 0 si ambos
 * son null.
 *
 * <p>Plan 015 (P-24/S-24 withdrawn): el campo {@code descuento} se retira del
 * contrato JSON. El motor no aplica descuento por APU; sobrevive FORMA 1 sobre
 * las columnas base de los insumos PROYECTO (regulada por
 * {@code rango_descuento_*} en {@code ParametrosSistema}).
 */
public record ApuCalculoParametros(BigDecimal hm, BigDecimal ciDefault, BigDecimal ciAplicado) {}
