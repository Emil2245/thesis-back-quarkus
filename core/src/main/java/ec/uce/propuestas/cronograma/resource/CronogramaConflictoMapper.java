package ec.uce.propuestas.cronograma.resource;

import ec.uce.propuestas.cronograma.service.CronogramaConflictoException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Plan 028 — mapper específico del 409 de configuración del cronograma.
 *
 * <p>El {@code GlobalExceptionMapper} común solo deja pasar entidades
 * {@code ErrorPayload}; este mapper, más específico, preserva el payload local
 * {@code {codigo, mensaje, perdidas}} sin modificar el contrato de error
 * compartido ni el comportamiento de los demás módulos.</p>
 */
@Provider
public class CronogramaConflictoMapper implements ExceptionMapper<CronogramaConflictoException> {

    @Override
    public Response toResponse(CronogramaConflictoException e) {
        return e.getResponse();
    }
}
