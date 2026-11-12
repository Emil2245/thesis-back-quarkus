package ec.uce.propuestas.documentos.render;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1.Mspdi;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1.MspdiRow;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;

class CronogramaMspdiRendererTest {
    @Test
    void rendersCapturedValuesAndEscapesHostileText() throws Exception {
        String hostile = "<&\"' > </Name><Injected>injection</Injected>";
        var row = new MspdiRow(
                "00000000-0000-0000-0000-000000000002",
                "1.1",
                hostile,
                hostile,
                "m²",
                "2.123456",
                "3.123456",
                "6.123456",
                "4.0000",
                List.of(1, 3),
                List.of("10.0000", "90.0000"));
        var mspdi = new Mspdi(
                "00000000-0000-0000-0000-000000000001", "P", hostile, 2025, "SEMANA", 3, "2025-01-02", List.of(row));
        var projection = new CronogramaProjectionV1(1, "mspdi", null, null, mspdi);

        byte[] bytes = CronogramaMspdiRenderer.renderizar(projection);
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        var doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        String ns = "http://schemas.microsoft.com/project/2007";
        assertEquals("Project", doc.getDocumentElement().getLocalName());
        assertEquals(ns, doc.getDocumentElement().getNamespaceURI());
        assertEquals("12", doc.getElementsByTagNameNS(ns, "SaveVersion").item(0).getTextContent());
        assertEquals(hostile, doc.getElementsByTagNameNS(ns, "Name").item(0).getTextContent());
        assertEquals(0, doc.getElementsByTagNameNS(ns, "Injected").getLength());
        assertTrue(new String(bytes, StandardCharsets.UTF_8).contains("&lt;"));
        assertEquals(1, doc.getElementsByTagNameNS(ns, "Task").getLength());
        assertEquals("6.123456", doc.getElementsByTagNameNS(ns, "Cost").item(0).getTextContent());
        assertTrue(
                doc.getElementsByTagNameNS(ns, "Notes").item(0).getTextContent().contains("1:10.0000;3:90.0000"));
        assertEquals(0, doc.getElementsByTagNameNS(ns, "PredecessorLink").getLength());
        assertEquals(0, doc.getElementsByTagNameNS(ns, "Assignment").getLength());
    }

    @Test
    void rejectsInvalidXmlCharactersInsteadOfEmittingAnUnparseablePrivateObject() {
        var row = new MspdiRow(
                "019a0000-0000-7000-8000-000000000002",
                "1",
                "R1",
                "bad" + (char) 1 + "xml",
                "u",
                "1.000000",
                "1.000000",
                "1.000000",
                "100.0000",
                List.of(1),
                List.of("100.0000"));
        var mspdi = new Mspdi(
                "019a0000-0000-7000-8000-000000000001", "P1", "Project", 2025, "SEMANA", 1, "2025-01-01", List.of(row));
        var projection = new CronogramaProjectionV1(1, "mspdi", null, null, mspdi);
        assertThrows(IllegalArgumentException.class, () -> CronogramaMspdiRenderer.renderizar(projection));
    }

    @Test
    void rejectsNullProjection() {
        assertThrows(IllegalArgumentException.class, () -> CronogramaMspdiRenderer.renderizar(null));
    }
}
