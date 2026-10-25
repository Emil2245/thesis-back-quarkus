package ec.uce.propuestas.usuario.auth.mail;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Typed;
import jakarta.inject.Inject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Envío real por SMTP (texto plano). El enlace se arma con
 * {@code app.frontend.base-url}; el servidor SMTP se configura con las
 * variables estándar {@code QUARKUS_MAILER_*}.
 */
@ApplicationScoped
@Typed(SmtpEnviadorCorreo.class)
public class SmtpEnviadorCorreo implements EnviadorCorreo {

    @Inject
    Mailer mailer;

    @ConfigProperty(name = "app.frontend.base-url")
    String baseUrl;

    @Override
    public void enviarVerificacion(String email, String token, Instant expiresAt) {
        enviar(
                email,
                "Verifica tu correo",
                "Para verificar tu correo abre este enlace:",
                "/verificar-email?token=" + codificar(token),
                expiresAt);
    }

    @Override
    public void enviarReset(String email, String token, Instant expiresAt) {
        enviar(
                email,
                "Restablece tu contraseña",
                "Para restablecer tu contraseña abre este enlace:",
                "/restablecer/" + codificar(token),
                expiresAt);
    }

    @Override
    public void enviarInvitacion(String email, String token, Instant expiresAt) {
        enviar(
                email,
                "Invitación a la plataforma",
                "Te invitaron a la plataforma. Para activar tu cuenta abre este enlace:",
                "/aceptar-invitacion?token=" + codificar(token),
                expiresAt);
    }

    private void enviar(String email, String asunto, String intro, String ruta, Instant expiresAt) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String cuerpo = intro + "\n\n" + base + ruta + "\n\nEl enlace expira el " + expiresAt
                + ". Si no esperabas este correo, ignóralo.\n";
        mailer.send(Mail.withText(email, asunto, cuerpo));
    }

    private static String codificar(String token) {
        return URLEncoder.encode(token, StandardCharsets.UTF_8);
    }
}
