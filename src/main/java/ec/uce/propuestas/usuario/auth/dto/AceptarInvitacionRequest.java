package ec.uce.propuestas.usuario.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record AceptarInvitacionRequest(
        @NotBlank String token,
        @NotBlank String password,
        @NotBlank String passwordConfirmacion,

        @AssertTrue(message = "Debe aceptar la política de privacidad")
        boolean aceptaPolitica) {}
