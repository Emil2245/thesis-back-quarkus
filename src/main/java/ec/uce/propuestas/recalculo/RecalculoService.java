package ec.uce.propuestas.recalculo;

import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.service.ApuCalculoService;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.motor.CapituloConTotal;
import ec.uce.propuestas.motor.Motor;
import ec.uce.propuestas.motor.RubroConPrecio;
import ec.uce.propuestas.motor.VersionCalculada;
import ec.uce.propuestas.presupuesto.entity.Capitulo;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.repository.CapituloRepository;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import ec.uce.propuestas.recalculo.internal.VersionSnapshotBuilder;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Módulo profundo {@code recalculo} (08-codebase-design.md §3).
 *
 * <p>Convierte «qué cambió» en derivados persistidos: los recursos REST
 * declaran el {@link Alcance} y este servicio decide qué invocar del motor
 * y qué columnas reescribir.
 *
 * <p>Cobertura:
 * <ul>
 *   <li>{@link Alcance.Version}: versión completa → totales raíz
 *       (reusa {@code Motor.consolidar} con el snapshot reducido). Es
 *       el alcance que declaran las mutaciones estructurales del agregado
 *       {@code Presupuesto → Capitulo → Rubro} (P-28/29/31); la propagación
 *       grano fino (capítulo, rubro) se sirve recorriendo el árbol por
 *       alcance de versión completa.</li>
 *   <li>{@link Alcance.Apu}: APU individual → reusa
 *       {@code ApuCalculoService.recalcular(apu)} (write-through APU) y, si
 *       está vinculado a un rubro, consolida la versión completa. Así la
 *       frontera APU→Rubro permanece exclusivamente en
 *       {@code Motor.consolidar}; no se replica {@code setScale} ni se
 *       accede a helpers internos del motor. Si el APU no tiene rubro,
 *       termina tras actualizar sólo el APU.</li>
 *   <li>{@link Alcance.Insumo}: insumo → resuelve los APUs que heredan
 *       el precio (override NULL), agrupa sus {@code presupuesto_id}
 *       distintos y consolida cada versión afectada una sola vez. No se
 *       inventa un umbral: primero se implementa el camino correcto y
 *       determinista; una optimización futura exige medición y plan.</li>
 * </ul>
 *
 * <p>El servicio es transaccional: si el recálculo falla, la mutación que
 * lo invocó aborta (rollback). La propagación es REQUIRED (default): se
 * une a la transacción del caller y crea una sólo para invocaciones
 * directas de integración.
 */
@ApplicationScoped
public class RecalculoService {

    @Inject
    ApuRepository apuRepository;

    @Inject
    ApuCalculoService apuCalculoService;

    @Inject
    RubroRepository rubroRepository;

    @Inject
    CapituloRepository capituloRepository;

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    VersionSnapshotBuilder snapshotBuilder;

    @Inject
    ec.uce.propuestas.cronograma.service.CronogramaSincronizacionService cronogramaSincronizacionService;

    @Transactional
    public void recalcular(Alcance alcance) {
        switch (alcance) {
            case Alcance.Version v -> recalcularVersion(v.presupuestoId());
            case Alcance.Apu a -> recalcularApu(a.apuId());
            case Alcance.Insumo i -> recalcularInsumo(i.insumoId());
        }
    }

    private void recalcularApu(Long apuId) {
        Apu apu = apuRepository.findById(apuId);
        if (apu == null) {
            throw ProblemaException.noEncontrado("APU no encontrado");
        }
        // 1) write-through APU (costoDirecto/costoIndirecto/costoTotal + subtotales)
        apuCalculoService.recalcular(apu);

        // 2) ¿está vinculado a un rubro? Si no, terminamos.
        Rubro rubro = rubroRepository
                .find("apuId = :apuId", Parameters.with("apuId", apuId))
                .firstResult();
        if (rubro == null) {
            return;
        }

        // 3) consolidación de la versión completa (rubro → capítulo → presupuesto)
        Long presupuestoId = presupuestoIdDeCapitulo(rubro.capituloId);
        if (presupuestoId != null) {
            consolidarVersionYPropagar(presupuestoId);
        }
    }

    private void recalcularInsumo(Long insumoId) {
        // Resolver APUs que heredan el insumo (override NULL para la sección
        // correspondiente). Cada APU se recalcula; las versiones afectadas se
        // consolidan exactamente una vez (deduplicado por presupuestoId).
        Set<Long> versionesAfectadas = new LinkedHashSet<>();

        // N04 §A1 FORMA 2: editar el insumo afecta sólo a los APUs que lo
        // referencian sin override explícito. En la BD, "sin override" se ve
        // como la columna de override (tarifa_jornal para MO/EQUIPO,
        // precio_unitario_tarifa para MATERIAL/TRANSPORTE) en NULL y la
        // sección apuntando al insumo.
        List<Apu> apus = apuRepository
                .getEntityManager()
                .createQuery(
                        "select distinct a from Apu a, ApuSeccion s, ApuDetalle d "
                                + "where d.insumoId = :iid and d.seccionId = s.id and s.apuId = a.id "
                                + "and (d.tarifaJornal is null and s.tipo in ('EQUIPO','MANO_OBRA') "
                                + "  or d.precioUnitarioTarifa is null and s.tipo in ('MATERIAL','TRANSPORTE'))",
                        Apu.class)
                .setParameter("iid", insumoId)
                .getResultList();
        for (Apu apu : apus) {
            apuCalculoService.recalcular(apu);
            // APU vinculado a rubro?
            Rubro rubro = rubroRepository
                    .find("apuId = :apuId", Parameters.with("apuId", apu.id))
                    .firstResult();
            if (rubro != null) {
                Long presupuestoId = presupuestoIdDeCapitulo(rubro.capituloId);
                if (presupuestoId != null) {
                    versionesAfectadas.add(presupuestoId);
                }
            }
        }
        for (Long presupuestoId : versionesAfectadas) {
            consolidarVersionYPropagar(presupuestoId);
        }
    }

