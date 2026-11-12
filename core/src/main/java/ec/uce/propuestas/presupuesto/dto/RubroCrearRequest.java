package ec.uce.propuestas.presupuesto.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Plan 023 (P-29) — body para {@code POST
 * /presupuestos/{presupuestoId}/capitulos/{capituloId}/rubros}.
 *
 * <p>Reglas:
 * <ul>
 *   <li>{@code apuId} es la identidad pública UUIDv7 del APU (columna
 *       {@code apu.public_id}; nunca el {@code BIGINT} interno). Validación
 *       de UUIDv7 en frontera (Plan 07 / WU-03) vía {@code UuidV7.parse}.</li>
 *   <li>{@code cantidad} > 0 validado vía {@code @DecimalMin("0.000001")} y
 *       {@code @NotNull}. La columna BD {@code rubro.cantidad} admite
 *       {@code >= 0} desde V007 estructural (sólo para reconstrucción
 *       desde plantilla de proyecto, Plan 016), pero la API REST P-29
 *       sigue estricta: cantidad > 0 (v1.1 §2.6).</li>
 * </ul>
 *
 * <p>Errores 400 (validación):
 * <ul>
 *   <li>{@code apuId} UUID malformado o no-v7 → 400 {@code validacion}.</li>
 *   <li>{@code cantidad} ausente o ≤ 0 → 400 {@code validacion}.</li>
 *   <li>APU pertenece a OTRA versión del mismo proyecto → 400
 *       {@code validacion} (cross-version, verificado por el service).</li>
 *   <li>Capítulo pertenece a OTRO presupuesto → 400 {@code validacion}.</li>
 * </ul>
 *
 * <p>Errores 409 (estado):
 * <ul>
 *   <li>APU ya vinculado a otro rubro del mismo presupuesto (D-09) →
 *       409 {@code apu-referenciado}.</li>
 * </ul>
 */
public record RubroCrearRequest(
        @NotNull UUID apuId,
        @NotNull @DecimalMin(value = "0.000001") BigDecimal cantidad) {}
