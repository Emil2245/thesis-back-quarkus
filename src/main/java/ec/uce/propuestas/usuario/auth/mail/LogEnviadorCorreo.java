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
        LOG.infof("[DEV-MAIL] Verificación → %s (expira: %s)", email, expiresAt);
        LOG.infof("[DEV-TOKEN] >>> %s <<<", token);
    }

    @Override
    public void enviarReset(String email, String token, Instant expiresAt) {
        LOG.infof("[DEV-MAIL] Reset → %s (expira: %s)", email, expiresAt);
        LOG.infof("[DEV-TOKEN] >>> %s <<<", token);
    }

    @Override
    public void enviarInvitacion(String email, String token, Instant expiresAt) {
        LOG.infof("[DEV-MAIL] Invitación → %s (expira: %s)", email, expiresAt);
        LOG.infof("[DEV-TOKEN] >>> %s <<<", token);
    }
}
