package ec.uce.propuestas.usuario.auth.mail;

import java.time.Instant;

public interface EnviadorCorreo {

    void enviarVerificacion(String email, String token, Instant expiresAt);

    void enviarReset(String email, String token, Instant expiresAt);

    void enviarInvitacion(String email, String token, Instant expiresAt);
}
