package ec.uce.propuestas.usuario.admin;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.usuario.Rol;
import ec.uce.propuestas.usuario.TipoToken;
import ec.uce.propuestas.usuario.Usuario;
import ec.uce.propuestas.usuario.UsuarioRepository;
import ec.uce.propuestas.usuario.audit.EventoLogActividad;
import ec.uce.propuestas.usuario.audit.service.LogActividadService;
import ec.uce.propuestas.usuario.auth.PasswordService;
import ec.uce.propuestas.usuario.auth.TokenService;
import ec.uce.propuestas.usuario.auth.mail.EnviadorCorreo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plan 034 — Servicio administrativo del ciclo de vida de usuarios
 * (P-38 / US-35 / TC-P38-01..03).
 *
 * <p>Contrato exacto del acta 032 D-04 (decisión 31 del plan):
 * <ul>
 *   <li>32 bytes aleatorios del {@link SecureRandom} canónico de la JVM;</li>
 *   <li>codificación Base64URL sin padding
 *       ({@link java.util.Base64#getUrlEncoder()} con {@code withoutPadding()});</li>
 *   <li>hash bcrypt único a través del {@link PasswordService} existente;</li>
 *   <li>descarta inmediato del {@code byte[]} y del {@code String} Base64URL
 *       tras obtener el hash; solo persiste {@code passwordHash};</li>
 *   <li>el algoritmo se ejecuta **únicamente** al crear usuarios invitados;
 *       no se regenera en login, logout, cambio de contraseña, ni en ningún
 *       otro flujo posterior.</li>
 *   <li>el token de invitación 72 h viaja por el {@link TokenService}
 *       existente ({@code SHA-256}, TTL {@code PT72H},
 *       {@link TipoToken#INVITACION}); el token y la contraseña temporal
 *       son **dos secretos independientes**.</li>
 *   <li>sin helper {@code RandomUtil} nuevo.</li>
 * </ul>
 *
 * <p>Emisión D-13 (decisión 32) dentro de la misma transacción
 * exterior ({@code MANDATORY}):
 * <ul>
 *   <li>{@code usuario.invitado} en {@link #invitar};</li>
 *   <li>{@code usuario.desactivado} en {@link #desactivar};</li>
 *   <li>{@code usuario.activado} en {@link #reactivar}.</li>
 * </ul>
 */
@ApplicationScoped
public class UsuarioAdminService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int BYTES_ALEATORIOS = 32;

    @Inject
    UsuarioRepository usuarioRepo;

    @Inject
    PasswordService passwordService;

    @Inject
    TokenService tokenService;

    @Inject
    EnviadorCorreo enviadorCorreo;

    @Inject
    LogActividadService logActividadService;

    // =========================================================================
    // Invitación (TC-P38-01)
    // =========================================================================

    /**
     * Invita a un nuevo usuario:
     * <ol>
     *   <li>verifica que el email no esté registrado;</li>
     *   <li>crea el {@link Usuario} con {@code passwordHash} inutilizable
     *       (contrato D-04 del acta 032) y {@code activo = true},
     *       {@code emailVerificado = false};</li>
     *   <li>emite el token de invitación 72 h por el {@link TokenService};</li>
     *   <li>envía el correo con el link de aceptación;</li>
     *   <li>emite {@code usuario.invitado} dentro de la misma transacción.</li>
     * </ol>
     */
    @Transactional
    public Usuario invitar(String nombre, String email, Rol rol) {
        if (usuarioRepo.isEmailTaken(email)) {
            throw ProblemaException.conflicto("email-ya-registrado", "El correo ya está registrado");
        }

        String passwordHashInutilizable = generarPasswordHashInutilizable();

        Usuario u = new Usuario();
        u.nombre = nombre;
        u.email = email;
        u.passwordHash = passwordHashInutilizable;
        u.rol = rol;
        u.activo = true;
        u.emailVerificado = false;
        usuarioRepo.persist(u);
        usuarioRepo.flush(); // Garantiza que publicId se materializa antes del token.

        Instant expiresAt = Instant.now().plus(tokenService.invitacionTtl());
        String rawToken =
                tokenService.issueOneTimeToken(u, TipoToken.INVITACION, tokenService.invitacionTtl(), u.email);
        enviadorCorreo.enviarInvitacion(u.email, rawToken, expiresAt);

        // Emisión D-13 (decisión 32) — dentro de la misma @Transactional exterior.
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("tokenExpiraEn", expiresAt.toString());
        logActividadService.emitir(null, EventoLogActividad.USUARIO_INVITADO, "usuario", u.publicId, detalle);

        return u;
    }

    /**
     * Genera el hash bcrypt inicial inutilizable.
     *
     * <p>Implementa **exactamente** el contrato del acta 032 D-04:
     * <ol>
     *   <li>32 bytes aleatorios del {@link SecureRandom} canónico;</li>
     *   <li>codificación Base64URL sin padding como portador efímero
     *       hacia el hash (no se persiste, no se loguea, no se devuelve,
     *       no se envía por correo);</li>
     *   <li>un único hash bcrypt vía el {@link PasswordService} inyectado;</li>
     *   <li>descarte inmediato del {@code byte[]} tras el hash; solo
     *       persiste {@code passwordHash}. El {@code String} Base64URL
     *       queda fuera de alcance al retornar del método (GC).</li>
     * </ol>
     */
    private String generarPasswordHashInutilizable() {
        byte[] bytesAleatorios = new byte[BYTES_ALEATORIOS];
        SECURE_RANDOM.nextBytes(bytesAleatorios);
        String portadorEfimero = Base64.getUrlEncoder().withoutPadding().encodeToString(bytesAleatorios);
        try {
            return passwordService.hash(portadorEfimero);
        } finally {
            // Descarte inmediato del byte[] (best-effort: el GC limpiará al
            // salir del scope, pero sobreescribimos para no dejar el patrón
            // en el heap logueable).
            java.util.Arrays.fill(bytesAleatorios, (byte) 0);
        }
    }

    // =========================================================================
    // PUT edición (sin email — decisión 6 del acta 032)
    // =========================================================================

    @Transactional
    public Usuario editar(UUID publicId, String nombre, Rol rol, boolean activo) {
        Usuario u = usuarioRepo
                .findByPublicId(publicId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario no encontrado"));
        u.nombre = nombre;
        u.rol = rol;
        u.activo = activo;
        return u;
    }

    // =========================================================================
    // Desactivar / reactivar (TC-P38-02)
    // =========================================================================

    @Transactional
    public Usuario desactivar(UUID publicId) {
        Usuario u = usuarioRepo
                .findByPublicId(publicId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario no encontrado"));
        if (u.activo) {
            u.activo = false;
            Map<String, Object> detalle = new LinkedHashMap<>();
            detalle.put("origen", "admin");
            logActividadService.emitir(null, EventoLogActividad.USUARIO_DESACTIVADO, "usuario", u.publicId, detalle);
        }
        return u;
    }

    @Transactional
    public Usuario reactivar(UUID publicId) {
        Usuario u = usuarioRepo
                .findByPublicId(publicId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario no encontrado"));
        if (!u.activo) {
            u.activo = true;
            Map<String, Object> detalle = new LinkedHashMap<>();
            detalle.put("origen", "admin");
            logActividadService.emitir(null, EventoLogActividad.USUARIO_ACTIVADO, "usuario", u.publicId, detalle);
        }
        return u;
    }

    // =========================================================================
    // DELETE (TC-P38-03) — FK RESTRICT → 409 usuario-con-proyectos-impedido
    // =========================================================================

    /**
     * Elimina un usuario. Si tiene proyectos propios (FK RESTRICT V001 §3),
     * captura la excepción de Hibernate/Postgres y la mapea a
     * {@link ProblemaException} 409 con código
     * {@code usuario-con-proyectos-impedido} (decisión 20 del acta 032).
     *
     * <p>Decisiones diferidas explícitamente a I-12 (no se codifican aquí):
     * self-delete, last-active SUPER_ADMIN, cambio de email admin y
     * bootstrap del primer SUPER_ADMIN.
     */
    @Transactional
    public void eliminar(UUID publicId) {
        Usuario u = usuarioRepo
                .findByPublicId(publicId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario no encontrado"));
        try {
            usuarioRepo.delete(u);
            usuarioRepo.flush();
        } catch (RuntimeException ex) {
            if (esViolacionForeignKey(ex)) {
                throw ProblemaException.conflicto(
                        "usuario-con-proyectos-impedido", "El usuario tiene proyectos propios y no puede eliminarse");
            }
            throw ex;
        }
    }

    /**
     * Recorre la cadena de causas buscando la señal de FK RESTRICT violada.
     * PostgreSQL emite SQLSTATE 23503 y mensajes como
     * {@code "violates foreign key constraint"} o
     * {@code "fk_proyecto_usuario"}; Hibernate los envuelve en
     * {@link PersistenceException} → {@link org.hibernate.exception.ConstraintViolationException}.
     */
    private static boolean esViolacionForeignKey(Throwable t) {
        Throwable cursor = t;
        while (cursor != null) {
            if (cursor instanceof org.hibernate.exception.ConstraintViolationException) {
                return true;
            }
            String msg = cursor.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("violates foreign key constraint")
                        || lower.contains("foreign key") && lower.contains("usuario")
                        || lower.contains("fk_proyecto_usuario")) {
                    return true;
                }
            }
            if (cursor instanceof java.sql.SQLException sql) {
                String state = sql.getSQLState();
                if ("23503".equals(state)) {
                    return true;
                }
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    // =========================================================================
    // Listado paginado (GET /admin/usuarios)
    // =========================================================================

    @Transactional
    public List<Usuario> listar(String q, Boolean activo, int page, int size) {
        return usuarioRepo.listar(q, activo, page, size);
    }

    public long contar(String q, Boolean activo) {
        return usuarioRepo.contar(q, activo);
    }

    /**
     * Lectura individual por {@code publicId} UUIDv7. Lanza
     * {@link ProblemaException} 404 cuando no existe.
     */
    public Usuario buscarPorPublicId(UUID publicId) {
        return usuarioRepo
                .findByPublicId(publicId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Usuario no encontrado"));
    }
}
