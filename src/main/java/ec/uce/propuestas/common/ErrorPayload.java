package ec.uce.propuestas.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorPayload(String codigo, String mensaje, Map<String, Object> detalles) {
    public ErrorPayload(String codigo, String mensaje) {
        this(codigo, mensaje, null);
    }
}
