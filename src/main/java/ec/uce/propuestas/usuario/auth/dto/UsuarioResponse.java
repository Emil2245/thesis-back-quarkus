package ec.uce.propuestas.usuario.auth.dto;

public record UsuarioResponse(Long id, String nombre, String email, String rol,
    boolean emailVerificado) {}
