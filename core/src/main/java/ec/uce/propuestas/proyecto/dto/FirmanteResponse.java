package ec.uce.propuestas.proyecto.dto;

import ec.uce.propuestas.proyecto.entity.RolFirmante;
import java.util.UUID;

/**
 * Plan 07 — Identidad externa inmutable UUIDv7 (columna {@code public_id}).
 * El campo {@code id} del JSON es siempre un UUIDv7 (semántico, no {@code publicId}).
 */
public record FirmanteResponse(UUID id, String nombre, String cargo, RolFirmante rol, Short orden) {}
