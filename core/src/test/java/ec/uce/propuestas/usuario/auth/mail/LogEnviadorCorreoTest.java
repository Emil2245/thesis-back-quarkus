package ec.uce.propuestas.usuario.auth.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.runtime.LaunchMode;
import org.junit.jupiter.api.Test;

class LogEnviadorCorreoTest {

    @Test
    void enmascara_destinatario() {
        assertEquals("j***@uce.edu.ec", LogEnviadorCorreo.enmascarar("juan@uce.edu.ec"));
        assertEquals("***", LogEnviadorCorreo.enmascarar("sin-arroba"));
        assertEquals("***", LogEnviadorCorreo.enmascarar(null));
    }

    @Test
    void token_solo_en_dev_y_test() {
        assertTrue(LogEnviadorCorreo.permiteTokenEnLog(LaunchMode.DEVELOPMENT));
        assertTrue(LogEnviadorCorreo.permiteTokenEnLog(LaunchMode.TEST));
        assertFalse(LogEnviadorCorreo.permiteTokenEnLog(LaunchMode.NORMAL));
    }
}
