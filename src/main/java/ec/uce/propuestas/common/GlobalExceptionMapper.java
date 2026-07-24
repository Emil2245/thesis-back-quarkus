package ec.uce.propuestas.common;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Throwable t) {
        if (t instanceof WebApplicationException wae) {
            Response original = wae.getResponse();
            // If the entity is already an ErrorPayload, pass it through
            if (original.getEntity() instanceof ErrorPayload) {
                return original;
            }
            int status = original.getStatus();
            String codigo = codePorEstatus(status);
            String mensaje = wae.getMessage() != null ? wae.getMessage() : codigo;
            return Response.status(status)
                .entity(new ErrorPayload(codigo, mensaje))
                .build();
        }

        if (t instanceof ConstraintViolationException cve) {
            String firstMsg = cve.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse("Datos de entrada inválidos");
            return Response.status(400)
                .entity(new ErrorPayload("validacion", firstMsg))
                .build();
        }

        // Fallthrough: 500 — log throwable, never leak internals
        LOG.error("Error inesperado del servidor", t);
        return Response.status(500)
            .entity(new ErrorPayload("servidor", "Error interno del servidor"))
            .build();
    }

    private static String codePorEstatus(int status) {
        return switch (status) {
            case 400 -> "validacion";
            case 401 -> "credenciales-invalidas";
            case 403 -> "acceso-denegado";
            case 404 -> "no-encontrado";
            case 410 -> "token-invalido-o-expirado";
            case 429 -> "cooldown-activo";
            default  -> "servidor";
        };
    }
}
