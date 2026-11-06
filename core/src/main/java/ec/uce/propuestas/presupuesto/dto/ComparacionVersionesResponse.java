package ec.uce.propuestas.presupuesto.dto;

import java.util.List;

/**
 * Plan 024 (P-31) — respuesta de
 * {@code GET /presupuestos/{presupuestoId}/comparar?con=<UUIDv7>}.
 *
 * <p>El orden de las dos entradas de {@code versiones} es estable: la primera
 * corresponde al {@code presupuestoId} del path, la segunda al {@code con}.
 * Cada entrada expone su total persistido y el desglose por capítulo raíz
 * (ordenado por {@code item} ascendente).</p>
 *
 * <p>{@code totalGeneral} y los totales por capítulo se serializan como cadena
 * decimal a escala 6 (P-30 contrato canónico).</p>
 */
public record ComparacionVersionesResponse(List<ComparacionItem> versiones) {}
