package ec.uce.propuestas.usuario.auth;

import ec.uce.propuestas.usuario.RefreshToken;
import ec.uce.propuestas.usuario.TipoToken;
import ec.uce.propuestas.usuario.TokenUsuario;
import ec.uce.propuestas.usuario.Usuario;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class TokenService {

    @ConfigProperty(name = "app.auth.refresh-ttl-recordado")
    Duration refreshTtlRecordado;

    @ConfigProperty(name = "app.auth.refresh-ttl-sesion")
    Duration refreshTtlSesion;

    @ConfigProperty(name = "app.auth.verificacion-ttl")
    Duration verificacionTtl;

    @ConfigProperty(name = "app.auth.reset-ttl")
    Duration resetTtl;

    @ConfigProperty(name = "app.auth.invitacion-ttl")
    Duration invitacionTtl;

    private static final long ACCESS_TOKEN_TTL_SECONDS = 3600L;
    private static final SecureRandom RANDOM = new SecureRandom();

    // ---------- Access token (JWT) ----------

    public String mintAccessToken(Usuario usuario) {
        return Jwt.issuer("https://propuestas-api.local")
            .upn(usuario.email)
            .groups(Set.of(usuario.rol.name()))
            .expiresIn(ACCESS_TOKEN_TTL_SECONDS)
            .sign();
    }

    // ---------- Refresh tokens ----------

    @Transactional
    public String issueRefreshToken(Usuario usuario, boolean recordarSesion) {
        String raw = generateRaw();
        String hash = sha256Hex(raw);
        Duration ttl = recordarSesion ? refreshTtlRecordado : refreshTtlSesion;

        RefreshToken rt = new RefreshToken();
        rt.usuario = usuario;
        rt.tokenHash = hash;
        rt.expiraEn = Instant.now().plus(ttl);
        rt.persist();

        return raw;
    }

    @Transactional
    public Optional<String> rotateRefreshToken(String rawOld) {
        String hashOld = sha256Hex(rawOld);
        RefreshToken old = RefreshToken.find("tokenHash", hashOld).firstResult();
        if (old == null || old.revocadoEn != null || old.expiraEn.isBefore(Instant.now())) {
            return Optional.empty();
        }
        old.revocadoEn = Instant.now();

        // Determine original TTL by expiry span; approximate with recordado TTL (best effort)
        Duration originalSpan = Duration.between(old.createdAt, old.expiraEn);
        String rawNew = generateRaw();
        RefreshToken rt = new RefreshToken();
        rt.usuario = old.usuario;
        rt.tokenHash = sha256Hex(rawNew);
        rt.expiraEn = Instant.now().plus(originalSpan);
        rt.persist();

        return Optional.of(rawNew);
    }

    @Transactional
    public void revokeRefreshToken(String rawToken) {
        String hash = sha256Hex(rawToken);
        RefreshToken rt = RefreshToken.find("tokenHash", hash).firstResult();
        if (rt != null && rt.revocadoEn == null) {
            rt.revocadoEn = Instant.now();
        }
    }

    @Transactional
    public void revokeAllRefreshTokensForUser(Long usuarioId) {
        RefreshToken.update(
            "revocadoEn = ?1 WHERE usuario.id = ?2 AND revocadoEn IS NULL",
            Instant.now(), usuarioId);
    }

    public Optional<Usuario> findUsuarioByRefreshToken(String rawToken) {
        String hash = sha256Hex(rawToken);
        RefreshToken rt = RefreshToken.find("tokenHash", hash).firstResult();
        if (rt == null || rt.revocadoEn != null || rt.expiraEn.isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(rt.usuario);
    }

    // ---------- One-time tokens ----------

    @Transactional
    public String issueOneTimeToken(Usuario usuario, TipoToken tipo, Duration ttl, String emailDestino) {
        String raw = generateRaw();
        String hash = sha256Hex(raw);

        TokenUsuario tu = new TokenUsuario();
        tu.usuario = usuario;
        tu.tipo = tipo;
        tu.tokenHash = hash;
        tu.emailDestino = emailDestino;
        tu.expiraEn = Instant.now().plus(ttl);
        tu.persist();

        // Never log the raw token value — log only the event
        return raw;
    }

    public Duration verificacionTtl() { return verificacionTtl; }
    public Duration resetTtl() { return resetTtl; }
    public Duration invitacionTtl() { return invitacionTtl; }

    @Transactional
    public Long consumeOneTimeToken(String rawToken, TipoToken expectedTipo) {
        String hash = sha256Hex(rawToken);
        TokenUsuario tu = TokenUsuario.find("tokenHash", hash).firstResult();
        if (tu == null
            || tu.tipo != expectedTipo
            || tu.expiraEn.isBefore(Instant.now())
            || tu.usadoEn != null) {
            return null;
        }
        tu.usadoEn = Instant.now();
        return tu.usuario.id;
    }

    /** Returns the most recent TokenUsuario for the given user/type (may be null). */
    public TokenUsuario findLatestToken(Long usuarioId, TipoToken tipo) {
        return TokenUsuario.find(
            "usuario.id = ?1 AND tipo = ?2 ORDER BY createdAt DESC",
            usuarioId, tipo).firstResult();
    }

    // ---------- Utilities ----------

    private static String generateRaw() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String raw) {
        try {
            var md = MessageDigest.getInstance("SHA-256");
            var bytes = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder(64);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public long accessTokenTtlSeconds() {
        return ACCESS_TOKEN_TTL_SECONDS;
    }
}
