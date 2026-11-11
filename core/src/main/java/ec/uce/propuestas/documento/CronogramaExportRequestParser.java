package ec.uce.propuestas.documento;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.cronograma.export.CronogramaPdfWriter.Papel;
import ec.uce.propuestas.cronograma.export.FormatoExportacion;
import ec.uce.propuestas.documento.exportacion.DocumentosHttpClient;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Closed POST body, separate from the legacy GET query parser. */
public final class CronogramaExportRequestParser {
    private CronogramaExportRequestParser() {}

    public static Parsed parse(byte[] raw, ObjectMapper mapper) {
        if (raw == null || raw.length == 0 || raw.length > DocumentosHttpClient.MAX_REQUEST_BYTES || mapper == null) {
            throw ProblemaException.validacion("Solicitud de exportación inválida");
        }
        JsonNode body;
        try (var parser = mapper.getFactory().createParser(raw)) {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            body = mapper.readTree(parser);
            if (parser.nextToken() != null) throw ProblemaException.validacion("JSON de exportación inválido");
        } catch (IOException invalid) {
            throw ProblemaException.validacion("JSON de exportación inválido");
        }
        if (body == null || !body.isObject()) throw ProblemaException.validacion("Solicitud de exportación inválida");
        var fields = new TreeMap<String, String>();
        var iterator = body.fields();
        while (iterator.hasNext()) {
            var field = iterator.next();
            if (!Set.of("formato", "papel").contains(field.getKey())
                    || !field.getValue().isTextual()) {
                throw ProblemaException.validacion("Opciones de exportación inválidas");
            }
            fields.put(field.getKey(), field.getValue().textValue());
        }
        String token = fields.get("formato");
        if (!Set.of("xlsx", "pdf", "mspdi").contains(token == null ? "" : token)) {
            throw ProblemaException.validacion("Formato de exportación inválido");
        }
        FormatoExportacion format = FormatoExportacion.parsear(token);
        String paper = fields.get("papel");
        if (paper != null
                && (format != FormatoExportacion.PDF || !Set.of("a4", "a3").contains(paper))) {
            throw ProblemaException.validacion("Papel de exportación inválido");
        }
        return new Parsed(
                format,
                format == FormatoExportacion.PDF ? ("a3".equals(paper) ? Papel.A3 : Papel.A4) : null,
                Collections.unmodifiableMap(fields));
    }

    public record Parsed(FormatoExportacion format, Papel paper, Map<String, String> requestFields) {}
}
