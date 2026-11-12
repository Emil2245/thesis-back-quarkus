package ec.uce.propuestas.insumo.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * WU-05 — Respuesta pública de una base PERSONAL. Expone la identidad externa
 * inmutable (UUIDv7) bajo el nombre semántico {@code id}; nunca el {@code id}
 * interno BIGINT ni el nombre de columna crudo {@code public_id}.
 */
public record BasePersonalResponse(
        UUID id, String nombre, boolean archivada, long totalInsumos, Instant createdAt, Instant updatedAt) {}