    private void recalcularVersion(Long presupuestoId) {
        Presupuesto presupuesto = presupuestoRepository.findById(presupuestoId);
        if (presupuesto == null) {
            throw ProblemaException.noEncontrado("Presupuesto no encontrado");
        }
        consolidarVersionYPropagar(presupuestoId);
    }

    /**
     * Carga el snapshot del motor, ejecuta {@code Motor.consolidar} y persiste
     * los derivados: {@code rubro.precio_unitario} / {@code precio_total},
     * {@code capitulo.total} recursivo y {@code presupuesto.total}.
     */
    private void consolidarVersionYPropagar(Long presupuestoId) {
        VersionCalculada calc = Motor.consolidar(snapshotBuilder.build(presupuestoId));

        // Map rubroCodigo -> RubroConPrecio (mismo orden de aparición que en
        // Capítulos — el builder preserva el árbol).
        Map<String, RubroConPrecio> rubrosPorCodigo = new LinkedHashMap<>();
        for (RubroConPrecio r : calc.rubros()) {
            rubrosPorCodigo.put(r.codigo(), r);
        }

        // Map capitulo.item -> CapituloConTotal (recorrido in-order del motor).
        Map<String, CapituloConTotal> totalesPorItem = new LinkedHashMap<>();
        for (CapituloConTotal c : calc.capitulos()) {
            totalesPorItem.put(c.item(), c);
        }

        // Persistir rubros (precio_unitario, precio_total)
        List<Capitulo> todosCapitulos = capituloRepository
                .find("presupuestoId = :pid", Parameters.with("pid", presupuestoId))
                .list();
        Map<Long, BigDecimal> totalPorCapituloId = new HashMap<>();
        for (Capitulo cap : todosCapitulos) {
            for (Rubro rubro : rubroRepository
                    .find("capituloId = :cid", Parameters.with("cid", cap.id))
                    .list()) {
                RubroConPrecio calculado = rubrosPorCodigo.get(rubro.codigo);
                if (calculado != null) {
                    rubro.precioUnitario = calculado.precioUnitario();
                    rubro.precioTotal = calculado.precioTotal();
                    rubroRepository.persist(rubro);
                }
            }
        }

        // Persistir totales de capítulo
        for (Capitulo cap : todosCapitulos) {
            String key = cap.item;
            BigDecimal total = null;
            for (Map.Entry<String, CapituloConTotal> e : totalesPorItem.entrySet()) {
                if (e.getValue().item().equals(key) && depthCoherente(e.getValue(), cap)) {
                    total = e.getValue().total();
                    break;
                }
            }
            if (total == null) {
                total = BigDecimal.ZERO;
            }
            cap.total = total;
            capituloRepository.persist(cap);
            totalPorCapituloId.put(cap.id, total);
        }

        // Presupuesto total
        Presupuesto presupuesto = presupuestoRepository.findById(presupuestoId);
        presupuesto.total = calc.totalGeneral();
        presupuestoRepository.persist(presupuesto);

        // Plan 029 — sincronización 1:1 rubro↔actividad dentro de la misma
        // transacción. La ausencia de cronograma es un no-op; los rubros
        // eliminados borran su actividad por FK CASCADE; los rubros nuevos
        // reciben una actividad con mapa {}; los pesos se recalculan con el
        // totalGeneral que acabamos de persistir.
        cronogramaSincronizacionService.sincronizar(presupuestoId);
    }

    /**
     * Valida que el {@code depth} del {@link CapituloConTotal} del motor
     * coincide con la profundidad real del capítulo en BD. Como el item
     * puede repetirse entre padre e hijo, este discriminante es
     * indispensable para evitar tomar el total del padre cuando estamos
     * procesando el hijo (o viceversa).
     */
    private boolean depthCoherente(CapituloConTotal c, Capitulo cap) {
        return c.depth() == depthDe(cap);
    }

    private int depthDe(Capitulo cap) {
        int depth = 1;
        Long parent = cap.parentId;
        while (parent != null) {
            depth++;
            Capitulo parentCap = capituloRepository.findById(parent);
            if (parentCap == null) break;
            parent = parentCap.parentId;
        }
        return depth;
    }

    private Long presupuestoIdDeCapitulo(Long capituloId) {
        Capitulo cap = capituloRepository.findById(capituloId);
        return cap == null ? null : cap.presupuestoId;
    }
}
