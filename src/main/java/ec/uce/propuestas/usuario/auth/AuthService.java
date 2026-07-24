package ec.uce.propuestas.usuario.auth;

import ec.uce.propuestas.common.ErrorPayload;
import ec.uce.propuestas.usuario.*;
import ec.uce.propuestas.usuario.auth.dto.*;
import ec.uce.propuestas.usuario.auth.mail.EnviadorCorreo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class AuthService {

    private static final Logger LOG = Logger.getLogger(AuthService.class);

    // Canned dummy hash used for constant-time defence when email is unknown
    private static final String DUMMY_HASH =
        "$2a$10$wJ7c.T3JB0kV5iZz8Q1wnOgSbYbHHcFd3KaJq8XtsMgRm5Y9GNMhO";

    @Inject UsuarioRepository usuarioRepo;
    @Inject PasswordService passwordService;
    @Inject TokenService tokenService;
    @Inject EnviadorCorreo enviadorCorreo;

    // -----------------------------------------------------------------------
    // Registration / verification
    // -----------------------------------------------------------------------

    @Transactional
    public UsuarioResponse registrar(RegistroRequest req) {
        if (!req.password().equals(req.passwordConfirmacion())) {
            throw error(400, "validacion", "Las contraseñas no coinciden");
        }
        if (!PasswordPolicy.isValid(req.password())) {
            throw error(400, "validacion",
                "La contraseña debe tener al menos 8 caracteres, una letra y un número");
        }
        if (usuarioRepo.isEmailTaken(req.email())) {
            throw error(400, "validacion", "El correo ya está registrado");
        }

        Usuario u = new Usuario();
        u.nombre = req.nombre();
        u.email = req.email();
        u.passwordHash = passwordService.hash(req.password());
        usuarioRepo.persist(u);

        Instant expiresAt = Instant.now().plus(tokenService.verificacionTtl());
        String raw = tokenService.issueOneTimeToken(
            u, TipoToken.VERIFICACION_EMAIL, tokenService.verificacionTtl(), u.email);
        enviadorCorreo.enviarVerificacion(u.email, raw, expiresAt);
        LOG.infof("Registro: usuario id=%d, email enviado", u.id);

        return toUsuarioResponse(u);
    }

    @Transactional
    public void verificarEmail(String rawToken) {
        Long uid = tokenService.consumeOneTimeToken(rawToken, TipoToken.VERIFICACION_EMAIL);
        if (uid == null) {
            throw error(410, "token-invalido-o-expirado", "Token inválido o expirado");
        }
        Usuario u = usuarioRepo.findById(uid);
        u.emailVerificado = true;
        LOG.infof("Email verificado: usuario id=%d", uid);
    }

    @Transactional
    public void reenviarVerificacion(String email, Duration cooldown) {
        var optUser = usuarioRepo.findByEmail(email);
        if (optUser.isEmpty()) {
            // Anti-enumeration: just return 202 silently; caller sends 202
            return;
        }
        Usuario u = optUser.get();
        if (u.emailVerificado) {
            return; // already verified — silently ignore
        }

        var latest = tokenService.findLatestToken(u.id, TipoToken.VERIFICACION_EMAIL);
        if (latest != null && latest.createdAt.isAfter(Instant.now().minus(cooldown))) {
            throw error(429, "cooldown-activo",
                "Debe esperar antes de reenviar el correo de verificación");
        }

        Instant expiresAt = Instant.now().plus(tokenService.verificacionTtl());
        String raw = tokenService.issueOneTimeToken(
            u, TipoToken.VERIFICACION_EMAIL, tokenService.verificacionTtl(), u.email);
        enviadorCorreo.enviarVerificacion(u.email, raw, expiresAt);
        LOG.infof("Verificación reenviada: usuario id=%d", u.id);
    }

    // -----------------------------------------------------------------------
    // Login / refresh / logout
    // -----------------------------------------------------------------------

    @Transactional
    public TokenResponse login(LoginRequest req) {
        var optUser = usuarioRepo.findByEmail(req.email());

        if (optUser.isEmpty()) {
            // Constant-time defence: run a dummy verify
            passwordService.verify(req.password(), DUMMY_HASH);
            throw error(401, "credenciales-invalidas", "Correo o contraseña incorrectos");
        }

        Usuario u = optUser.get();
        if (!passwordService.verify(req.password(), u.passwordHash)) {
            throw error(401, "credenciales-invalidas", "Correo o contraseña incorrectos");
        }
        if (!u.emailVerificado) {
            throw error(403, "email-no-verificado", "El correo no ha sido verificado");
        }
        if (!u.activo) {
            throw error(403, "cuenta-desactivada", "La cuenta está desactivada");
        }

        String accessToken = tokenService.mintAccessToken(u);
        String refreshRaw = tokenService.issueRefreshToken(u, req.recordarSesion());
        LOG.infof("Login exitoso: usuario id=%d", u.id);

        return new TokenResponse(
            accessToken,
            tokenService.accessTokenTtlSeconds(),
            refreshRaw,
            toUsuarioResponse(u));
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        var optUser = tokenService.findUsuarioByRefreshToken(rawRefreshToken);
        if (optUser.isEmpty()) {
            throw error(401, "token-invalido-o-expirado", "Refresh token inválido o expirado");
        }
        Usuario u = optUser.get();

        // Rotate: revoke old, issue new
        var optNewRaw = tokenService.rotateRefreshToken(rawRefreshToken);
        if (optNewRaw.isEmpty()) {
            throw error(401, "token-invalido-o-expirado", "Refresh token inválido o expirado");
        }

        String newAccessToken = tokenService.mintAccessToken(u);
        LOG.infof("Token renovado: usuario id=%d", u.id);

        return new TokenResponse(
            newAccessToken,
            tokenService.accessTokenTtlSeconds(),
            optNewRaw.get(),
            toUsuarioResponse(u));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        tokenService.revokeRefreshToken(rawRefreshToken);
        LOG.info("Logout: refresh token revocado");
    }

    // -----------------------------------------------------------------------
    // Password recovery
    // -----------------------------------------------------------------------

    @Transactional
    public void iniciarRecuperacion(String email) {
        var optUser = usuarioRepo.findByEmail(email);
        if (optUser.isEmpty()) {
            // Anti-enumeration: do nothing, caller always returns 202
            return;
        }
        Usuario u = optUser.get();
        Instant expiresAt = Instant.now().plus(tokenService.resetTtl());
        String raw = tokenService.issueOneTimeToken(
            u, TipoToken.RESET_PASSWORD, tokenService.resetTtl(), u.email);
        enviadorCorreo.enviarReset(u.email, raw, expiresAt);
        LOG.infof("Recuperación iniciada: usuario id=%d", u.id);
    }

    @Transactional
    public void restablecerPassword(RestablecerPasswordRequest req) {
        if (!req.password().equals(req.passwordConfirmacion())) {
            throw error(400, "validacion", "Las contraseñas no coinciden");
        }
        if (!PasswordPolicy.isValid(req.password())) {
            throw error(400, "validacion",
                "La contraseña debe tener al menos 8 caracteres, una letra y un número");
        }

        Long uid = tokenService.consumeOneTimeToken(req.token(), TipoToken.RESET_PASSWORD);
        if (uid == null) {
            throw error(410, "token-invalido-o-expirado", "Token inválido o expirado");
        }

        Usuario u = usuarioRepo.findById(uid);
        u.passwordHash = passwordService.hash(req.password());
        // D-03: revoke all refresh tokens on password change
        tokenService.revokeAllRefreshTokensForUser(uid);
        LOG.infof("Password restablecido: usuario id=%d", uid);
    }

    // -----------------------------------------------------------------------
    // Invitation acceptance
    // -----------------------------------------------------------------------

    @Transactional
    public void aceptarInvitacion(AceptarInvitacionRequest req) {
        if (!req.password().equals(req.passwordConfirmacion())) {
            throw error(400, "validacion", "Las contraseñas no coinciden");
        }
        if (!PasswordPolicy.isValid(req.password())) {
            throw error(400, "validacion",
                "La contraseña debe tener al menos 8 caracteres, una letra y un número");
        }

        Long uid = tokenService.consumeOneTimeToken(req.token(), TipoToken.INVITACION);
        if (uid == null) {
            throw error(410, "token-invalido-o-expirado", "Token de invitación inválido o expirado");
        }

        Usuario u = usuarioRepo.findById(uid);
        u.passwordHash = passwordService.hash(req.password());
        u.emailVerificado = true;  // invitation implies admin vouched for the email
        u.activo = true;
        LOG.infof("Invitación aceptada: usuario id=%d", uid);
    }

    // -----------------------------------------------------------------------
    // Profile operations (called from PerfilResource)
    // -----------------------------------------------------------------------

    public PerfilResponse leerPerfil(String email) {
        var u = usuarioRepo.findByEmail(email)
            .orElseThrow(() -> error(404, "no-encontrado", "Usuario no encontrado"));
        return new PerfilResponse(u.id, u.nombre, u.email, u.rol.name(), u.createdAt);
    }

    @Transactional
    public PerfilResponse actualizarPerfil(String currentEmail, PerfilActualizarRequest req) {
        var u = usuarioRepo.findByEmail(currentEmail)
            .orElseThrow(() -> error(404, "no-encontrado", "Usuario no encontrado"));

        boolean emailChanged = !req.email().equalsIgnoreCase(currentEmail);
        if (emailChanged && usuarioRepo.isEmailTaken(req.email())) {
            throw error(400, "validacion", "El correo ya está en uso");
        }

        u.nombre = req.nombre();
        if (emailChanged) {
            // D-03: New email requires re-verification; account keeps operating with old email
            Instant expiresAt = Instant.now().plus(tokenService.verificacionTtl());
            String raw = tokenService.issueOneTimeToken(
                u, TipoToken.CAMBIO_EMAIL, tokenService.verificacionTtl(), req.email());
            enviadorCorreo.enviarVerificacion(req.email(), raw, expiresAt);
            LOG.infof("Cambio de email iniciado: usuario id=%d", u.id);
        }

        return new PerfilResponse(u.id, u.nombre, u.email, u.rol.name(), u.createdAt);
    }

    @Transactional
    public void cambiarPassword(String email, PasswordCambiarRequest req) {
        if (!req.passwordNueva().equals(req.passwordConfirmacion())) {
            throw error(400, "validacion", "Las contraseñas no coinciden");
        }
        if (!PasswordPolicy.isValid(req.passwordNueva())) {
            throw error(400, "validacion",
                "La contraseña debe tener al menos 8 caracteres, una letra y un número");
        }

        var u = usuarioRepo.findByEmail(email)
            .orElseThrow(() -> error(404, "no-encontrado", "Usuario no encontrado"));

        if (!passwordService.verify(req.passwordActual(), u.passwordHash)) {
            throw error(401, "credenciales-invalidas", "Contraseña actual incorrecta");
        }

        u.passwordHash = passwordService.hash(req.passwordNueva());
        // D-03: revoke all refresh tokens on password change
        tokenService.revokeAllRefreshTokensForUser(u.id);
        LOG.infof("Password cambiado: usuario id=%d", u.id);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static UsuarioResponse toUsuarioResponse(Usuario u) {
        return new UsuarioResponse(u.id, u.nombre, u.email, u.rol.name(), u.emailVerificado);
    }

    private static WebApplicationException error(int status, String codigo, String mensaje) {
        var payload = new ErrorPayload(codigo, mensaje);
        return new WebApplicationException(
            Response.status(status).entity(payload).build());
    }
}
