package ec.uce.propuestas.usuario.auth.mail;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Instant;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Único bean {@link EnviadorCorreo} por defecto: elige en tiempo de ejecución
 * entre log y SMTP según {@code app.mail.mode} ({@code log} | {@code smtp}).
 */
@ApplicationScoped
public class CorreoDelegador implements EnviadorCorreo {

    @Inject
    LogEnviadorCorreo log;

    @Inject
    SmtpEnviadorCorreo smtp;

    @ConfigProperty(name = "app.mail.mode", defaultValue = "log")
    String modo;

    private EnviadorCorreo activo() {
        return "smtp".equalsIgnoreCase(modo) ? smtp : log;
    }

    @Override
    public void enviarVerificacion(String email, String token, Instant expiresAt) {
        activo().enviarVerificacion(email, token, expiresAt);
    }

    @Override
    public void enviarReset(String email, String token, Instant expiresAt) {
        activo().enviarReset(email, token, expiresAt);
    }

    @Override
    public void enviarInvitacion(String email, String token, Instant expiresAt) {
        activo().enviarInvitacion(email, token, expiresAt);
    }
}
