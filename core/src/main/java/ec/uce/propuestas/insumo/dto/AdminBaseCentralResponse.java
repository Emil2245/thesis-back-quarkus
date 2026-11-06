package ec.uce.propuestas.insumo.dto;

import java.util.UUID;

/**
 * Plan 05 — Respuesta administrativa de una base CENTRAL. Expone la identidad
 * externa inmutable (UUIDv7) bajo el nombre semántico {@code id} (misma
 * convención que {@code BasePersonalResponse}); nunca el {@code id} interno
 * BIGINT. {@code tipo} siempre vale {@code "CENTRAL"} para el recurso admin.
 */
public record AdminBaseCentralResponse(UUID id, String nombre, String tipo, boolean archivada, long totalInsumos) {}
