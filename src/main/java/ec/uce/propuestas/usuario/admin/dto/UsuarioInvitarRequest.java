package ec.uce.propuestas.usuario.admin.dto;

import ec.uce.propuestas.usuario.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Plan 034 — DTO de entrada para {@code POST /admin/usuarios} (invitación).
 *
 * <p>Decisión 30 del plan 034:
 * <ul>
 *   <li>{@code nombre}: 1..200 chars (no se permite contraseña temporal —
 *       la contraseña la fija el invitado al aceptar la invitación).</li>
 *   <li>{@code email}: 1..320 chars, formato RFC-5322.</li>
 *   <li>{@code rol}: USUARIO o SUPER_ADMIN (únicos roles canónicos).</li>
 * </ul>
 */
public record UsuarioInvitarRequest(
        @NotBlank @Size(max = 200) String nombre,
        @NotBlank @Email @Size(max = 320) String email,
        @NotNull Rol rol) {}
