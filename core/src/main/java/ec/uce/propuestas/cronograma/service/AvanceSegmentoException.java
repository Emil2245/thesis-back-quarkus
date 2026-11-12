package ec.uce.propuestas.cronograma.service;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Plan 029 (P-34) — 409 canónico de solapamiento de segmento en
 * {@code MOVER_SEGMENTO} o {@code REDIMENSIONAR_SEGMENTO}. Se lanza cuando el
 * destino del segmento colisiona con claves activas fuera del rango fuente.
 *
 * <p>El código {@code segmento-solapado} es local al módulo cronograma y se
 * sirve como {@link ProblemaException} 409; sin embargo, para conservar la
 * respuesta original sin envoltorios adicionales de errores comunes, este
 * helper expone una excepción JAX-RS que el {@code GlobalExceptionMapper}
 * deja pasar como {@link ec.uce.propuestas.common.ErrorPayload} estándar.
 */
public class AvanceSegmentoException extends WebApplicationException {

    public static final String CODIGO_SEGMENTO_SOLAPADO = "segmento-solapado";

    public AvanceSegmentoException(String mensaje) {
        super(Response.status(409)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ec.uce.propuestas.common.ErrorPayload(CODIGO_SEGMENTO_SOLAPADO, mensaje))
                .build());
    }
}
