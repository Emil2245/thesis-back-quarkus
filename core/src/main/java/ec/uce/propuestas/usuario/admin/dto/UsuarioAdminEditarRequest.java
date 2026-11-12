package ec.uce.propuestas.usuario.admin.dto;

import ec.uce.propuestas.usuario.Rol;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Plan 034 — DTO de entrada para {@code PUT /admin/usuarios/{id}}.
 *
 * <p>Decisión 30 + decisión 6 del acta 032: NO permite cambiar
 * {@code email} (cambio de email admin queda gated por acta 032; el
 * flujo canónico actual vive en {@code PUT /perfil}).
 *
 * <p>Permite modificar {@code nombre} (1..200), {@code rol} y
 * {@code activo}.
 */
public record UsuarioAdminEditarRequest(
        @NotNull @Size(max = 200) String nombre,
        @NotNull Rol rol,
        @NotNull Boolean activo) {}
