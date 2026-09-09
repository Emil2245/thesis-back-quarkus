package ec.uce.propuestas.common.config;

/**
 * Plan 014 — Contrato JSON público del endpoint
 * {@code GET /api/v1/config/display}.
 *
 * <p>Los nombres de los campos del JSON <b>no</b> coinciden con los nombres
 * de la configuración (deliberado): {@code app.display.precision} →
 * {@code precisionDinero}; {@code app.display.precision-porcentaje} →
 * {@code precisionPorcentaje}. El resource es el único punto de traducción.
 */
public record DisplayConfigResponse(int precisionDinero, int precisionPorcentaje) {}
