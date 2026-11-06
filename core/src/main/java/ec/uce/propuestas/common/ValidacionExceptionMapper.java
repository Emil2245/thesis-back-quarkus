package ec.uce.propuestas.common;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Asegura que las violaciones de {@code @Valid} en la frontera del recurso
 * devuelvan el payload contract {@link ErrorPayload} ({@code codigo} /{@code mensaje})
 * en lugar del formato ViolationReport nativo de Quarkus.
 *
 * <p>El mapeador del framework ({@code ResteasyReactiveViolationExceptionMapper})
 * registra {@code ExceptionMapper<ValidationException>}; este mapeador registra
 * {@code ExceptionMapper<ConstraintViolationException>}, un tipo más específico,
 * por lo que JAX-RS lo selecciona a él.</p>
 */
@Provider
public class ValidacionExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException cve) {
        String primerMensaje = cve.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .orElse("Datos de entrada inválidos");
        return Response.status(400)
                .entity(new ErrorPayload("validacion", primerMensaje))
                .build();
    }
}
