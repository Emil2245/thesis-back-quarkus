package ec.uce.propuestas.documento.service;

import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.service.PresupuestoService;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.repository.ProyectoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

@ApplicationScoped
public class ExportEspecificacionesService {

    @Inject
    ApuRepository apuRepository;

    @Inject
    PresupuestoService presupuestoService;

    @Inject
    ProyectoRepository proyectoRepository;

    public byte[] generarDocx(Long presupuestoId, String titulo1Override, String titulo2Override) throws IOException {
        Presupuesto p = presupuestoService.validar(presupuestoId);
        Proyecto proy = proyectoRepository.findByIdOptional(p.proyectoId).orElseThrow();
        List<Apu> apus = apuRepository.list("presupuestoId", presupuestoId);

        String titulo1 = titulo1Override != null
                ? titulo1Override
                : (proy.tituloEt1 != null ? proy.tituloEt1 : "ESPECIFICACIONES TECNICAS");
        String titulo2 = titulo2Override != null
                ? titulo2Override
                : (proy.tituloEt2 != null ? proy.tituloEt2 : proy.nombreProyecto);

        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph t1 = doc.createParagraph();
            t1.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun r1 = t1.createRun();
            r1.setBold(true);
            r1.setFontSize(16);
            r1.setText(titulo1);

            XWPFParagraph t2 = doc.createParagraph();
            t2.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun r2 = t2.createRun();
            r2.setFontSize(14);
            r2.setText(titulo2);

            doc.createParagraph();

            for (Apu apu : apus) {
                XWPFParagraph apuHeader = doc.createParagraph();
                XWPFRun hr = apuHeader.createRun();
                hr.setBold(true);
                hr.setFontSize(12);
                hr.setText(apu.codigo + " - " + apu.descripcion);

                XWPFParagraph body = doc.createParagraph();
                XWPFRun br = body.createRun();
                if (apu.especificacionTecnica != null && !apu.especificacionTecnica.isBlank()) {
                    br.setText(apu.especificacionTecnica);
                } else {
                    br.setItalic(true);
                    br.setText("(Sin especificación técnica)");
                }

                doc.createParagraph();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }
}
