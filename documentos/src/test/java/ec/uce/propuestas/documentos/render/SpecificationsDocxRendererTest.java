package ec.uce.propuestas.documentos.render;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.documentos.contract.SpecificationProjectionV1;
import ec.uce.propuestas.documentos.contract.SpecificationProjectionV1.Specification;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class SpecificationsDocxRendererTest {
    @Test
    void rendersOnlyOrderedTechnicalFieldsFromImmutableProjection() throws Exception {
        var projection = new SpecificationProjectionV1(
                1,
                UUID.fromString("01900000-0000-7000-8000-000000000001"),
                "Hospital Solanda",
                (short) 2027,
                "Título elegido",
                "Subtítulo elegido",
                "2026-11-07",
                List.of(
                        new Specification("A-1", "Losa", "m²", "Hormigón armado"),
                        new Specification("A-2", "Pintura", "u", "Preparación de superficie")));

        byte[] bytes = SpecificationsDocxRenderer.renderizar(projection);

        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);
        try (var document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String text =
                    document.getParagraphs().stream().map(p -> p.getText()).reduce("", (a, b) -> a + "\n" + b);
            assertTrue(text.contains("Título elegido"), text);
            assertTrue(text.contains("Subtítulo elegido"), text);
            assertTrue(
                    text.contains("Proyecto: Hospital Solanda · Año: 2027 · Fecha de exportación: 2026-11-07"), text);
            assertTrue(text.contains("1. A-1 — Losa [m²]"), text);
            assertTrue(text.contains("2. A-2 — Pintura [u]"), text);
            assertTrue(text.indexOf("Hormigón armado") < text.indexOf("Preparación de superficie"), text);
            assertTrue(text.contains("Generado por Sistema APU · 2026-11-07"), text);
            assertFalse(text.contains("costo_directo"), text);
        }
    }
}
