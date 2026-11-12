package ec.uce.propuestas.cronograma.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Plan 030 — bloque de curva S dentro de la respuesta única de
 * {@code GET /cronogramas/{id}/vistas}. Usa la MISMA proyección que el
 * cronograma valorizado (Tabla §DM-16); no recalcula ni acepta puntos del
 * cliente.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CurvaSResponse(List<PuntoCurvaSResponse> puntos) {}
