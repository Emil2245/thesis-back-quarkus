package ec.uce.propuestas.presupuesto.service;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.presupuesto.dto.PresupuestoResponse;
import ec.uce.propuestas.presupuesto.dto.PresupuestoVersionResponse;
import ec.uce.propuestas.presupuesto.entity.Capitulo;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.mapper.PresupuestoMapper;
import ec.uce.propuestas.presupuesto.repository.CapituloRepository;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.repository.ProyectoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plan 021 — capa de servicio del agregado {@code Presupuesto → Capitulo →
 * Rubro} (read model + auto-create v1 vigente).
 *
 * <p>Responsabilidades cubiertas por este plan:
 * <ul>
 *   <li>{@link #crearVigenteInicial(Long)} — auto-create de la fila
 *       {@code Presupuesto(version=1, es_vigente=true)} en la misma
 *       transacción que {@code ProyectoService.crear}. La invariante
 *       «exactamente una vigente por proyecto» se valida por el índice
 *       único parcial {@code ux_presupuesto_vigente} (V001 §2.8); un
 *       intento de doble-create se traduce en
 *       {@link ProblemaException#conflicto}.</li>
 *   <li>{@link #listarVersiones(UUID, Long)} — versiones del proyecto
 *       ordenadas por {@code version DESC}.</li>
 *   <li>{@link #obtenerArbol(UUID, Long)} — read model recursivo
 *       (capítulos → subcapítulos → rubros).</li>
 * </ul>
 *
 * <p>Decisión CDI: este servicio <strong>no</strong> inyecta
 * {@code ProyectoService}. Existía un ciclo
 * {@code ProyectoService → PresupuestoService → ProyectoService}
 * (el lado presupuesto sólo usaba {@code ProyectoService} para una
 * validación owner-scope); se elimina inyectando directamente
 * {@link ProyectoRepository} y reutilizando
 * {@link ProyectoRepository#findByPublicIdAndOwnerScope(UUID, Long)}.
 * {@code ProyectoService} sigue inyectando este bean para el auto-create
 * del Presupuesto v1 vigente — ese sentido del grafo queda intacto.</p>
 *
 * <p>No incluye mutaciones del árbol (P-28/P-29 escritura): viven en los
 * planes 022 y 023. No invoca {@code recalculo} sobre el presupuesto
 * recién creado — un presupuesto fresco no tiene árbol, por lo que
 * no hay nada que propagar (Plan 020 §STOP).</p>
 */
@ApplicationScoped
public class PresupuestoService {

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    CapituloRepository capituloRepository;

    @Inject
    RubroRepository rubroRepository;

    @Inject
    ProyectoRepository proyectoRepository;

    /**
     * Plan 021 — auto-create del Presupuesto v1 vigente al crear un proyecto.
     * Se ejecuta dentro de la transacción abierta por
     * {@code ProyectoService.crear}; un fallo aquí aborta el commit completo
     * (rollback de la fila {@code proyecto} también).
     *
     * <p>Decisiones locked: {@code version = 1}, {@code es_vigente = true},
     * {@code origen_id = null}, {@code notas = null}, {@code total = 0}.
     * {@code porcentaje_indirecto} queda {@code null} — la lectura del
     * árbol lo toma del {@code ParametrosProyecto} (DM §17 #17, COALESCE
     * con el default del proyecto). El índice único parcial
     * {@code ux_presupuesto_vigente} (V001) protege la invariante; un
     * doble llamado se traduce a {@link ProblemaException#conflicto} con
     * código {@code vigente-duplicado}.</p>
     */
    @Transactional
    public Presupuesto crearVigenteInicial(Long proyectoId) {
        if (presupuestoRepository.findVigenteDeProyecto(proyectoId).isPresent()) {
            throw ProblemaException.conflicto("vigente-duplicado", "El proyecto ya tiene un presupuesto vigente");
        }
        Presupuesto presupuesto = new Presupuesto();
        presupuesto.proyectoId = proyectoId;
        presupuesto.version = (short) 1;
        presupuesto.esVigente = true;
        presupuesto.origenId = null;
        presupuesto.notas = null;
        presupuesto.porcentajeIndirecto = null;
        presupuesto.total = BigDecimal.ZERO;
        presupuestoRepository.persist(presupuesto);
        // Forzar el INSERT + recálculo de defaults (uuidv7() + created_at/updated_at)
        // para que publicId quede materializado antes de cualquier lectura externa.
        presupuestoRepository.getEntityManager().flush();
        return presupuesto;
    }

    /**
     * Lista las versiones del proyecto identificadas por su {@code publicId}
     * UUIDv7. Devuelve 404 si el proyecto no pertenece al caller.
     */
    public List<PresupuestoVersionResponse> listarVersiones(UUID proyectoPublicId, Long callerUsuarioId) {
        Proyecto proyecto = proyectoRepository
                .findByPublicIdAndOwnerScope(proyectoPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Proyecto no encontrado"));
        List<Presupuesto> versiones = presupuestoRepository.listarVersiones(proyecto.id);
        Map<Long, UUID> origenesPublicos = resolverOrigenesPublicos(versiones);
        return versiones.stream()
                .map(p -> PresupuestoMapper.toVersionResponse(p, origenesPublicos.get(p.origenId)))
                .toList();
    }

    /**
     * Devuelve el árbol recursivo del presupuesto identificado por su
     * {@code publicId} UUIDv7 (cabecera + capítulos + rubros + subcapítulos).
     * Devuelve 404 si el presupuesto no pertenece al caller.
     */
    public PresupuestoResponse obtenerArbol(UUID presupuestoPublicId, Long callerUsuarioId) {
        Presupuesto presupuesto = presupuestoRepository
                .findByPublicIdAndOwnerScope(presupuestoPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));

        List<Capitulo> capitulos = capituloRepository.listarPorPresupuesto(presupuesto.id);
        List<Long> capituloIds = capitulos.stream().map(c -> c.id).toList();
        List<Rubro> rubros = rubroRepository.listarPorCapitulos(capituloIds);

        Map<Long, UUID> apuPublicos = resolverApuPublicos(rubros);

        return PresupuestoMapper.toPresupuestoResponseConInyecciones(presupuesto, capitulos, rubros, apuPublicos);
    }

    /**
     * Devuelve un mapa {@code BIGINT-origen → publicId} para resolver
     * el campo opcional {@code origenId} de cada versión. Devuelve mapa
     * vacío si no hay orígenes (versión 1 sin padre).
     */
    private Map<Long, UUID> resolverOrigenesPublicos(List<Presupuesto> versiones) {
        Map<Long, UUID> result = new HashMap<>();
        for (Presupuesto p : versiones) {
            if (p.origenId != null && !result.containsKey(p.origenId)) {
                presupuestoRepository
                        .findByIdOptional(p.origenId)
                        .ifPresent(origen -> result.put(p.origenId, origen.publicId));
            }
        }
        return result;
    }

    /**
     * Devuelve un mapa {@code BIGINT-apu → publicId} para que el read model
     * emita {@code apuId} como UUIDv7 (P-29 contrato). La consulta vive
     * aquí (en lugar de en el repositorio {@code rubro}) para no abrir un
     * seam en otra capa — la inyección de UUID público es un detalle del
     * read model, no de la entidad.
     */
    private Map<Long, UUID> resolverApuPublicos(List<Rubro> rubros) {
        Map<Long, UUID> result = new HashMap<>();
        if (rubros.isEmpty()) {
            return result;
        }
        List<Long> apuIds = rubros.stream().map(r -> r.apuId).distinct().toList();
        List<Object[]> rows = presupuestoRepository
                .getEntityManager()
                .createQuery("select a.id, a.publicId from Apu a where a.id in :ids", Object[].class)
                .setParameter("ids", apuIds)
                .getResultList();
        for (Object[] row : rows) {
            result.put((Long) row[0], (UUID) row[1]);
        }
        return result;
    }
}
