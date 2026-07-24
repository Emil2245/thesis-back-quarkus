package ec.uce.propuestas.usuario.auth.dto;

public record TokenResponse(String accessToken, long expiraEnSegundos,
    String refreshToken, UsuarioResponse usuario) {}
