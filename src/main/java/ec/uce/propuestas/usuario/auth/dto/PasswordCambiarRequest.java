package ec.uce.propuestas.usuario.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordCambiarRequest(
    @NotBlank String passwordActual,
    @NotBlank String passwordNueva,
    @NotBlank String passwordConfirmacion) {}
