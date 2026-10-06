package ec.uce.propuestas.documento.exportacion;

import ec.uce.propuestas.common.ProblemaException;
import jakarta.ws.rs.core.MultivaluedMap;
import java.util.Map;
import java.util.Set;

/** Closed, case-sensitive budget/APU contract; independent of MSPDI. */
public record OpcionesDocumento(String formato, Map<String, String> opciones) {
    public OpcionesDocumento {
        opciones = Map.copyOf(opciones);
    }

    public static OpcionesDocumento parsear(String documento, MultivaluedMap<String, String> query) {
        for (var entry : query.entrySet()) {
            if (!Set.of("formato", "orientacion", "layout").contains(entry.getKey())
                    || entry.getValue().size() != 1
                    || entry.getValue().getFirst().isBlank()) {
                throw ProblemaException.validacion("Opciones de documento inválidas");
            }
        }
        String formato = query.getFirst("formato");
        if (!"xlsx".equals(formato) && !"pdf".equals(formato)) {
            throw ProblemaException.validacion("formato debe ser xlsx o pdf");
        }
        String option = "presupuesto".equals(documento) && "pdf".equals(formato)
                ? "orientacion"
                : "apus".equals(documento) && "xlsx".equals(formato) ? "layout" : null;
        for (String key : query.keySet()) {
            if (!key.equals("formato") && !key.equals(option)) {
                throw ProblemaException.validacion("Opción incompatible con el documento y formato");
            }
        }
        if (option == null) return new OpcionesDocumento(formato, Map.of());
        String value = query.getFirst(option);
        Set<String> values = option.equals("layout") ? Set.of("pestanas", "apilado") : Set.of("vertical", "horizontal");
        if (value == null) value = option.equals("layout") ? "pestanas" : "vertical";
        if (!values.contains(value)) throw ProblemaException.validacion("Opción de documento inválida");
        return new OpcionesDocumento(formato, Map.of(option, value));
    }
}
