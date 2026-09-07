package ec.uce.propuestas.cronograma.export;

import ec.uce.propuestas.cronograma.dto.ProyeccionExportacion;
import ec.uce.propuestas.cronograma.export.CronogramaExportPreflightService.BloqueosCalculador;
import ec.uce.propuestas.cronograma.export.CronogramaExportPreflightService.ResultadoBloqueos;
import ec.uce.propuestas.cronograma.export.CronogramaExportPreflightService.SnapshotCompleto;
import ec.uce.propuestas.documento.ArchivoGenerado;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;

/**
 * Plan 031 (P-37) — punto de entrada único y transaccional para la descarga
 * documental del cronograma. Cierra la grieta TOCTOU entre preflight, proyección
 * y render de bytes (Plan 031 §TOCTOU): una sola transacción REQUIRED mantiene
 * el lock pesimista del presupuesto hasta que los bytes ya están materializados.
 *
 * <p>Arquitectura:
 *
 * <pre>
 *   CronogramaDocumentoResource.descargar(...)
 *     └─ CronogramaDescargaService.generar(...) @Transactional REQUIRED
 *           ├─ preflightService.cargarSnapshot(...) @Transactional REQUIRED   (join)
 *           ├─ BloqueosCalculador.evaluar(...)   (puro, sin JTA)
 *           └─ si exportable:
 *                ├─ proyeccionService.construirProyeccionCompleta(...) @Transactional REQUIRED (join)
 *                └─ CronogramaXlsxWriter.renderizar(...)  ó CronogramaPdfWriter.renderizar(...)
 *                ó
 *                ├─ proyeccionService.construirProyeccionMspdi(...) @Transactional REQUIRED (join)
 *                └─ CronogramaMspdiWriter.renderizar(...)
 * </pre>
 *
 * <p>Reglas:
 * <ul>
 *   <li>El lock pesimista del presupuesto se toma dentro de
 *       {@link CronogramaExportPreflightService#cargarSnapshot(UUID, Long)} y
 *       permanece hasta el commit de {@link #generar(UUID, Long, FormatoExportacion)};
 *       por lo tanto los writers (XLSX/PDF/MSPDI) corren dentro de la sección
 *       crítica y NO pueden ver una mutación intermedia (TOCTOU cerrado).</li>
 *   <li>Los writers reciben una {@link ProyeccionExportacion} ya calculada y
 *       una sola {@link SnapshotCompleto} por invocación; nunca se filtra la
 *       entidad gestionada fuera del contexto transaccional.</li>
 * </ul>
 *
 * <p>El método se marca {@code @Blocking} a nivel de resource para evitar que la
 * serialización XLSX/PDF/MSPDI corra en el event loop de RESTEasy Reactive
 * (Plan 031 §NFR).</p>
 */
@ApplicationScoped
public class CronogramaDescargaService {

    @Inject
    CronogramaExportPreflightService preflightService;

    @Inject
    CronogramaProyeccionExportService proyeccionService;

    /**
     * Resultado de la generación. Si {@code exportable=false}, {@code bytes} es
     * {@code null} y el resource debe responder 409 con el cuerpo tipado
     * {@link ec.uce.propuestas.cronograma.dto.BloqueoExportDetalle}. Si
     * {@code exportable=true}, {@code bytes} contiene el archivo y
     * {@code bloqueos} está vacío.
     *
     * <p>El snapshot NO se expone fuera de la transacción: el resource sólo
     * recibe bytes + bloqueos + flag stale para construir la respuesta HTTP.
     * Esto evita pasar entidades gestionadas entre transacciones (regla
     * TOCTOU/DETACHED).</p>
     */
    public record ResultadoDescarga(
            ResultadoBloqueos bloqueos,
            boolean desactualizado,
            boolean exportable,
            FormatoExportacion formato,
            byte[] bytes,
            String extension,
            String mediaType,
            String filename) {}

    /**
     * Genera los bytes del documento bajo una sola transacción. El lock
     * pesimista del presupuesto se mantiene hasta el commit final, de modo que
     * la proyección que alimenta al writer está congelada y coincide con el
     * fingerprint que vio el preflight (Plan 031 §TOCTOU).
     *
     * @param presupuestoPublicId UUIDv7 del presupuesto (validado por el resource)
     * @param callerUsuarioId     usuario autenticado (validado por el resource)
     * @param formato             formato documental solicitado
     * @return bytes + bloqueos + flag stale; nunca entidades gestionadas
     */
    @Transactional
    public ResultadoDescarga generar(UUID presupuestoPublicId, Long callerUsuarioId, FormatoExportacion formato) {
        if (formato == null) {
            throw new IllegalArgumentException("formato es obligatorio");
        }
        // (1) Cargar snapshot bajo el lock pesimista del presupuesto. Método
        //     @Transactional(REQUIRED): se une a esta misma transacción.
        SnapshotCompleto snap = preflightService.cargarSnapshot(presupuestoPublicId, callerUsuarioId);
        // (2) Misma regla que el preflight: P-32 + borrador + stale + MSPDI-fecha.
        boolean desactualizado = preflightService.esStale(snap);
        ResultadoBloqueos bloqueos = BloqueosCalculador.evaluar(snap.comun(), formato, desactualizado);
        // (3) Si no es exportable, devolvemos bytes=null para que el resource
        //     emita 409 export-bloqueado. La entidad Cronograma no viaja fuera
        //     de la tx: sólo bloqueos y stale llegan al cliente.
        if (!bloqueos.exportable()) {
            return new ResultadoDescarga(bloqueos, desactualizado, false, formato, null, null, null, null);
        }
        // (4) Render dentro de la misma tx. La proyección que llega al writer
        //     está construida sobre las mismas filas bloqueadas.
        byte[] bytes;
        String extension;
        String mediaType;
        switch (formato) {
            case XLSX -> {
                ProyeccionExportacion p = proyeccionService.construirProyeccionCompleta(snap);
                bytes = CronogramaXlsxWriter.renderizar(p);
                extension = "xlsx";
                mediaType = ArchivoGenerado.XLSX_MEDIA_TYPE;
            }
            case PDF -> {
                ProyeccionExportacion p = proyeccionService.construirProyeccionCompleta(snap);
                bytes = CronogramaPdfWriter.renderizar(p);
                extension = "pdf";
                mediaType = ArchivoGenerado.PDF_MEDIA_TYPE;
            }
            case MSPDI -> {
                CronogramaMspdiWriter.ProyeccionMspdi m = proyeccionService.construirProyeccionMspdi(snap);
                bytes = CronogramaMspdiWriter.renderizar(m);
                extension = "xml";
                mediaType = ArchivoGenerado.MSPDI_MEDIA_TYPE;
            }
            default -> throw new IllegalArgumentException("Formato no soportado: " + formato);
        }
        // (5) Construir filename desde el snapshot, dentro de la misma tx, para
        //     que el resource no tenga que cruzar la frontera con entidades
        //     gestionadas.
        String filename = construirFilename(snap, extension);
        return new ResultadoDescarga(bloqueos, desactualizado, true, formato, bytes, extension, mediaType, filename);
    }

