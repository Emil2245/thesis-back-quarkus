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
            String mensaje = mensajeLegible(wae, status, codigo);
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
            default -> "servidor";
        };
    }

    /**
     * Para excepciones del framework (p. ej. {@code NotFoundException} por un
     * path param ilegible, {@code BadRequestException} por JSON malformado) el
     * mensaje JAX-RS es genérico ("HTTP 404 Not Found"). Se reemplaza por uno
     * legible en lenguaje de dominio salvo que la excepción traiga ya contexto.
     */
    private static String mensajeLegible(WebApplicationException wae, int status, String codigo) {
        String mensaje = wae.getMessage();
        if (mensaje == null || mensaje.isBlank() || mensaje.startsWith("HTTP ") || mensaje.equals(codigo)) {
            return switch (status) {
                case 404 -> "Recurso no encontrado";
                case 400 -> "Solicitud inválida";
                case 401 -> "Credenciales inválidas";
                case 403 -> "Acceso denegado";
                case 410 -> "Token inválido o expirado";
                case 429 -> "Demasiadas solicitudes, intente más tarde";
                default -> "Error del servidor";
            };
        }
        return mensaje;
    }
}
