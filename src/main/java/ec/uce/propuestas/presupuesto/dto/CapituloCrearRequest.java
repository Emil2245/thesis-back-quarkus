package ec.uce.propuestas.presupuesto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Plan 022 (P-28) — body para {@code POST /presupuestos/{presupuestoId}/capitulos}.
 *
 * <p>El backend calcula el {@code item} y el {@code orden} efectivo (este campo
 * es opcional). La regla «{@code orden} omitido = append al final» vive en
 * {@code CapituloService}.</p>
 *
 * <p>{@code parentId} es la identidad pública UUIDv7 del padre dentro del mismo
 * presupuesto; {@code null} = crear raíz. Una UUID que pertenece a otro
 * presupuesto del mismo owner se traduce en 400 {@code validacion}; una UUID
 * inexistente o ajena, en 404 {@code no-encontrado} (RNF-05: nunca 403).</p>
 */
public record CapituloCrearRequest(
        @NotBlank @Size(max = 255) String descripcion, UUID parentId, Short orden) {}
