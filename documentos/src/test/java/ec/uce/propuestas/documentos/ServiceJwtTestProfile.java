package ec.uce.propuestas.documentos;

import io.quarkus.test.junit.QuarkusTestProfile;
import io.smallrye.jwt.build.Jwt;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/** Ephemeral test-only keys; neither service identity nor private key is committed. */
public class ServiceJwtTestProfile implements QuarkusTestProfile {
    private static final KeyPair CORE_KEY = generateKey();
    private static final KeyPair USER_KEY = generateKey();

    @Override
    public Map<String, String> getConfigOverrides() {
        String encoded = Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(CORE_KEY.getPublic().getEncoded());
        try {
            Path publicKey = Path.of("build/test-jwt/service-public.pem").toAbsolutePath();
            Files.createDirectories(publicKey.getParent());
            Files.writeString(publicKey, "-----BEGIN PUBLIC KEY-----\n" + encoded + "\n-----END PUBLIC KEY-----\n");
            return Map.of("mp.jwt.verify.publickey.location", publicKey.toUri().toString());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to write ephemeral public test key", e);
        }
    }

    static String token(String issuer, String audience, Set<String> roles, long expiresAt, boolean userKey) {
        PrivateKey key = userKey ? USER_KEY.getPrivate() : CORE_KEY.getPrivate();
        return Jwt.upn("core-service")
                .issuer(issuer)
                .audience(audience)
                .groups(roles)
                .expiresAt(expiresAt)
                .sign(key);
    }

    static String serviceToken() {
        return token(
                "core",
                "documentos",
                Set.of("CORE_SERVICE"),
                Instant.now().plusSeconds(30).getEpochSecond(),
                false);
    }

    static String exportToken(String operationId) {
        return Jwt.upn("core-service")
                .issuer("core")
                .audience("documentos")
                .groups(Set.of("CORE_SERVICE"))
                .claim("operationId", operationId)
                .expiresAt(Instant.now().plusSeconds(30).getEpochSecond())
                .sign(CORE_KEY.getPrivate());
    }

    private static KeyPair generateKey() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create ephemeral service test key", e);
        }
    }
}
