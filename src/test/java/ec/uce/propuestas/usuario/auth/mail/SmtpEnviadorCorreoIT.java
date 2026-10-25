package ec.uce.propuestas.usuario.auth.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SmtpEnviadorCorreoIT {

    @Inject
    SmtpEnviadorCorreo smtp;

    @Inject
    MockMailbox mailbox;

    @BeforeEach
    void limpiar() {
        mailbox.clear();
    }

    @Test
    void reset_envia_correo_con_enlace_de_restablecer() {
        smtp.enviarReset("ana@ex.com", "tok-123", Instant.now());
        var enviados = mailbox.getMailsSentTo("ana@ex.com");
        assertEquals(1, enviados.size());
        assertTrue(enviados.get(0).getText().contains("/restablecer/tok-123"));
    }

    @Test
    void verificacion_e_invitacion_llevan_token_en_el_enlace() {
        smtp.enviarVerificacion("b@ex.com", "v-1", Instant.now());
        smtp.enviarInvitacion("b@ex.com", "i-1", Instant.now());
        var textos = mailbox.getMailsSentTo("b@ex.com").stream()
                .map(m -> m.getText())
                .toList();
        assertTrue(textos.get(0).contains("/verificar-email?token=v-1"));
        assertTrue(textos.get(1).contains("/aceptar-invitacion?token=i-1"));
        assertFalse(textos.get(0).contains("<"));
    }
}
