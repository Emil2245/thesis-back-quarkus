package ec.uce.propuestas.usuario.auth;

import ec.uce.propuestas.usuario.auth.mail.EnviadorCorreo;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Alternative
@Priority(10)
@ApplicationScoped
public class RecordingEnviadorCorreo implements EnviadorCorreo {

    public record Entrega(String destinatario, String tipo, String tokenRaw, Instant enviadoEn) {}

    private final List<Entrega> entregas = new CopyOnWriteArrayList<>();

    @Override
    public void enviarVerificacion(String email, String token, Instant expiresAt) {
        entregas.add(new Entrega(email, "verificacion", token, Instant.now()));
    }

    @Override
    public void enviarReset(String email, String token, Instant expiresAt) {
        entregas.add(new Entrega(email, "reset", token, Instant.now()));
    }

    @Override
    public void enviarInvitacion(String email, String token, Instant expiresAt) {
        entregas.add(new Entrega(email, "invitacion", token, Instant.now()));
    }

    public List<Entrega> entregas() { return List.copyOf(entregas); }

    public void clear() { entregas.clear(); }
}
