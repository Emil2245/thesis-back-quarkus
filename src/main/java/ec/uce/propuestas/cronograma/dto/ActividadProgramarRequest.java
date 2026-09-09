package ec.uce.propuestas.cronograma.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Plan 029 (P-34) — body discriminado del
 * {@code PATCH /cronogramas/{cronogramaId}/actividades/{actividadId}}.
 *
 * <p>El discriminador canónico es el campo string {@code operacion} y el universo
 * se cierra a las cuatro operaciones semánticas congeladas por el Plan 026:
 * <ul>
 *   <li>{@link Reemplazar}      — {@value #OP_REEMPLAZAR}.</li>
 *   <li>{@link Distribuir}      — {@value #OP_DISTRIBUIR}.</li>
 *   <li>{@link Mover}           — {@value #OP_MOVER}.</li>
 *   <li>{@link Redimensionar}   — {@value #OP_REDIMENSIONAR}.</li>
 * </ul>
 *
 * <p>El discriminador NO acepta alias: las cuatro etiquetas son las únicas
 * reconocidas por la frontera. Una etiqueta ausente, desconocida o nula responde
 * 400 {@code validacion} desde el parser; cualquier propiedad fuera del shape
 * aprobado también lo hace, sin fusiones silenciosas.</p>
 */
public sealed interface ActividadProgramarRequest
        permits ActividadProgramarRequest.Reemplazar,
                ActividadProgramarRequest.Distribuir,
                ActividadProgramarRequest.Mover,
                ActividadProgramarRequest.Redimensionar {

    /** Etiqueta discriminadora — debe coincidir byte a byte con la firma del cliente. */
    String operacion();

    /** Reemplazo atómico del mapa completo (mapa vac\u00edo = borrador). */
    record Reemplazar(
            @JsonProperty("operacion") String operacion,
            @JsonProperty("avancePorPeriodo") java.util.Map<String, String> avancePorPeriodo)
            implements ActividadProgramarRequest {}

    /** Distribuci\u00f3n uniforme del peso entre los per\u00edodos listados (escala 4, residual determinista). */
    record Distribuir(
            @JsonProperty("operacion") String operacion,
            @JsonProperty("periodos") java.util.List<Integer> periodos) implements ActividadProgramarRequest {}

    /** Mover un segmento m\u00e1ximo actual a una nueva posici\u00f3n desplazada por delta. */
    record Mover(
            @JsonProperty("operacion") String operacion,
            @JsonProperty("inicio") int inicio,
            @JsonProperty("fin") int fin,
            @JsonProperty("delta") int delta)
            implements ActividadProgramarRequest {}

    /** Redimensionar un segmento m\u00e1ximo actual a un nuevo rango conservando la suma del segmento. */
    record Redimensionar(
            @JsonProperty("operacion") String operacion,
            @JsonProperty("inicio") int inicio,
            @JsonProperty("fin") int fin,
            @JsonProperty("nuevoInicio") int nuevoInicio,
            @JsonProperty("nuevoFin") int nuevoFin)
            implements ActividadProgramarRequest {}

    String OP_REEMPLAZAR = "REEMPLAZAR_AVANCES";
    String OP_DISTRIBUIR = "DISTRIBUIR_UNIFORME";
    String OP_MOVER = "MOVER_SEGMENTO";
    String OP_REDIMENSIONAR = "REDIMENSIONAR_SEGMENTO";
}
