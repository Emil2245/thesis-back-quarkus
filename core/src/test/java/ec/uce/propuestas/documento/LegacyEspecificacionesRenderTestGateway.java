package ec.uce.propuestas.documento;

import ec.uce.propuestas.documentos.contract.SpecificationProjectionV1;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/** Test-only DOCX renderer for legacy parse-back fixtures; never available in production. */
@Alternative
@IfBuildProperty(name = "documentos.test-local-render", stringValue = "true")
@Priority(1)
@ApplicationScoped
public class LegacyEspecificacionesRenderTestGateway implements EspecificacionesRenderGateway {
    @Override
    public byte[] render(SpecificationProjectionV1 projection) {
        try (var doc = new XWPFDocument();
                var output = new ByteArrayOutputStream()) {
            var title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            var titleRun = title.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(18);
            titleRun.setText(projection.titulo1());
            var subtitle = doc.createParagraph();
            subtitle.setAlignment(ParagraphAlignment.CENTER);
            var subtitleRun = subtitle.createRun();
            subtitleRun.setBold(true);
            subtitleRun.setFontSize(16);
            subtitleRun.setText(projection.titulo2());
            var project = doc.createParagraph();
            project.setAlignment(ParagraphAlignment.CENTER);
            var projectRun = project.createRun();
            projectRun.setFontSize(10);
            projectRun.setText("Proyecto: " + projection.nombreProyecto() + " · Año: " + projection.anio()
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
}
