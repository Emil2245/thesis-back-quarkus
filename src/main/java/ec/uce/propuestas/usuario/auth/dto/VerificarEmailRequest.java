package ec.uce.propuestas.usuario.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record VerificarEmailRequest(@NotBlank String token) {}
