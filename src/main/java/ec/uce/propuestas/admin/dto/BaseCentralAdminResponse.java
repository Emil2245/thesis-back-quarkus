package ec.uce.propuestas.admin.dto;

import java.time.Instant;

public record BaseCentralAdminResponse(Long id, String nombre, boolean archivada, Instant createdAt) {}
