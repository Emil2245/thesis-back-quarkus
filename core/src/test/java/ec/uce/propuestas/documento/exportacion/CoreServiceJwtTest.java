package ec.uce.propuestas.documento.exportacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CoreServiceJwtTest {
    @Test
    void signsShortLivedCoreOnlyTokenWithSeparateKey() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var service = generator.generateKeyPair();
        var user = generator.generateKeyPair();
        var issued = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        var token = CoreServiceJwt.signedToken(service.getPrivate(), user.getPublic(), issued);
        var parts = token.split("\\.");
        assertEquals(3, parts.length);
        var payload = new ObjectMapper()
                .readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        assertEquals("core", payload.path("iss").asText());
        var audience = payload.path("aud");
        assertEquals("documentos", audience.isArray() ? audience.get(0).asText() : audience.asText());
        assertEquals("core-service", payload.path("upn").asText());
        assertEquals("CORE_SERVICE", payload.path("groups").get(0).asText());
        assertTrue(payload.path("exp").asLong() - payload.path("iat").asLong() <= 60);
        assertTrue(payload.path("exp").asLong() > payload.path("iat").asLong());
        assertTrue(verifies(parts, service.getPublic()));
        assertFalse(verifies(parts, user.getPublic()));
    }

    @Test
    void rejectsReusedUserSigningKeyEvenWhenConfiguredAtAnotherPath() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var user = generator.generateKeyPair();

        assertThrows(
                IllegalStateException.class,
                () -> CoreServiceJwt.signedToken(user.getPrivate(), user.getPublic(), Instant.now()));
    }

    private static boolean verifies(String[] parts, PublicKey key) throws Exception {
        var verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        return verifier.verify(Base64.getUrlDecoder().decode(parts[2]));
    }
}
