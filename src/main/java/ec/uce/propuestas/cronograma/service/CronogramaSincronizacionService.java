package ec.uce.propuestas.cronograma.service;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.cronograma.entity.Actividad;
import ec.uce.propuestas.cronograma.entity.Cronograma;
import ec.uce.propuestas.cronograma.mapper.CronogramaMapper.ActividadConRubro;
import ec.uce.propuestas.cronograma.repository.ActividadRepository;
import ec.uce.propuestas.cronograma.repository.CronogramaRepository;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan 029 (P-34) — sincronización 1:1 entre {@code Rubro} y {@code Actividad}
 * tras la persistencia de totales raíz (rubros/capítulos/presupuesto).
 *
 * <p>Esta clase se invoca desde el final de
 * {@link ec.uce.propuestas.recalculo.RecalculoService#consolidarVersionYPropagar}
 * (Plan 020) <strong>después</strong> de que los totales ya estén persistidos,
 * dentro de la misma transacción: la ausencia de cronograma es un no-op; los
 * rubros desaparecen y borran su actividad por la FK CASCADE; los rubros
 * nuevos crean la actividad con {@code {}}; los precios ya recalculados se
 * traducen a pesoPonderado fresco con {@link PesoPonderadoCalculador}.</p>
 *
 * <p>Reglas operativas:
 * <ul>
 *   <li>No-op si el presupuesto no tiene cronograma (P-32 + I-08).</li>
 *   <li>Eliminación de actividades huérfanas: V001 §2.13 fija
 *       {@code actividad.rubro_id} con {@code ON DELETE CASCADE}, por lo que
 *       borrar un rubro ya borra su actividad. Esta clase NO invoca
 *       {@code actividadRepository.delete} explícitamente; <em>confía en la
 *       CASCADE de la FK</em> para evitar filas huérfanas.</li>
 *   <li>Creación 1:1: por cada {@code rubro_id} vigente sin actividad, crea
 *       una con {@code avancePorPeriodo = "{}"} y peso recalculado.</li>
 *   <li>Recalcular pesos sin tocar el mapa: el mapa no se borra silenciosamente;
 *       sólo se actualiza {@code peso_ponderado} cuando el total del presupuesto
 *       y la jerarquía de precios cambian (RESET del fingerprint, derivación
 *       natural).</li>
 *   <li>Lock concurrente: adquiere {@code lockPresupuestoRow} antes de iterar;
 *       ordena por {@code item + codigo + precioTotal DESC} (orden canónico
 *       presupuestario).</li>
 *   <li>Aislamiento de dependencia: <strong>no inyecta</strong>
 *       {@code RecalculoService} para evitar circularidad CDI. El seam es
 *       estrictamente Recalculo → sincronización, nunca al revés.</li>
 * </ul>
 *
 * <p>Tests públicos deben consumir esta clase únicamente a través de su
 * efecto observable (filas en {@code actividad}, estado de la respuesta del
 * cronograma vía {@code CronogramaService}). No existe API para invocarla
 * directamente desde el resource layer.</p>
 */
@ApplicationScoped
public class CronogramaSincronizacionService {

    /** Orden presupuestario canónico: item, desempate por id interno (BIGINT). */
    private static final Comparator<Rubro> ORDEN_PRESUPUESTARIO =
            Comparator.comparing((Rubro r) -> r.item == null ? "" : r.item).thenComparing(r -> r.id);

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    RubroRepository rubroRepository;

    @Inject
    CronogramaRepository cronogramaRepository;

    @Inject
    ActividadRepository actividadRepository;

    /**
     * Sincroniza las actividades del cronograma del presupuesto indicado.
     * La sección crítica adquiere {@code lockPresupuestoRow} para serializar
     * contra {@code PATCH/PUT} que usan el mismo lock.
     *
     * @param presupuestoId id interno BIGINT (uso interno de recalculo).
     */
    @Transactional
    public void sincronizar(Long presupuestoId) {
        if (presupuestoId == null) {
            return;
        }
        // El lock debe preceder incluso la comprobación de existencia: así una
        // creación concurrente de cronograma y una mutación de rubros observan
        // el mismo corte serializado y mantienen la cobertura 1:1.
        presupuestoRepository.lockPresupuestoRow(presupuestoId);
        ec.uce.propuestas.presupuesto.entity.Presupuesto presupuesto = presupuestoRepository.findById(presupuestoId);
        if (presupuesto == null) {
            throw ProblemaException.noEncontrado("Presupuesto no encontrado");
        }
        presupuestoRepository.getEntityManager().refresh(presupuesto);

        Cronograma cronograma = cronogramaRepository
                .find("presupuestoId = :pid", Parameters.with("pid", presupuestoId))
                .firstResult();
        if (cronograma == null) {
            return;
        }
        presupuestoRepository.getEntityManager().refresh(cronograma);
        BigDecimal totalGeneral = escala6(presupuesto.total);

        // Rubros vigentes del presupuesto, en orden presupuestario canónico.
        List<Rubro> rubros = new ArrayList<>(rubroRepository.listarPorPresupuesto(presupuestoId));
        rubros.sort(ORDEN_PRESUPUESTARIO);

        // Cálculo de pesos en una sola pasada (precioTotal, no cantidad).
        List<BigDecimal> pesos = PesoPonderadoCalculador.calcular(
                rubros.stream().map(r -> r.precioTotal).toList(), totalGeneral);

        // Mapa rubroId → actividad existente.
        List<Actividad> actuales = actividadRepository.listarPorCronograma(cronograma.id);
        Map<Long, Actividad> porRubro = new LinkedHashMap<>();
        for (Actividad a : actuales) {
            porRubro.put(a.rubroId, a);
        }

        // Crear faltantes y actualizar pesos existentes. La FK CASCADE ya borró
        // las actividades huérfanas; cualquier actividad restante pertenece
        // a un rubro vigente.
        for (int i = 0; i < rubros.size(); i++) {
            Rubro r = rubros.get(i);
            BigDecimal nuevoPeso = pesos.get(i);
            Actividad existente = porRubro.get(r.id);
            if (existente == null) {
                Actividad creada = new Actividad();
                creada.cronogramaId = cronograma.id;
                creada.rubroId = r.id;
                creada.pesoPonderado = nuevoPeso;
                creada.avancePorPeriodo = "{}";
                actividadRepository.persist(creada);
            } else if (existente.pesoPonderado == null || existente.pesoPonderado.compareTo(nuevoPeso) != 0) {
                existente.pesoPonderado = nuevoPeso;
                actividadRepository.persist(existente);
            }
        }

        actividadRepository.flush();
    }

    private static BigDecimal escala6(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Reconstruye la lista canónica {@link ActividadConRubro} desde repos.
     * Útil para que {@link ec.uce.propuestas.cronograma.service.CronogramaService}
     * devuelva la respuesta tras sincronizar. No se exporta fuera del paquete.
     */
    public List<ActividadConRubro> cargarActividades(Long cronogramaId) {
        return actividadRepository.listarConRubroPorCronograma(cronogramaId).stream()
                .map(par -> new ActividadConRubro((Actividad) par[0], (Rubro) par[1], (String) par[2]))
                .toList();
    }
}
