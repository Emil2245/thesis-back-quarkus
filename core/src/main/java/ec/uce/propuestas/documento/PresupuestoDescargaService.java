package ec.uce.propuestas.documento;

import ec.uce.propuestas.documento.exportacion.CapturaDocumentoService;
import ec.uce.propuestas.documento.exportacion.OpcionesDocumento;
import ec.uce.propuestas.documento.exportacion.PreflightDocumento;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;

/** Una captura atómica; el render puro empieza cuando su transacción ya terminó. */
@ApplicationScoped
public class PresupuestoDescargaService {
    @Inject
    CapturaDocumentoService captura;

    public record Resultado(PreflightDocumento preflight, ArchivoGenerado archivo) {}

    // Suspend any caller transaction too: serialization must never hold database locks.
    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public Resultado generar(UUID id, Long caller, OpcionesDocumento opciones) {
        var snapshot = captura.capturar(id, caller, "presupuesto", opciones);
        var preflight = snapshot.preflight();
        if (!preflight.exportable()) return new Resultado(preflight, null);
        boolean pdf = "pdf".equals(preflight.formato());
        var capturadas = new OpcionesDocumento(preflight.formato(), preflight.opciones());
        byte[] bytes = pdf
                ? PresupuestoPdfWriter.renderizar(snapshot, capturadas)
                : PresupuestoXlsxWriter.renderizar(snapshot, capturadas);
        String proyecto =
                snapshot.proyecto().nombre() == null ? "" : snapshot.proyecto().nombre();
        // ASCII allowlist excludes controls, quotes, path separators and traversal.
        String base = proyecto.replaceAll("[^A-Za-z0-9_-]", "_").replaceAll("_+", "_");
        if (base.length() > 48) base = base.substring(0, 48);
        String filename = "presupuesto-" + base + "-" + snapshot.presupuestoId() + "-v" + snapshot.version()
                + (pdf ? ".pdf" : ".xlsx");
        return new Resultado(
                preflight,
                new ArchivoGenerado(
                        bytes, filename, pdf ? ArchivoGenerado.PDF_MEDIA_TYPE : ArchivoGenerado.XLSX_MEDIA_TYPE));
    }
}
