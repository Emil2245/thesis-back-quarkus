package ec.uce.propuestas.presupuesto.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Plan 023 (P-29) — body para {@code PATCH
 * /presupuestos/{presupuestoId}/capitulos/{capituloId}/rubros/{rubroId}}.
 *
 * <p>Semántica de PATCH (Plan 023, lock): sólo cambia la cantidad. El
 * resto del rubro ({@code codigo}, {@code descripcion}, {@code unidad},
 * {@code item}, {@code apuId}) viene del APU y NO se reescribe en este
 * endpoint. Editar esos campos del APU propaga vía write-through de
 * {@code recalcular}, no vía este PATCH.</p>
 *
 * <p>{@code cantidad} requerida ({@code @NotNull}) y {@code > 0}
 * ({@code @DecimalMin("0.000001")}); cantidad ≤ 0 → 400
 * {@code validacion}.</p>
 */
public record RubroPatchRequest(
        @NotNull @DecimalMin(value = "0.000001") BigDecimal cantidad) {}
