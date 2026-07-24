package ec.uce.propuestas.usuario.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
    @NotBlank @Size(max = 200) String nombre,
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank String password,
    @NotBlank String passwordConfirmacion) {}
