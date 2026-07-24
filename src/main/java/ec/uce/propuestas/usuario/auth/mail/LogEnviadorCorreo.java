package ec.uce.propuestas.usuario.auth.mail;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Instant;

@ApplicationScoped
@Priority(1)
public class LogEnviadorCorreo implements EnviadorCorreo {

    private static final Logger LOG = Logger.getLogger(LogEnviadorCorreo.class);

    @Override
    public void enviarVerificacion(String email, String token, Instant expiresAt) {
        // NEVER log the token value (RNF-08). Log only the event.
        LOG.infof("[DEV] Verificación de email enviada a %s (expira: %s)", email, expiresAt);
    }

    @Override
    public void enviarReset(String email, String token, Instant expiresAt) {
        LOG.infof("[DEV] Reset de password enviado a %s (expira: %s)", email, expiresAt);
    }

    @Override
    public void enviarInvitacion(String email, String token, Instant expiresAt) {
        LOG.infof("[DEV] Invitación enviada a %s (expira: %s)", email, expiresAt);
    }
}
