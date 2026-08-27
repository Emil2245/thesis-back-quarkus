package ec.uce.propuestas.apu.dto;

import java.util.List;

/**
 * Response del endpoint {@code GET /apus/{apuId}/calculo} (dossier §B.8, P-27).
 * Proyecta el cálculo del APU en cuatro bloques canónicos M/N/O/P con sus
 * líneas, parámetros efectivos aplicados y resumen CD / CD ajustado / CI / CT.
 *
 * <p>El cálculo se delega a {@code Motor.calcularApu} (puro). Esta proyección
 * no recalcula ni persiste: solo lee el estado actual del APU.
 */
public record ApuCalculoResponse(
        Long apuId,
        String codigo,
        ApuCalculoParametros parametros,
        List<ApuCalculoSeccion> secciones,
        ApuCalculoResumen resumen) {}
