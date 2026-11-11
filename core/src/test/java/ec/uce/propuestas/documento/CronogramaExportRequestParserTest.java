package ec.uce.propuestas.documento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.cronograma.export.CronogramaPdfWriter.Papel;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CronogramaExportRequestParserTest {
    private final ObjectMapper json = new ObjectMapper();

    private CronogramaExportRequestParser.Parsed parse(String body) {
        return CronogramaExportRequestParser.parse(body.getBytes(StandardCharsets.UTF_8), json);
    }

    @Test
    void paperDefaultsOnlyForPdf() {
        assertEquals(Papel.A4, parse("{\"formato\":\"pdf\"}").paper());
        assertEquals(Papel.A3, parse("{\"formato\":\"pdf\",\"papel\":\"a3\"}").paper());
        assertNull(parse("{\"formato\":\"xlsx\"}").paper());
        assertNull(parse("{\"formato\":\"mspdi\"}").paper());
    }

    @Test
    void rejectsNonClosedOrInvalidBodies() {
        for (String body : new String[] {
            "{}",
            "[]",
            "null",
            "{\"formato\":\"PDF\"}",
            "{\"formato\":\"xlsx\",\"papel\":\"a4\"}",
            "{\"formato\":\"pdf\",\"papel\":null}",
            "{\"formato\":\"pdf\",\"otro\":\"x\"}",
            "{\"formato\":\"pdf\",\"formato\":\"pdf\"}",
            "{\"formato\":\"pdf\"} {}"
        }) {
            assertThrows(ProblemaException.class, () -> parse(body), body);
        }
    }
}
