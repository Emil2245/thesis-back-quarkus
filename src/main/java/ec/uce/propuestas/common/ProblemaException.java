package ec.uce.propuestas.common;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Excepción de dominio que transporta el código de error tipado del contrato
 * (catálogo de "type" en 07-api-contract.md) y un mensaje legible.
 * El {@link GlobalExceptionMapper} lo convierte en la respuesta problem+json.
 *
 * <p>El Content-Type se fija explícitamente a {@code application/json} para que
 * los recursos cuyo {@code @Produces} no incluye JSON (p. ej. writers binarios
 * del módulo {@code documento} que declaran {@code *\/*}) también reciban
 * {@code Content-Type: application/json} en sus respuestas de error.</p>
 */
public class ProblemaException extends WebApplicationException {

    public ProblemaException(int status, String codigo, String mensaje) {
        super(Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorPayload(codigo, mensaje))
                .build());
    }

    /** 400 validacion */
    public static ProblemaException validacion(String mensaje) {
        return new ProblemaException(400, "validacion", mensaje);
    }

    /** 404 no-encontrado (también recursos ajenos — RNF-05) */
    public static ProblemaException noEncontrado(String mensaje) {
        return new ProblemaException(404, "no-encontrado", mensaje);
    }

    /** 400 codigo-duplicado (catálogo de 07-api-contract.md) */
    public static ProblemaException codigoDuplicado(String mensaje) {
        return new ProblemaException(400, "codigo-duplicado", mensaje);
    }

    /** 409 apu-referenciado (D-09 rubro ↔ APU 1:1; D-08 auxiliar) */
    public static ProblemaException apuReferenciado(String mensaje) {
        return new ProblemaException(409, "apu-referenciado", mensaje);
    }

    /** 409 fila-protegida (fila HM del bloque M no editable ni eliminable) */
    public static ProblemaException filaProtegida(String mensaje) {
        return new ProblemaException(409, "fila-protegida", mensaje);
    }
}
