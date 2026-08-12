package ec.uce.propuestas.common;

import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Convierte los fallos de seguridad (token ausente, inválido, expirado o rol
 * insuficiente) en el payload contract {@link ErrorPayload} en lugar de la
 * respuesta vacía que Quarkus envía por defecto.
 *
 * <p>Requiere {@code quarkus.http.auth.proactive=false}: con la autenticación
 * proactiva (default) el fallo ocurre antes de entrar al pipeline JAX-RS y no
 * puede ser interceptado por un {@code ExceptionMapper}. Con lazy auth, las
 * excepciones de seguridad fluyen por el mapeador.</p>
 *
 * <p>Prioridad más alta que los mappers built-in de Quarkus (5001), por lo que
 * estos delegados ganan.</p>
 */
@Provider
public class SeguridadExceptionMapper {

    @ServerExceptionMapper(value = UnauthorizedException.class, priority = Priorities.AUTHENTICATION)
    public Response noAutenticado(UnauthorizedException e) {
        return error(401, "credenciales-invalidas", "Token ausente, inválido o expirado");
    }

    @ServerExceptionMapper(value = AuthenticationFailedException.class, priority = Priorities.AUTHENTICATION)
    public Response autenticacionFallida(AuthenticationFailedException e) {
        return error(401, "credenciales-invalidas", "Token ausente, inválido o expirado");
    }

    @ServerExceptionMapper(value = ForbiddenException.class, priority = Priorities.AUTHENTICATION)
    public Response accesoDenegado(ForbiddenException e) {
        return error(403, "acceso-denegado", "No posee los permisos necesarios para esta operación");
    }

    private static Response error(int status, String codigo, String mensaje) {
        return Response.status(status).entity(new ErrorPayload(codigo, mensaje)).build();
    }
}
