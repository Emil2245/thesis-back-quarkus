package ec.uce.propuestas.admin.dto;

import java.time.Instant;

public record UsuarioAdminResponse(
        Long id, String nombre, String email, String rol, boolean emailVerificado, boolean activo, Instant createdAt) {}
