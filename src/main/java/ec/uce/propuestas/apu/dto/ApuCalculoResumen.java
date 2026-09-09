package ec.uce.propuestas.apu.dto;

import java.math.BigDecimal;

/**
 * Resumen del desglose de un APU (dossier §B.8, P-27).
 *
 * <p>Plan 015 (P-24/S-24 withdrawn): los campos {@code cdAjustado} y
 * {@code operacionCdAjustado} se retiran del contrato. Queda
 * {@code {cd, ci, ct}} con aritmética equivalente a la identidad
 * {@code CD ≡ CD_ajustado} (descuento siempre 0): {@code CI = CD × %CI} y
 * {@code CT = CD + CI}.
 */
public record ApuCalculoResumen(BigDecimal cd, BigDecimal ci, BigDecimal ct) {}
