package ec.uce.propuestas.documento.exportacion;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(CoreServiceJwtConfigIT.ReuseUserKeyProfile.class)
class CoreServiceJwtConfigIT {
    @Inject
    CoreServiceJwt serviceJwt;

    @Test
    void refusesConfiguredServiceKeyWhenItMatchesTheUserJwtKey() {
        var error = assertThrows(IllegalStateException.class, serviceJwt::issueToken);
        assertTrue(error.getMessage().contains("must differ"));
        assertFalse(error.getMessage().contains("test-privateKey.pem"));
    }

    public static class ReuseUserKeyProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("documentos.service.signing-key-location", "classpath:jwt/test-privateKey.pem");
        }
    }
}
