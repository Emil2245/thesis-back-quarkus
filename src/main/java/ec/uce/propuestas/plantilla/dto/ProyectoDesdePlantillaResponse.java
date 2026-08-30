package ec.uce.propuestas.plantilla.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import ec.uce.propuestas.proyecto.dto.ProyectoResponse;
import java.util.List;

/**
 * Plan 06 (P-46, N04 §A8) — Respuesta del endpoint
 * {@code POST /proyectos/desde-plantilla/{plantillaId}}. Combina el
 * {@link ProyectoResponse} del proyecto recién creado (BORRADOR) con la lista
 * de advertencias (mismas que {@code POST /presupuestos/{id}/apus}).
 * No debilita la forma existente de {@link ProyectoResponse}: el campo
 * {@code advertencias} es opcional y sólo aparece cuando hay faltantes.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProyectoDesdePlantillaResponse(
        ProyectoResponse proyecto, List<AdvertenciaPlantillaResponse> advertencias) {

    public boolean tieneAdvertencias() {
        return advertencias != null && !advertencias.isEmpty();
    }
}
