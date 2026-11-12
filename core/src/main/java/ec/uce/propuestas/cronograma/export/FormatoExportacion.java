package ec.uce.propuestas.cronograma.export;

/**
 * Plan 031 (P-37) — enumeración cerrada de formatos de exportación soportados
 * por el módulo {@code documento} del cronograma. Vive en el paquete export
 * porque los writers se acoplan por formato y porque el parser del query
 * param {@code formato} lo usa para distinguir 400 validacion de
 * {@code xlsx|pdf|mspdi}.
 *
 * <p>Reglas (Plan 026 §4 + Plan 031 §G4):</p>
 * <ul>
 *   <li>{@link #XLSX} → {@code application/vnd.openxmlformats-officedocument.spreadsheetml.sheet}.</li>
 *   <li>{@link #PDF} → {@code application/pdf}.</li>
 *   <li>{@link #MSPDI} → {@code application/xml}; lane separado, exige
 *       {@code Proyecto.fechaInicio}.</li>
 * </ul>
 */
public enum FormatoExportacion {
    XLSX("xlsx"),
    PDF("pdf"),
    MSPDI("mspdi");

    private final String token;

    FormatoExportacion(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    /** Parsea el query param; case-insensitive, blank = {@code null}. 400 si no matchea. */
    public static FormatoExportacion parsear(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalizado = raw.trim().toLowerCase();
        for (FormatoExportacion f : values()) {
            if (f.token.equals(normalizado)) {
                return f;
            }
        }
        return null;
    }
}
