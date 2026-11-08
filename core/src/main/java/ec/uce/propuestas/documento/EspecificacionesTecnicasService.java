package ec.uce.propuestas.documento;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;

/** Coordinates capture, remote rendering and post-success audit without a transaction over HTTP. */
@ApplicationScoped
public class EspecificacionesTecnicasService {
    @Inject
    EspecificacionesCapturaService captura;

    @Inject
    EspecificacionesRenderGateway renderer;

    @Inject
    EspecificacionesAuditService auditoria;

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public ArchivoGenerado generar(UUID presupuestoId, String email, String titulo1, String titulo2) {
        var captured = captura.capturar(presupuestoId, email, titulo1, titulo2);
        byte[] bytes = renderer.render(captured.projection());
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("Documentos no devolvió bytes de especificaciones técnicas");
        }
        auditoria.registrar(captured.callerId(), presupuestoId, bytes.length);
        String filename = "especificaciones-tecnicas-" + captured.proyectoInternoId() + "-"
                + captured.projection().fechaExportacion() + ".docx";
        return new ArchivoGenerado(bytes, filename, ArchivoGenerado.DOCX_MEDIA_TYPE);
    }
}
