package ec.uce.propuestas.presupuesto.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Plan 021 — versión de presupuesto dentro de un proyecto. Lista plana para
 * {@code GET /proyectos/{proyectoId}/presupuestos}. El {@code id} es la
 * identidad pública UUIDv7; el {@code BIGINT} interno nunca aparece en JSON.
 *
 * <p>Los campos monetarios se serializan como cadena decimal a escala 6
 * (P-30 contrato canónico: p. ej. {@code "0.000000"}) para evitar
 * pérdida de precisión del transporte JSON numérico.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PresupuestoVersionResponse(
        UUID presupuestoId,
        Short version,
        boolean esVigente,
        UUID origenId,
        String notas,
        Instant fechaCreacion,
        String totalGeneral) {}
