package ec.uce.propuestas.presupuesto.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Plan 024 (P-31) — body para
 * {@code POST /proyectos/{proyectoId}/presupuestos}.
 *
 * <p>{@code origenId} es la identidad pública UUIDv7 del presupuesto a clonar;
 * se valida como UUIDv7 en la frontera REST antes de invocar este DTO (un
 * UUID malformado o de versión incorrecta corta con 400 {@code validacion} sin
 * invocar al service).</p>
 *
 * <p>{@code notas} es opcional y se persiste sin generar ni normalizar texto
 * adicional.</p>
 */
public record PresupuestoVersionCrearRequest(@NotNull UUID origenId, String notas) {}
