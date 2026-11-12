package ec.uce.propuestas.usuario.auth.mail;

import io.quarkus.runtime.LaunchMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import java.time.Instant;
import org.jboss.logging.Logger;

/**
 * Envío "de mentira" que solo escribe en el log. El token de un solo uso se
 * imprime únicamente en modo dev/test; en producción el log lleva el
 * destinatario enmascarado y nunca el token.
 */
@ApplicationScoped
@Typed(LogEnviadorCorreo.class)
public class LogEnviadorCorreo implements EnviadorCorreo {

    private static final Logger LOG = Logger.getLogger(LogEnviadorCorreo.class);

    @Override
    public void enviarVerificacion(String email, String token, Instant expiresAt) {
        registrar("Verificación", email, token, expiresAt);
    }

    @Override
    public void enviarReset(String email, String token, Instant expiresAt) {
        registrar("Reset", email, token, expiresAt);
    }

    @Override
    public void enviarInvitacion(String email, String token, Instant expiresAt) {
        registrar("Invitación", email, token, expiresAt);
    }

    private void registrar(String tipo, String email, String token, Instant expiresAt) {
        if (permiteTokenEnLog(LaunchMode.current())) {
            LOG.infof("[DEV-MAIL] %s → %s (expira: %s)", tipo, email, expiresAt);
            LOG.infof("[DEV-TOKEN] >>> %s <<<", token);
        } else {
            LOG.infof("[MAIL] %s → %s", tipo, enmascarar(email));
        }
    }

    /** Solo dev y test pueden imprimir tokens. */
    static boolean permiteTokenEnLog(LaunchMode modo) {
        return modo == LaunchMode.DEVELOPMENT || modo == LaunchMode.TEST;
    }

    /** {@code juan@uce.edu.ec} → {@code j***@uce.edu.ec}. */
    static String enmascarar(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int arroba = email.indexOf('@');
        if (arroba <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(arroba);
    }
}
