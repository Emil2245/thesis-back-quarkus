package ec.uce.propuestas.apu.dto;

import ec.uce.propuestas.motor.SeccionTipo;
import java.math.BigDecimal;
import java.util.List;

/**
 * Sección (bloque M/N/O/P) del desglose de un APU (dossier §B.8, P-27).
 * {@code operacion} resume la suma de las líneas a 6 dp;
 * {@code resultado} = subtotal de la sección a 6 dp.
 */
public record ApuCalculoSeccion(
        SeccionTipo tipo, BigDecimal subtotal, String operacion, BigDecimal resultado, List<ApuCalculoLinea> lineas) {}
