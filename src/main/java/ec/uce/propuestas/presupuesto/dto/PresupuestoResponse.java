package ec.uce.propuestas.presupuesto.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * Plan 021 — read model completo del presupuesto: cabecera + árbol recursivo
 * de capítulos con sus rubros. Único endpoint que devuelve la forma completa
 * del árbol (P-28/P-29/P-30 read model).
 *
 * <p>{@code presupuestoId} es la identidad pública UUIDv7 (Plan 07 / WU-03);
 * el {@code BIGINT} interno no aparece en JSON. {@code totalGeneral} se
 * serializa como cadena decimal a escala 6 (P-30 contrato canónico: p. ej.
 * {@code "0.000000"}).</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PresupuestoResponse(
        UUID presupuestoId, Short version, boolean esVigente, String totalGeneral, List<CapituloResponse> capitulos) {}
