package ec.uce.propuestas.documento.exportacion;

/** Límites de tamaño de la frontera HTTP Core ↔ Documentos. */
public final class DocumentosLimites {

    private DocumentosLimites() {}

    /** Máximo de una proyección o cuerpo de exportación enviado/aceptado. */
    public static final int MAX_REQUEST_BYTES = 8 * 1024 * 1024;

    /** Máximo de un documento generado. */
    public static final int MAX_RESPONSE_BYTES = 64 * 1024 * 1024;
}
