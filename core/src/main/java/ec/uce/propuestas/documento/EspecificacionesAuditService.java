package ec.uce.propuestas.documento;

import ec.uce.propuestas.usuario.audit.EventoLogActividad;
import ec.uce.propuestas.usuario.audit.service.LogActividadService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.UUID;

/** Commits the export event only after the remote renderer returns bytes. */
@ApplicationScoped
public class EspecificacionesAuditService {
    @Inject
    LogActividadService logActividadService;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void registrar(Long callerId, UUID presupuestoId, int size) {
        logActividadService.emitir(
                callerId,
                EventoLogActividad.DOCUMENTO_EXPORTADO,
                "presupuesto",
                presupuestoId,
                Map.of("formato", "DOCX", "bytes", (long) size, "stale", false));
    }
}
