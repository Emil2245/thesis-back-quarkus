package ec.uce.propuestas.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record UsuarioAdminEditarRequest(@NotBlank String nombre, String email, String rol) {}
