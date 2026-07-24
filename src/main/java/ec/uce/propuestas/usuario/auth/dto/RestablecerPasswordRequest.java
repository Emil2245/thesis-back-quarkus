package ec.uce.propuestas.usuario.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RestablecerPasswordRequest(
    @NotBlank String token,
    @NotBlank String password,
    @NotBlank String passwordConfirmacion) {}
