package ec.uce.propuestas.usuario.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ec.uce.propuestas.usuario.Rol;
import ec.uce.propuestas.usuario.Usuario;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

@QuarkusTest
class TokenIssuerTest {

    @Inject
    TokenService tokenService;

    @ConfigProperty(name = "smallrye.jwt.new-token.issuer")
    String issuer;

    @Test
    void issClaimEsElIssuerConfigurado() {
        Usuario u = new Usuario();
        u.email = "iss@test.ec";
        u.rol = Rol.values()[0];
        String payload = tokenService.mintAccessToken(u).split("\\.")[1];
        String json = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
        assertEquals(true, json.contains("\"iss\":\"" + issuer + "\""), json);
    }
}
