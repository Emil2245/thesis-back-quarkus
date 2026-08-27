package ec.uce.propuestas.apu.dto;

import java.math.BigDecimal;

/**
 * Resumen del desglose de un APU (dossier §B.8, P-27).
 * {@code operacionCdAjustado} describe el descuento aplicado a {@code cd}
 * para producir {@code cdAjustado} (a precisión 6 dp).
 */
public record ApuCalculoResumen(
        BigDecimal cd, BigDecimal cdAjustado, String operacionCdAjustado, BigDecimal ci, BigDecimal ct) {}
