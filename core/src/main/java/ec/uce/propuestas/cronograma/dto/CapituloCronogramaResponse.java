package ec.uce.propuestas.cronograma.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * Plan 030 (P-35/P-36) — nodo recursivo de la jerarquía de capítulos
 * expuesto por {@link CronogramaVistasResponse} (Gantt y Cronograma Valorizado).
 *
 * <p>Forma canónica 07-api-contract §7 Apéndice B:
 *
 * <pre>
 * CapituloCronogramaResponse { "id": "&lt;UUIDv7&gt;",
 *                              "item": "1",
 *                              "descripcion": "",
 *                              "subcapitulos": [ CapituloCronogramaResponse ],
 *                              "rubros": [ RubroCronogramaResponse ] }
 * </pre>
 *
 * <p>Los capítulos son nodos estructurales: NO llevan actividad ni
 * segmentos propios. La actividad y los segmentos derivados viven en el
 * rubro (TC-P35-01: los nodos capítulo no deben adjuntar la primera
 * actividad del primer rubro, contraviniendo la práctica observada en el
 * PDF aceptado). El orden es estable por {@code item} ascendente tanto entre
 * raíces como entre hermanos.</p>
 *
 * <p>Las identidades públicas son UUIDv7 — los FK {@code BIGINT} internos
 * nunca aparecen en la respuesta.</p>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record CapituloCronogramaResponse(
        UUID id,
        String item,
        String descripcion,
        List<CapituloCronogramaResponse> subcapitulos,
        List<RubroCronogramaResponse> rubros) {}
