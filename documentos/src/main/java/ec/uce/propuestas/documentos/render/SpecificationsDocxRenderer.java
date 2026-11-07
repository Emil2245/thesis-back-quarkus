package ec.uce.propuestas.documentos.render;

import ec.uce.propuestas.documentos.contract.SpecificationProjectionV1;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

/** Pure DOCX serialization of Core's ordered, validated technical projection. */
public final class SpecificationsDocxRenderer {
    private SpecificationsDocxRenderer() {}

    public static byte[] renderizar(SpecificationProjectionV1 projection) {
        if (projection == null || projection.schemaVersion() != SpecificationProjectionV1.VERSION)
            throw new IllegalArgumentException("Unsupported technical specification version");
        try (var doc = new XWPFDocument();
                var output = new ByteArrayOutputStream()) {
            var title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            boldCentered(title, projection.titulo1(), 18);
            var subtitle = doc.createParagraph();
            subtitle.setAlignment(ParagraphAlignment.CENTER);
            boldCentered(subtitle, projection.titulo2(), 16);
            var projectParagraph = doc.createParagraph();
            projectParagraph.setAlignment(ParagraphAlignment.CENTER);
            var project = projectParagraph.createRun();
            project.setFontSize(10);
            project.setText("Proyecto: " + projection.nombreProyecto() + " · Año: " + projection.anio()
                    + " · Fecha de exportación: " + projection.fechaExportacion());
            doc.createParagraph();
            int index = 1;
            for (var specification : projection.especificaciones()) {
                var header = doc.createParagraph().createRun();
                header.setBold(true);
                header.setFontSize(13);
                header.setText(index++ + ". " + specification.codigo() + " — " + specification.descripcion() + " ["
                        + specification.unidad() + "]");
                var body = doc.createParagraph().createRun();
                body.setFontSize(11);
                body.setText(specification.especificacionTecnica());
            }
            doc.createParagraph();
            var footer = doc.createParagraph().createRun();
            footer.setFontSize(9);
            footer.setItalic(true);
            footer.setText("Generado por Sistema APU · " + projection.fechaExportacion());
            doc.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el documento de Especificaciones Técnicas", e);
        }
    }

    private static void boldCentered(XWPFParagraph paragraph, String value, int size) {
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(size);
        run.setText(value);
    }
}
