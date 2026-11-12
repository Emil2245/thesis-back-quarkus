package ec.uce.propuestas.documento.exportacion;

/** Content-Type oficiales de los documentos exportados. */
public final class DocumentoMediaTypes {

    private DocumentoMediaTypes() {}

    /** Word moderno (.docx, ECMA-376 / OOXML). */
    public static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /** Plan 031 (P-37) — XLSX (OOXML spreadsheet). */
    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** Plan 031 (P-37) — PDF. */
    public static final String PDF = "application/pdf";

    /** Plan 031 (P-37) — XML MSPDI (Lane C). */
    public static final String MSPDI = "application/xml";
}
