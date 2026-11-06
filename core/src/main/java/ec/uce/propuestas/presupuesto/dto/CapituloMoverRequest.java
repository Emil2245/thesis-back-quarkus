package ec.uce.propuestas.presupuesto.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Plan 022 (P-28) — body para {@code PATCH
 * /presupuestos/{presupuestoId}/capitulos/{capituloId}/mover}.
 *
 * <p>{@code parentId} es UUIDv7 dentro del mismo presupuesto; {@code null} =
 * mover a raíz. {@code orden} es obligatorio y debe estar en
 * {@code [1, hermanos+1]} dentro del padre destino (los hermanos se cuentan sin
 * el propio capítulo movido, que libera su slot); tras el movimiento se
 * renumeran TODOS los hermanos afectados para preservar contigüidad
 * {@code 1..n}.</p>
 *
 * <p>El backend rechaza con 400 {@code validacion}:
 * <ul>
 *   <li>{@code parentId == capituloId} (auto-ciclo).</li>
 *   <li>{@code parentId} es descendiente del capítulo movido.</li>
 *   <li>{@code parentId} pertenece a otro presupuesto.</li>
 *   <li>{@code orden} &lt; 1 o &gt; hermanos + 1.</li>
 * </ul>
 * </p>
 */
public record CapituloMoverRequest(
        UUID parentId, @NotNull @Min(1) Short orden) {}
