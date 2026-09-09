package ec.uce.propuestas.usuario.audit.dto;

import java.time.Instant;
import java.util.UUID;

/** Normalized and validated query filters for the administrative activity log. */
public record LogActividadFiltros(UUID usuarioId, String evento, Instant desde, Instant hasta, int page, int size) {}
