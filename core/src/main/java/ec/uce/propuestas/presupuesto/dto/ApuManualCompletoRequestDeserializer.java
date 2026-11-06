package ec.uce.propuestas.presupuesto.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import ec.uce.propuestas.apu.dto.ApuDetalleCrearRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Strict input reader for the aggregate endpoint's server-authored fields. */
final class ApuManualCompletoRequestDeserializer extends StdDeserializer<ApuManualCompletoRequest> {

    private static final long serialVersionUID = 1L;
    private static final Set<String> REQUEST_FIELDS =
            Set.of("codigo", "descripcion", "unidad", "porcentajeIndirecto", "capituloId", "detalles");
    private static final Set<String> DETAIL_FIELDS = Set.of("seccionTipo", "insumoId", "cantidad", "rendimiento");

    ApuManualCompletoRequestDeserializer() {
        super(ApuManualCompletoRequest.class);
    }

    @Override
    public ApuManualCompletoRequest deserialize(
            JsonParser parser, com.fasterxml.jackson.databind.DeserializationContext context) throws IOException {
        ObjectCodec codec = parser.getCodec();
        JsonNode root = codec.readTree(parser);
        requireObject(root, parser, "El body debe ser un objeto JSON");
        rejectUnknown(root, REQUEST_FIELDS, parser);

        String codigo = value(codec, root, "codigo", String.class);
        String descripcion = value(codec, root, "descripcion", String.class);
        String unidad = value(codec, root, "unidad", String.class);
        BigDecimal porcentajeIndirecto = value(codec, root, "porcentajeIndirecto", BigDecimal.class);
        UUID capituloId = value(codec, root, "capituloId", UUID.class);
        List<ApuDetalleCrearRequest> detalles = leerDetalles(codec, parser, root.get("detalles"));
        return new ApuManualCompletoRequest(codigo, descripcion, unidad, porcentajeIndirecto, capituloId, detalles);
    }

    private static List<ApuDetalleCrearRequest> leerDetalles(ObjectCodec codec, JsonParser parser, JsonNode node)
            throws IOException {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isArray()) {
            throw JsonMappingException.from(parser, "detalles debe ser un arreglo");
        }
        List<ApuDetalleCrearRequest> detalles = new ArrayList<>();
        for (JsonNode detalle : node) {
            requireObject(detalle, parser, "Cada detalle debe ser un objeto JSON");
            rejectUnknown(detalle, DETAIL_FIELDS, parser);
            detalles.add(codec.treeToValue(detalle, ApuDetalleCrearRequest.class));
        }
        return detalles;
    }

    private static void rejectUnknown(JsonNode object, Set<String> allowed, JsonParser parser) throws IOException {
        Iterator<String> fields = object.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!allowed.contains(field)) {
                throw JsonMappingException.from(parser, "Campo no permitido: " + field);
            }
        }
    }

    private static void requireObject(JsonNode node, JsonParser parser, String message) throws IOException {
        if (node == null || !node.isObject()) {
            throw JsonMappingException.from(parser, message);
        }
    }

    private static <T> T value(ObjectCodec codec, JsonNode root, String field, Class<T> type) throws IOException {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : codec.treeToValue(node, type);
    }
}
