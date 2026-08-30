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

    /** 409 version-vigente-protegida */
    public static ProblemaException versionVigenteProtegida(String mensaje) {
        return new ProblemaException(409, "version-vigente-protegida", mensaje);
    }

    /** 409 cronograma-ya-existe */
    public static ProblemaException cronogramaYaExiste(String mensaje) {
        return new ProblemaException(409, "cronograma-ya-existe", mensaje);
    }

    /** 409 reduccion-periodos-requiere-confirmacion */
    public static ProblemaException reduccionPeriodosRequiereConfirmacion(String mensaje) {
        return new ProblemaException(409, "reduccion-periodos-requiere-confirmacion", mensaje);
    }

    /** 409 export-bloqueado */
    public static ProblemaException exportBloqueado(String mensaje) {
        return new ProblemaException(409, "export-bloqueado", mensaje);
    }
}
