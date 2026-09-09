package ec.uce.propuestas.insumo.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Plan 07 — los identificadores públicos son UUIDv7 (identidad externa
 * inmutable, columna {@code public_id}). El parse/validación contra el formato
 * UUIDv7 se hace en el resource; este DTO sólo fija la no-nullabilidad.
 *
 * <p>{@code fuenteTipo} discrimina el origen de la copia
 * ({@code CENTRAL} | {@code PROYECTO}). Para {@code CENTRAL}, {@code baseId}
 * apunta al {@code publicId} UUIDv7 de la base central. Para {@code PROYECTO},
 * apunta al {@code publicId} UUIDv7 del proyecto fuente (la copia materializa
 * desde su base PROYECTO). El {@code proyectoId} destino es siempre el
 * {@code publicId} UUIDv7 del proyecto del caller.</p>
 */
public record CopiarBaseRequest(
        @NotNull String fuenteTipo, // CENTRAL | PROYECTO
        UUID baseId,
        UUID proyectoId) {}
