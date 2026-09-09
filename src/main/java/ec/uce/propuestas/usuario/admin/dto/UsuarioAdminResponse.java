package ec.uce.propuestas.usuario.admin.dto;

import ec.uce.propuestas.usuario.Rol;
import ec.uce.propuestas.usuario.Usuario;
import java.time.Instant;
import java.util.UUID;

/**
 * Plan 034 — DTO canónico de respuesta para {@code /admin/usuarios}.
 *
 * <p>Decisión 30 del plan 034: campos canónicos mínimos decididos por el
 * acta 032 — {@code id} UUIDv7, {@code nombre}, {@code email}, {@code rol},
 * {@code activo}, {@code emailVerificado}, {@code fechaCreacion} Instant.
 *
 * <p>Sin {@code passwordHash}, sin {@code tokenHash}, sin JWT, sin
 * {@code invitacionExpiraEn} (se omite para evitar el N+1 que
 * requeriría una subconsulta por fila del listado).
 */
public record UsuarioAdminResponse(
        UUID id, String nombre, String email, Rol rol, boolean activo, boolean emailVerificado, Instant fechaCreacion) {

    public static UsuarioAdminResponse from(Usuario u) {
        return new UsuarioAdminResponse(u.publicId, u.nombre, u.email, u.rol, u.activo, u.emailVerificado, u.createdAt);
    }
}
