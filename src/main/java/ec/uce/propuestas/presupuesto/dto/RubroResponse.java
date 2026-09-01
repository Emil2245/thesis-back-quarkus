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
 * <p>El campo {@code alertas} queda diferido a Plan 025 (P-32); no se
 * introduce en este plan para evitar inventar un catálogo sin cita canónica.
 * Cuando se introduzca, se hará con {@code @JsonInclude(NON_NULL)} para
 * preservar la forma estable de los consumidores existentes.</p>
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
