package ec.uce.propuestas.documento;

/**
 * Bytes de un documento generado por el módulo {@code documento}
 * (08-codebase-design.md §4), más los metadatos mínimos que el resource
 * necesita para entregar el stream al cliente (Content-Type + Content-Disposition).
 *
 * @param bytes       contenido del archivo (XLSX/PDF/DOCX)
 * @param nombreArchivo nombre sugerido para la descarga (sin ruta)
 * @param mediaType   Content-Type del archivo (p. ej. {@code application/vnd.openxmlformats-officedocument.wordprocessingml.document})
 */
public record ArchivoGenerado(byte[] bytes, String nombreArchivo, String mediaType) {

    /** Media type oficial para archivos Word modernos (.docx, ECMA-376 / OOXML). */
    public static final String DOCX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /** Plan 031 (P-37) — media type oficial para XLSX (OOXML spreadsheet). */
    public static final String XLSX_MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Plan 031 (P-37) — media type oficial para PDF. */
    public static final String PDF_MEDIA_TYPE = "application/pdf";

    /** Plan 031 (P-37) — media type para el XML MSPDI (Lane C). */
    public static final String MSPDI_MEDIA_TYPE = "application/xml";
}
