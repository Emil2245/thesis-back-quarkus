package ec.uce.propuestas.usuario.auth.dto;

import java.time.Instant;

public record PerfilResponse(Long id, String nombre, String email, String rol,
    Instant fechaCreacion) {}
