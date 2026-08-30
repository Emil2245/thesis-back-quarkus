package ec.uce.propuestas.insumo.dto;

import java.util.UUID;

/**
 * Plan 07 — el {@code id} público es el {@code publicId} UUIDv7 de la base;
 * nunca el {@code BIGINT} interno.
 */
public record BaseInsumosResponse(UUID id, String nombre, String tipo, boolean archivada, long totalInsumos) {}
