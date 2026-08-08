package ec.uce.propuestas.common;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * Excepción de dominio que transporta el código de error tipado del contrato
 * (catálogo de "type" en 07-api-contract.md) y un mensaje legible.
 * El {@link GlobalExceptionMapper} lo convierte en la respuesta problem+json.
 */
public class ProblemaException extends WebApplicationException {

    public ProblemaException(int status, String codigo, String mensaje) {
        super(Response.status(status).entity(new ErrorPayload(codigo, mensaje)).build());
    }

    /** 400 validacion */
    public static ProblemaException validacion(String mensaje) {
        return new ProblemaException(400, "validacion", mensaje);
    }

    /** 404 no-encontrado (también recursos ajenos — RNF-05) */
    public static ProblemaException noEncontrado(String mensaje) {
        return new ProblemaException(404, "no-encontrado", mensaje);
    }
}