package ec.uce.propuestas.documento.exportacion;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.cronograma.export.CronogramaExportPreflightService;
import ec.uce.propuestas.cronograma.repository.CronogramaRepository;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import ec.uce.propuestas.presupuesto.service.ValidacionPresupuestoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.FlushMode;
import org.hibernate.Session;

/** Frontera CDI independiente: no comparte entidades con la transacción suspendida. */
@ApplicationScoped
public class CapturaDocumentoService {
    @Inject
    PresupuestoRepository presupuestos;

    @Inject
    RubroRepository rubros;

    @Inject
    ValidacionPresupuestoService validacion;

    @Inject
    CronogramaRepository cronogramas;

    @Inject
    CronogramaExportPreflightService stale;

    @Inject
    ProyeccionDocumentoService proyeccion;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public SnapshotDocumento capturar(UUID id, Long caller, String documento, OpcionesDocumento opciones) {
        return capturarDentro(id, caller, documento, opciones, () -> {});
    }

    /** Seam acotado de prueba, sin observer CDI ni API pública; mismo pipeline real. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    SnapshotDocumento capturar(
            UUID id, Long caller, String documento, OpcionesDocumento opciones, Runnable despuesDeOwnership) {
        return capturarDentro(id, caller, documento, opciones, despuesDeOwnership);
    }

    private SnapshotDocumento capturarDentro(
            UUID id, Long caller, String documento, OpcionesDocumento opciones, Runnable despuesDeOwnership) {
        Session session = presupuestos.getEntityManager().unwrap(Session.class);
        session.setHibernateFlushMode(FlushMode.MANUAL);
        session.setDefaultReadOnly(true);
        // La conexión de Hibernate ya enlistada establece aislamiento antes de cualquier SELECT.
        // SET TRANSACTION no altera el datasource ni la próxima transacción del pool.
        session.doWork(connection -> {
            try (var statement = connection.createStatement()) {
                statement.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ, READ ONLY");
            }
        });
        var presupuesto = presupuestos
                .findByPublicIdAndOwnerScope(id, caller)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));
        despuesDeOwnership.run();
        var seleccion = rubros.listarPorPresupuesto(presupuesto.id);
        var bloqueos = PreflightDocumentoService.evaluarBloqueos(
                validacion.validar(id, caller), seleccion.isEmpty(), documento.equals("apus") && seleccion.isEmpty());
        List<PreflightDocumento.Detalle> warnings = new ArrayList<>();
        cronogramas.findByPresupuestoAndOwnerScope(presupuesto.id, caller).ifPresent(c -> {
            if (stale.esStale(c, presupuesto))
                warnings.add(new PreflightDocumento.Detalle(
                        "cronograma-desactualizado",
                        "El total o fingerprint del presupuesto cambió desde la última revisión explícita",
                        List.of()));
        });
        var preflight = new PreflightDocumento(
                presupuesto.publicId,
                presupuesto.version,
                documento,
                opciones.formato(),
                opciones.opciones(),
                bloqueos.isEmpty(),
                bloqueos,
                warnings);
        return proyeccion.proyectar(presupuesto, caller, preflight);
    }
}
