package ec.uce.propuestas.usuario.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

@QuarkusTest
class LogActividadSinPiiTest {

    private static final Pattern EMAIL = Pattern.compile("@\\S+\\.\\S+");
    private static final String[] PROHIBIDOS = {
        "passwordhash", "token", "jwt", "bearer", "hash", "iporigen", "email", "correo"
    };

    @Inject
    ObjectMapper mapper;

    @Test
    void los_26_detalles_minimos_no_contienen_pii_ni_secretos() throws Exception {
        for (EventoLogActividad evento : EventoLogActividad.values()) {
            Map<String, Object> detalle = DetalleCanonicoFixtures.para(evento);
            LogActividadDetalleValidator.validar(evento, detalle);
            String json = mapper.writeValueAsString(detalle);
            // tokenExpiraEn is the one canonical key containing the word "token";
            // the matrix forbids token material, not this signed expiry-field name.
            String normalizado = json.replace("tokenExpiraEn", "expiraEn").toLowerCase(Locale.ROOT);
            assertFalse(json.contains("@"), evento.value());
            assertFalse(EMAIL.matcher(json).find(), evento.value());
            for (String prohibido : PROHIBIDOS) {
                assertFalse(normalizado.contains(prohibido), evento.value() + ": " + prohibido);
            }
        }
    }
}
