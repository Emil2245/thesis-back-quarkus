package ec.uce.propuestas.cronograma.service;

import ec.uce.propuestas.cronograma.dto.CronogramaConflictoPayload;
import ec.uce.propuestas.cronograma.dto.PerdidaAvanceResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Plan 028 — 409 local del módulo cronograma.
 *
 * <p>Se usa exclusivamente para
 * {@code configuracion-cronograma-requiere-confirmacion}, cuyo body debe
 * enumerar de forma determinista los avances que se perderían (D-10 y
 * STOP-028-LOSS). El resto de errores del módulo siguen usando el
 * {@code ProblemaException}/{@code ErrorPayload} común sin cambios; el
 * {@code GlobalExceptionMapper} deja pasar esta respuesta porque ya trae su
 * entidad construida.</p>
 */
public class CronogramaConflictoException extends WebApplicationException {

    public static final String CODIGO_REQUIERE_CONFIRMACION = "configuracion-cronograma-requiere-confirmacion";

    public CronogramaConflictoException(String mensaje, List<PerdidaAvanceResponse> perdidas) {
        super(Response.status(409)
                .type(MediaType.APPLICATION_JSON)
                .entity(new CronogramaConflictoPayload(CODIGO_REQUIERE_CONFIRMACION, mensaje, List.copyOf(perdidas)))
                .build());
    }
}
