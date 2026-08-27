package ec.uce.propuestas.apu.dto;

import ec.uce.propuestas.motor.SeccionTipo;
import java.math.BigDecimal;

/**
 * Una línea del desglose de cálculo de un APU (dossier §B.8, P-27).
 *
 * <p>{@code operacion} es una cadena legible que describe los operandos
 * aplicados a precisión de 6 dp (auditoría). {@code resultado} es el costo
 * final de la fila a 6 dp. Los campos restantes exponen la trazabilidad
 * mínima (id, orden, descripción, sección, insumoId) sin filtrar tipos del
 * motor.
 */
public record ApuCalculoLinea(
        Long detalleId,
        Short orden,
        SeccionTipo seccion,
        boolean esHerramientaMenor,
        Long insumoId,
        String descripcion,
        BigDecimal cantidad,
        BigDecimal rendimiento,
        BigDecimal precioEfectivo,
        BigDecimal costoHora,
        String operacion,
        BigDecimal resultado) {}