    /**
     * Construye un nombre de archivo seguro: usa codigo y nombre del proyecto
     * saneados (sin BIGINT, sin path traversal, sin reserved Windows), mas la
     * version del presupuesto. Si la combinacion codigo+nombre produce una
     * cadena vacia o solo subrayados/guiones, cae a {@code cronograma}. Vive
     * en el service para que la proyeccion no salga de la transaccion.
     *
     * <p>Reglas aplicadas (audit closure §filename):
     *
     * <ul>
     *   <li>Sin secuencias de BIGINT (≥ 6 dígitos consecutivos).
     *   <li>Sin path traversal: cualquier {@code ..} se aplana a {@code _}.
     *   <li>Sin caracteres prohibidos por Windows/Unix en filenames
     *       ({@code \ / : * ? " < > |}).
     *   <li>Sin nombres reservados de Windows ({@code CON}, {@code PRN},
     *       {@code AUX}, {@code NUL}, {@code COM1}…{@code COM9},
     *       {@code LPT1}…{@code LPT9}), case-insensitive.
     *   <li>Si el resultado saneado termina compuesto solo por
     *       {@code _} o {@code -}, cae a {@code cronograma}.
     *   <li>Longitud máxima 80 caracteres (sin extensión) para no rebasar
     *       límites de filesystem.
     *   <li>Los espacios se reemplazan por {@code _} (CLI/shell friendly).
     * </ul>
     */
    static String construirFilename(SnapshotCompleto snap, String extension) {
        String codigo = snap.proyecto().codigo == null ? "" : snap.proyecto().codigo;
        String nombre = snap.proyecto().nombreProyecto == null ? "" : snap.proyecto().nombreProyecto;
        String base = (codigo + "-" + nombre)
                // Secuencias de BIGINT (≥ 6 dígitos consecutivos).
                .replaceAll("\\d{6,}", "")
                // Caracteres prohibidos por Windows + separadores Unix.
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                // Espacios → _ (CLI/shell friendly).
                .replaceAll("\\s+", "_")
                // Path traversal: secuencias '.' repetidas.
                .replaceAll("\\.+", "_")
                // Colapsar separadores repetidos.
                .replaceAll("_+", "_")
                .replaceAll("-+", "-")
                .replaceAll("_+-+|_-+|-+_+", "-");
        // Quitar separadores al borde.
        base = base.replaceAll("^[_-]+", "").replaceAll("[_-]+$", "");
        // Longitud máxima razonable.
        if (base.length() > 80) {
            base = base.substring(0, 80).replaceAll("[_-]+$", "");
        }
        // Nombres reservados Windows.
        if (esNombreReservadoWindows(base)) {
            base = "cronograma";
        }
        // Si quedó vacío o compuesto sólo de separadores, fallback.
        if (base.isBlank() || base.matches("^[_-]+$")) {
            base = "cronograma";
        }
        String version = snap.presupuesto().version == null ? "1" : Short.toString(snap.presupuesto().version);
        return base + "-v" + version + "." + extension;
    }

    /**
     * Devuelve true si la base (sin extensión) es un nombre reservado de
     * Windows. Windows reserva CON, PRN, AUX, NUL, COM1..COM9, LPT1..LPT9
     * con o sin extensión (case-insensitive). Comparamos la base exacta
     * (sin separadores al borde).
     */
    private static boolean esNombreReservadoWindows(String base) {
        if (base == null || base.isBlank()) {
            return false;
        }
        String upper = base.toUpperCase(java.util.Locale.ROOT);
        if (upper.equals("CON") || upper.equals("PRN") || upper.equals("AUX") || upper.equals("NUL")) {
            return true;
        }
        // COM1..COM9 y LPT1..LPT9 — comparación exacta.
        for (int i = 1; i <= 9; i++) {
            if (upper.equals("COM" + i) || upper.equals("LPT" + i)) {
                return true;
            }
        }
        return false;
    }
}
