package ec.uce.propuestas.documento.exportacion;

import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Core-only service identity; never uses the user JWT signing key or propagates a user bearer token. */
@ApplicationScoped
public class CoreServiceJwt {
    @ConfigProperty(name = "documentos.service.signing-key-location")
    Optional<String> signingKeyLocation;

    @ConfigProperty(name = "mp.jwt.verify.publickey.location")
    Optional<String> userPublicKeyLocation;

    public String issueToken() {
        var serviceLocation = signingKeyLocation
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalStateException("Dedicated Documentos signing key is not configured"));
        var userLocation = userPublicKeyLocation
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new IllegalStateException("User JWT verification key is not configured"));
        return signedToken(readPrivateKey(serviceLocation), readPublicKey(userLocation), Instant.now());
    }

    static String signedToken(PrivateKey serviceKey, PublicKey userKey, Instant now) {
        // Compare the actual RSA public components, not paths: copied key files must be rejected too.
        if (!(serviceKey instanceof RSAPrivateCrtKey service) || !(userKey instanceof RSAPublicKey user)) {
            throw new IllegalStateException("Cannot verify distinct RSA service and user keys");
        }
        if (service.getModulus().equals(user.getModulus())
                && service.getPublicExponent().equals(user.getPublicExponent())) {
            throw new IllegalStateException("The Documentos signing key must differ from the user JWT key");
        }
        long issued = now.getEpochSecond();
        return Jwt.upn("core-service")
                .issuer("core")
                .audience("documentos")
                .groups("CORE_SERVICE")
                .issuedAt(issued)
                .expiresAt(issued + 60)
                .sign(serviceKey);
    }

    private static PrivateKey readPrivateKey(String location) {
        try {
            String body = readPem(location)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (Exception e) {
            throw new IllegalStateException("Dedicated Documentos signing key is unavailable");
        }
    }

    private static PublicKey readPublicKey(String location) {
        try {
            String body = readPem(location)
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(body)));
        } catch (Exception e) {
            throw new IllegalStateException("User JWT verification key is unavailable");
        }
    }

    private static String readPem(String location) throws IOException {
        if (location.startsWith("classpath:")) {
            String resource = location.substring("classpath:".length()).replaceFirst("^/", "");
            try (var input = CoreServiceJwt.class.getClassLoader().getResourceAsStream(resource)) {
                if (input == null) throw new IOException("Key resource unavailable");
                return new String(input.readAllBytes(), StandardCharsets.US_ASCII);
            }
        }
        Path path = Path.of(location.startsWith("file:") ? location.substring("file:".length()) : location);
        return Files.readString(path);
    }
}
