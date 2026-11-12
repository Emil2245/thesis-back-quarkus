package ec.uce.propuestas.documento.exportacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.common.ProblemaException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExportRequestParserTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void acceptsOnlyFamilyOptionsAndKeepsRequestHashInputsInStableKeyOrder() {
        var budget = parse("presupuesto", "{\"orientacion\":\"horizontal\",\"formato\":\"pdf\"}");
        assertEquals("pdf", budget.options().formato());
        assertEquals(Map.of("orientacion", "horizontal"), budget.options().opciones());
        var reordered = parse("presupuesto", "{\"formato\":\"pdf\",\"orientacion\":\"horizontal\"}");
        assertEquals(budget.requestFields(), reordered.requestFields());
        assertEquals(
                ExportFingerprint.hash(json, budget.requestFields()),
                ExportFingerprint.hash(json, reordered.requestFields()));
        var apus = parse("apus", "{\"formato\":\"xlsx\",\"layout\":\"apilado\"}");
        assertEquals(Map.of("layout", "apilado"), apus.options().opciones());
        var specs = parse(
                "especificaciones-tecnicas",
                "{\"formato\":\"docx\",\"titulo1\":\"  Technical  \",\"titulo2\":\"Notes\"}");
        assertEquals("Technical", specs.title1());
        assertEquals("Notes", specs.title2());
        assertEquals("docx", specs.options().formato());
        assertNull(parse("especificaciones-tecnicas", "{\"formato\":\"docx\"}").title1());
    }

    @Test
    void rejectsDuplicateJsonMembersInsteadOfHashingLastValue() {
        assertEquals(
                400,
                assertThrows(
                                ProblemaException.class,
                                () -> parse("presupuesto", "{\"formato\":\"xlsx\",\"formato\":\"pdf\"}"))
                        .getResponse()
                        .getStatus());
    }

    @Test
    void rejectsUnknownMissingIncompatibleOrNonStringFields() {
        for (String body : new String[] {
            "{}",
            "null",
            "[]",
            "{\"formato\":\"pdf\"} {\"formato\":\"xlsx\"}",
            "{\"formato\":\"pdf\",\"layout\":\"apilado\"}",
            "{\"formato\":\"pdf\",\"otro\":\"x\"}",
            "{\"formato\":\"pdf\",\"orientacion\":12}",
            "{\"formato\":\"xlsx\",\"orientacion\":\"horizontal\"}"
        }) {
            assertEquals(
                    400,
                    assertThrows(ProblemaException.class, () -> parse("presupuesto", body))
                            .getResponse()
                            .getStatus());
        }
        assertEquals(
                400,
                assertThrows(ProblemaException.class, () -> parse("especificaciones-tecnicas", "{\"formato\":\"pdf\"}"))
                        .getResponse()
                        .getStatus());
        assertEquals(
                400,
                assertThrows(
                                ProblemaException.class,
                                () -> ExportRequestParser.parse(
                                        "presupuesto",
                                        "x"
                                                .repeat(DocumentosLimites.MAX_REQUEST_BYTES + 1)
                                                .getBytes(StandardCharsets.UTF_8),
                                        json))
                        .getResponse()
                        .getStatus());
    }

    private ExportRequestParser.Parsed parse(String document, String body) {
        return ExportRequestParser.parse(document, body.getBytes(StandardCharsets.UTF_8), json);
    }
}
