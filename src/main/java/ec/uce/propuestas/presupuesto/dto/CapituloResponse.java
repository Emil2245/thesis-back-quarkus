package ec.uce.propuestas.presupuesto.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * Plan 021 — read model del capítulo dentro del árbol del presupuesto.
 * Forma estable introducida en este plan. La recursión es ilimitada
 * (decisión de canonical: práctica IESS ≤ 4 niveles de profundidad, pero
 * el endpoint no acota — ver STOP (B) del plan).
 *
 * <p>{@code id} es la identidad pública UUIDv7 (Plan 07 / WU-03); el
 * {@code BIGINT} interno no aparece en JSON. El campo {@code total} se
 * serializa como cadena decimal a escala 6 (P-30 contrato canónico).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CapituloResponse(
        UUID id,
        String item,
        String descripcion,
        Short orden,
        String total,
        List<CapituloResponse> subcapitulos,
        List<RubroResponse> rubros) {}
