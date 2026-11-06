package ec.uce.propuestas.usuario.auth;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PasswordService {

    public String hash(String plain) {
        return BcryptUtil.bcryptHash(plain);
    }

    public boolean verify(String plain, String hash) {
        return BcryptUtil.matches(plain, hash);
    }
}
