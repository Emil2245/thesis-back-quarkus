package ec.uce.propuestas.presupuesto.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Plan 021 — read model del rubro dentro del árbol del presupuesto. Forma
 * estable introducida en este plan; los planes 022 (capítulos) y 023
 * (rubros) la conservan sin ampliar.
 *
 * <p>{@code id} es la identidad pública UUIDv7 (Plan 07 / WU-03);
 * el {@code BIGINT} interno no aparece en JSON. Los campos monetarios y
 * cantidades se serializan como cadena decimal a escala 6 (P-30 contrato
 * canónico).</p>
 *
 * <p>Plan 025 (P-32) NO introduce un campo {@code alertas} por rubro en este
 * read model; la validación de integridad del presupuesto se expone como tres
 * listas top-level independientes de referencias reducidas
 * ({@code RubroRefResponse}) en
 * {@code GET /presupuestos/{presupuestoId}/validacion}. Esto preserva la forma
 * estable de los consumidores existentes: no hay cambios de JSON shape.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RubroResponse(
        UUID id,
        String item,
        String codigo,
        String descripcion,
        String unidad,
        String cantidad,
        String precioUnitario,
        String precioTotal,
        UUID apuId) {}
