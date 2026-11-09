package ec.uce.propuestas.documento.exportacion;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.common.ProblemaException;
import jakarta.ws.rs.core.MultivaluedHashMap;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Closed JSON body contract for new export POSTs; never changes legacy GET query parsing. */
public final class ExportRequestParser {
    private ExportRequestParser() {}

    public static Parsed parse(String document, byte[] rawBody, ObjectMapper mapper) {
        if (rawBody == null
                || rawBody.length == 0
                || rawBody.length > DocumentosHttpClient.MAX_REQUEST_BYTES
                || mapper == null) {
            throw ProblemaException.validacion("Solicitud de exportación inválida");
        }
        JsonNode body;
        try (var parser = mapper.getFactory().createParser(rawBody)) {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            body = mapper.readTree(parser);
            if (parser.nextToken() != null) {
                throw ProblemaException.validacion("JSON de exportación inválido");
            }
        } catch (IOException invalid) {
            throw ProblemaException.validacion("JSON de exportación inválido");
        }
        return parseTree(document, body);
    }

    private static Parsed parseTree(String document, JsonNode body) {
        if (body == null
                || !body.isObject()
                || body.isEmpty()
                || !Set.of("presupuesto", "apus", "especificaciones-tecnicas").contains(document)) {
            throw ProblemaException.validacion("Solicitud de exportación inválida");
        }
        var fields = new TreeMap<String, String>();
        var names = body.fields();
        while (names.hasNext()) {
            var field = names.next();
            if (!Set.of("formato", "orientacion", "layout", "titulo1", "titulo2")
                            .contains(field.getKey())
                    || !field.getValue().isTextual()) {
                throw ProblemaException.validacion("Opciones de exportación inválidas");
            }
            fields.put(field.getKey(), field.getValue().textValue());
        }
        String format = fields.get("formato");
        if ("especificaciones-tecnicas".equals(document)) {
            if (!"docx".equals(format) || fields.containsKey("orientacion") || fields.containsKey("layout")) {
                throw ProblemaException.validacion("Formato u opciones incompatibles con especificaciones técnicas");
            }
            return new Parsed(
                    new OpcionesDocumento(format, Map.of()),
                    title(fields.get("titulo1")),
                    title(fields.get("titulo2")),
                    Collections.unmodifiableMap(fields));
        }
        if (fields.containsKey("titulo1") || fields.containsKey("titulo2")) {
            throw ProblemaException.validacion("Títulos incompatibles con el documento");
        }
        var options = new MultivaluedHashMap<String, String>();
        fields.forEach(options::add);
        return new Parsed(
                OpcionesDocumento.parsear(document, options), null, null, Collections.unmodifiableMap(fields));
    }

    private static String title(String input) {
        return input == null || input.isBlank() ? null : input.trim();
    }

    public record Parsed(OpcionesDocumento options, String title1, String title2, Map<String, String> requestFields) {}
}
