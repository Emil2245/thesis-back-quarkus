package ec.uce.propuestas.documento;

import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

/**
 * P-45 (N04 §ESP + N04-bis): genera el documento Word (.docx) de
 * Especificaciones Técnicas de un proyecto a partir de las ETs de sus APUs.
 *
 * <p>Es el primer writer del módulo {@code documento} (08-codebase-design.md §4),
 * específico para la variante DOCX de ETs. La interfaz {@code generar(tipo,
 * formato, datos) → ArchivoGenerado} se cubre aquí con un método concreto para
 * este caso; los writers de presupuesto/APU/cronograma viven en iteraciones
 * posteriores (P-37).</p>
 *
 * <p><b>Reglas de contenido (04-export-sercop-spec.md §7, 03-procesos-detalle.md P-45):</b>
 * <ul>
 *   <li>Solo se incluyen APUs con {@code especificacionTecnica} no nula y no vacía.</li>
 *   <li>Título 1 = override del query {@code titulo1} → {@code Proyecto.titulo_et_1} no vacío → literal
 *       {@code "ESPECIFICACIONES TÉCNICAS"}.</li>
 *   <li>Título 2 = override del query {@code titulo2} → {@code Proyecto.titulo_et_2} no vacío →
 *       {@code Proyecto.nombreProyecto}.</li>
 *   <li><b>No se incluyen campos monetarios</b> (sin costo directo/indirecto/total, sin precios,
 *       sin subtotales). El documento es estrictamente técnico, sin pricing (§7 del spec).</li>
 *   <li>Numeración correlativa por APU en el orden por {@code codigo}.</li>
 * </ul>
 * </p>
 *
 * <p>El detalle del formato (.docx ECMA-376) lo cubre Apache POI
 * ({@code poi-ooxml}, ya en dependencias); no se añade ninguna dependencia nueva.</p>
 */
@ApplicationScoped
public class EspecificacionesTecnicasService {

    private static final String TITULO1_DEFAULT = "ESPECIFICACIONES TÉCNICAS";
    private static final DateTimeFormatter FECHA_FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Inject
    ApuRepository apuRepository;

    /**
     * Genera el .docx con las ETs del proyecto al que pertenece el presupuesto.
     *
     * @param presupuestoId id (Long) del presupuesto; debe pertenecer al usuario autenticado
     * @param proyecto      proyecto dueño del presupuesto (resuelto y validado por el resource)
     * @param titulo1Override override del query {@code titulo1} (nullable/blank → ignorar)
     * @param titulo2Override override del query {@code titulo2} (nullable/blank → ignorar)
     * @return archivo .docx listo para streaming
     * @throws ProblemaException 400 {@code validacion} si no hay APUs con ET en el presupuesto
     */
    public ArchivoGenerado generar(
            Long presupuestoId, Proyecto proyecto, String titulo1Override, String titulo2Override) {
        List<Apu> apus = apuRepository.listarConEspecificacionTecnica(presupuestoId).stream()
                .filter(a -> a.especificacionTecnica != null && !a.especificacionTecnica.isBlank())
                .toList();
        if (apus.isEmpty()) {
            throw ProblemaException.validacion(
                    "El presupuesto no contiene APUs con Especificaciones Técnicas para exportar");
        }

        String titulo1 = resolverTitulo(titulo1Override, proyecto.tituloEt1, TITULO1_DEFAULT);
        String titulo2 = resolverTitulo(titulo2Override, proyecto.tituloEt2, proyecto.nombreProyecto);
        LocalDate hoy = LocalDate.now();
        String fechaExportacion = FECHA_FORMATO.format(hoy);

        byte[] bytes;
        try (XWPFDocument doc = new XWPFDocument();
                ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            escribirCabecera(doc, titulo1, titulo2, proyecto, fechaExportacion);
            int n = 1;
            for (Apu apu : apus) {
                escribirSeccion(doc, n++, apu);
            }
            escribirPie(doc, fechaExportacion);
            doc.write(baos);
            bytes = baos.toByteArray();
        } catch (IOException ioe) {
            throw new RuntimeException("No se pudo generar el documento de Especificaciones Técnicas", ioe);
        }

        String nombreArchivo = "especificaciones-tecnicas-" + proyecto.id + "-" + fechaExportacion + ".docx";
        return new ArchivoGenerado(bytes, nombreArchivo, ArchivoGenerado.DOCX_MEDIA_TYPE);
    }

    /** Override > proyecto (no vacío) > default; blank se trata como ausente. */
    private static String resolverTitulo(String override, String proyectoTitulo, String defaultTitulo) {
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        if (proyectoTitulo != null && !proyectoTitulo.isBlank()) {
            return proyectoTitulo.trim();
        }
        return defaultTitulo;
    }

    private static void escribirCabecera(
            XWPFDocument doc, String titulo1, String titulo2, Proyecto proyecto, String fecha) {
        XWPFParagraph t1 = doc.createParagraph();
        t1.setAlignment(ParagraphAlignment.CENTER);
        negritaCentrado(t1, titulo1, 18);

        XWPFParagraph t2 = doc.createParagraph();
        t2.setAlignment(ParagraphAlignment.CENTER);
        negritaCentrado(t2, titulo2, 16);

        XWPFParagraph sub = doc.createParagraph();
        sub.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = sub.createRun();
        r.setFontSize(10);
        r.setText("Proyecto: " + proyecto.nombreProyecto + " · Año: " + proyecto.anio + " · Fecha de exportación: "
                + fecha);

        doc.createParagraph(); // separador
    }

    private static void escribirSeccion(XWPFDocument doc, int n, Apu apu) {
        XWPFParagraph header = doc.createParagraph();
        XWPFRun hr = header.createRun();
        hr.setBold(true);
        hr.setFontSize(13);
        hr.setText(n + ". " + apu.codigo + " — " + apu.descripcion + " [" + apu.unidad + "]");

        XWPFParagraph cuerpo = doc.createParagraph();
        XWPFRun cr = cuerpo.createRun();
        cr.setFontSize(11);
        cr.setText(apu.especificacionTecnica);
    }

    private static void escribirPie(XWPFDocument doc, String fecha) {
        doc.createParagraph();
        XWPFParagraph pie = doc.createParagraph();
        XWPFRun r = pie.createRun();
        r.setFontSize(9);
        r.setItalic(true);
        r.setText("Generado por Sistema APU · " + fecha);
    }

    private static void negritaCentrado(XWPFParagraph p, String texto, int tam) {
        XWPFRun r = p.createRun();
        r.setBold(true);
        r.setFontSize(tam);
        r.setText(texto);
    }
}
