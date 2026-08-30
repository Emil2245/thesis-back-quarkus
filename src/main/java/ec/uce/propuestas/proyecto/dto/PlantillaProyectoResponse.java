package ec.uce.propuestas.proyecto.dto;

import java.time.Instant;

public record PlantillaProyectoResponse(Long id, String nombre, String descripcion, Instant createdAt) {}
