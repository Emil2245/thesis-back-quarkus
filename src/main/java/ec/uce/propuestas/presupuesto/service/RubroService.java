package ec.uce.propuestas.presupuesto.service;

import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.presupuesto.dto.PresupuestoResponse;
import ec.uce.propuestas.presupuesto.dto.RubroCrearRequest;
import ec.uce.propuestas.presupuesto.dto.RubroPatchRequest;
import ec.uce.propuestas.presupuesto.entity.Capitulo;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.mapper.PresupuestoMapper;
import ec.uce.propuestas.presupuesto.repository.CapituloRepository;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import ec.uce.propuestas.recalculo.Alcance;
import ec.uce.propuestas.recalculo.RecalculoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Plan 023 — capa de servicio del agregado Rubro (P-29 escritura).
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li>{@link #crear(UUID, UUID, RubroCrearRequest, Long)} — POST crear un
 *       rubro bajo un capítulo. Valida que el APU exista y pertenezca al
 *       mismo presupuesto del path (D-09: 1:1 APU↔rubro por versión;
 *       cross-version → 400), que el capítulo pertenezca al presupuesto del
 *       path (cross-presupuesto → 400), que el APU no esté ya vinculado a
 *       otro rubro del presupuesto (D-09 early 409 apu-referenciado),
 *       que {@code cantidad > 0} (v1.1 §2.6). Inserta con
 *       {@code item = capitulo.item + "." + ordinal} (append-only en este
 *       plan; sin input de posición intermedia), espeja
 *       {@code codigo/descripcion/unidad} del APU, normaliza los rubros
 *       hermanos del capítulo antes de devolver (riesgo Plan 022: item
 *       stale cuando el capítulo se renumeró tras mover/eliminar).</li>
 *   <li>{@link #editarCantidad(UUID, UUID, UUID, RubroPatchRequest, Long)} —
 *       PATCH editar únicamente la cantidad. Item, codigo, descripcion,
 *       unidad y apuId permanecen; el write-through propaga el nuevo
 *       {@code precio_total} (cantidad × PU_2dp) al árbol.</li>
 *   <li>{@link #eliminar(UUID, UUID, UUID, Long)} — DELETE eliminar un rubro.
 *       El APU sobrevive (D-09 + V001 §3: la FK {@code rubro.apu_id →
 *       apu.id} es {@code ON DELETE RESTRICT}, y al eliminarse el rubro
 *       el catálogo de APUs queda intacto y reutilizable). Tras el
 *       DELETE se compactan los rubros hermanos del capítulo a
 *       {@code capitulo.item + "." + 1..n} (Plan 022 style: ver
 *       {@link #compactarRubrosDelCapitulo}).</li>
 * </ul>
 *
 * <p>Convención de respuesta (idéntica a Plan 022): cada mutación termina
 * con {@link RecalculoService#recalcular(Alcance)} sobre
 * {@link Alcance.Version} del presupuesto del path (write-through de
 * totales raíz → capítulos → rubros), siempre tras {@code flush()}, y
 * devuelve el árbol completo {@link PresupuestoResponse}. El status HTTP
 * lo decide el resource (201 en crear, 200 en el resto).</p>
 *
 * <p>Decisión de seam: este servicio <strong>no</strong> introduce
 * variantes nuevas de {@link Alcance} (D-08 + DM §17 #17). El
 * write-through tras mutación de rubro se sirve recorriendo el árbol por
 * alcance de versión completa, igual que las mutaciones estructurales de
 * Plan 022.</p>
 *
 * <p>Reglas de error (07-api-contract.md §6 + P-29):
 * <ul>
 *   <li>400 {@code validacion}: UUIDv7 malformado, cantidad ≤ 0,
 *       APU pertenece a otro presupuesto, capítulo pertenece a otro
 *       presupuesto, rubro pertenece a otro capítulo (cross-capítulo).</li>
 *   <li>404 {@code no-encontrado}: presupuesto, capítulo, rubro o APU
 *       ajeno/inexistente (nunca 403 — RNF-05).</li>
 *   <li>409 {@code apu-referenciado}: APU ya vinculado a otro rubro del
 *       mismo presupuesto (D-09 1:1).</li>
 * </ul>
 *
 * <p>Antes de devolver/recalcular en este service se normaliza el
 * {@code item} de los rubros del capítulo destino
 * ({@link #compactarRubrosDelCapitulo}). Esto cierra el riesgo Plan 022
 * de un rubro con {@code item} stale heredado de una renumeración de
 * capítulos que movió/compacto al padre: la compactación a
 * {@code capitulo.item + "." + 1..n} siempre deja el árbol contiguo.</p>
 */
@ApplicationScoped
public class RubroService {

    @Inject
    RubroRepository rubroRepository;

    @Inject
    CapituloRepository capituloRepository;

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    ApuRepository apuRepository;

    @Inject
    RecalculoService recalculoService;

    /**
     * Plan 023 — crea un rubro bajo un capítulo y devuelve el árbol
     * recalculado del presupuesto. El {@code item} del rubro se calcula
     * como {@code capitulo.item + "." + ordinal}, donde {@code ordinal} es
     * {@code hermanos.size() + 1} (append-only en este plan; el
     * frontend no puede insertar en posición intermedia).
     *
     * <p>Reglas, en este orden:
     * <ol>
     *   <li>UUIDv7 del path y del body (parse en resource).</li>
     *   <li>Resolución owner-scope del presupuesto (404 si ajeno).</li>
     *   <li>Resolución del capítulo dentro del presupuesto (400 si
     *       pertenece a otro presupuesto del owner).</li>
     *   <li>Resolución del APU con scope de owner (404 si ajeno).</li>
     *   <li>APU pertenece al mismo presupuesto (400 si cross-version).</li>
     *   <li>APU no vinculado ya a otro rubro del presupuesto (D-09 → 409
     *       apu-referenciado).</li>
     *   <li>Cantidad > 0 (validado en DTO).</li>
     * </ol>
     */
    public Rubro crearRubroAppendOnlySinRecalculo(Presupuesto presupuesto, Capitulo capitulo, Apu apu) {
        compactarRubrosDelCapitulo(capitulo.id);
        int ordinal = rubroRepository.listarPorCapitulo(capitulo.id).size() + 1;
        Rubro rubro = new Rubro();
        rubro.capituloId = capitulo.id;
        rubro.apuId = apu.id;
        rubro.codigo = apu.codigo;
        rubro.descripcion = apu.descripcion;
        rubro.unidad = apu.unidad;
        rubro.cantidad = BigDecimal.ONE;
        rubro.item = ordinalItem(capitulo.item, ordinal);
        rubro.precioUnitario = BigDecimal.ZERO;
        rubro.precioTotal = BigDecimal.ZERO;
        rubroRepository.persist(rubro);
        return rubro;
    }

    public PresupuestoResponse cargarArbol(Long presupuestoId) {
        return cargarArbolRecalculado(presupuestoId);
    }

    @Transactional
    public PresupuestoResponse crear(
            UUID presupuestoPublicId, UUID capituloPublicId, RubroCrearRequest req, Long callerUsuarioId) {
        Presupuesto presupuesto = resolverPresupuestoPorOwner(presupuestoPublicId, callerUsuarioId);
        Long presupuestoId = presupuesto.id;
        Capitulo capitulo = resolverCapituloEnPresupuesto(capituloPublicId, presupuestoId, callerUsuarioId);
        Long capituloId = capitulo.id;

        Apu apu = resolverApuPorOwner(req.apuId(), callerUsuarioId);
        if (!apu.presupuestoId.equals(presupuestoId)) {
            throw ProblemaException.validacion("El APU indicado pertenece a otro presupuesto");
        }
        if (rubroRepository.findByApuId(apu.id).isPresent()) {
            throw ProblemaException.apuReferenciado("El APU ya está vinculado a un rubro del presupuesto");
        }

        // Normalizar rubro items del capítulo ANTES de calcular el ordinal
        // del nuevo rubro (Plan 022 risk): un item stale heredado (p. ej.
        // capítulo movido) podría dejar huecos que el append-only no
        // detectaría. La compactación deja 1..n contiguo.
        compactarRubrosDelCapitulo(capituloId);
        List<Rubro> hermanos = rubroRepository.listarPorCapitulo(capituloId);
        int ordinal = hermanos.size() + 1;

        Rubro rubro = new Rubro();
        rubro.capituloId = capituloId;
        rubro.apuId = apu.id;
        rubro.codigo = apu.codigo;
        rubro.descripcion = apu.descripcion;
        rubro.unidad = apu.unidad;
        rubro.cantidad = req.cantidad();
        rubro.item = ordinalItem(capitulo.item, ordinal);
        rubro.precioUnitario = BigDecimal.ZERO;
        rubro.precioTotal = BigDecimal.ZERO;
        rubroRepository.persist(rubro);
        rubroRepository.getEntityManager().flush();

        recalculoService.recalcular(new Alcance.Version(presupuestoId));
        return cargarArbolRecalculado(presupuestoId);
    }

    /**
     * Plan 023 — edita únicamente la {@code cantidad} de un rubro y
     * devuelve el árbol recalculado. {@code item}, {@code codigo},
     * {@code descripcion}, {@code unidad} y {@code apuId} NO se tocan
     * (vienen del APU; editar el APU propaga vía {@code recalcular}).
     */
    @Transactional
    public PresupuestoResponse editarCantidad(
            UUID presupuestoPublicId,
            UUID capituloPublicId,
            UUID rubroPublicId,
            RubroPatchRequest req,
            Long callerUsuarioId) {
        Presupuesto presupuesto = resolverPresupuestoPorOwner(presupuestoPublicId, callerUsuarioId);
        Long presupuestoId = presupuesto.id;
        Capitulo capitulo = resolverCapituloEnPresupuesto(capituloPublicId, presupuestoId, callerUsuarioId);
        Rubro rubro = resolverRubroEnCapitulo(rubroPublicId, capitulo.id, presupuestoId, callerUsuarioId);

        rubro.cantidad = req.cantidad();
        rubroRepository.persist(rubro);
        rubroRepository.getEntityManager().flush();

        recalculoService.recalcular(new Alcance.Version(presupuestoId));
        return cargarArbolRecalculado(presupuestoId);
    }

    /**
     * Plan 023 — elimina un rubro y devuelve el árbol recalculado. El APU
     * sobrevive (D-09 + V001 §3); los rubros hermanos del capítulo se
     * compactan a {@code capitulo.item + "." + 1..n}. La normalización se
     * hace antes de devolver/recalcular (Plan 022 risk).
     */
    @Transactional
    public PresupuestoResponse eliminar(
            UUID presupuestoPublicId, UUID capituloPublicId, UUID rubroPublicId, Long callerUsuarioId) {
        Presupuesto presupuesto = resolverPresupuestoPorOwner(presupuestoPublicId, callerUsuarioId);
        Long presupuestoId = presupuesto.id;
        Capitulo capitulo = resolverCapituloEnPresupuesto(capituloPublicId, presupuestoId, callerUsuarioId);
        Rubro rubro = resolverRubroEnCapitulo(rubroPublicId, capitulo.id, presupuestoId, callerUsuarioId);

        rubroRepository.delete(rubro);
        rubroRepository.getEntityManager().flush();
        rubroRepository.getEntityManager().clear();

        compactarRubrosDelCapitulo(capitulo.id);

        recalculoService.recalcular(new Alcance.Version(presupuestoId));
        return cargarArbolRecalculado(presupuestoId);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Compactación de items (Plan 022 risk)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Normaliza el {@code item} de los rubros del capítulo a
     * {@code capitulo.item + "." + 1..n} contiguo, ordenado por el item
     * actual (lexicográfico coincide con el orden natural). Idempotente.
     *
     * <p>Cierra el riesgo Plan 022: si un capítulo se renumeró (mover
     * hermano intermedio, mover subárbol, eliminar capítulo), los rubros
     * conservan el {@code item} que tenían antes — un rubro con
     * {@code item = "1.2"} puede seguir así aunque el capítulo haya
     * pasado a ser el hermano "3". Antes de cualquier cálculo que use
     * ese {@code item} (compactación tras DELETE, ordinal del nuevo
     * rubro en POST) hay que reescribir el {@code item} con el prefijo
     * vigente del capítulo.</p>
     */
    public void compactarRubrosDelCapitulo(Long capituloId) {
        List<Rubro> rubros = rubroRepository.listarPorCapitulo(capituloId);
        if (rubros.isEmpty()) {
            return;
        }
        Capitulo capitulo = capituloRepository.findById(capituloId);
        if (capitulo == null) {
            return;
        }
        // Orden estable por item lexicográfico (desempata por id) — coincide
        // con la regla de renumerarArbol en CapituloService para que ambos
        // caminos produzcan la misma secuencia de items 1..n.
        rubros.sort(Comparator.comparing((Rubro r) -> r.item).thenComparing(r -> r.id));
        boolean cambios = false;
        for (int i = 0; i < rubros.size(); i++) {
            String objetivo = ordinalItem(capitulo.item, i + 1);
            if (!objetivo.equals(rubros.get(i).item)) {
                rubros.get(i).item = objetivo;
                rubroRepository.persist(rubros.get(i));
                cambios = true;
            }
        }
        if (cambios) {
            rubroRepository.getEntityManager().flush();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers privados
    // ──────────────────────────────────────────────────────────────────────

    /** Compone el {@code item} ordinal de un rubro: {@code capitulo.item + "." + ordinal}. */
    private static String ordinalItem(String capituloItem, int ordinal) {
        return capituloItem + "." + ordinal;
    }

    /** Resolución owner-scope del presupuesto (404 si ajeno o inexistente). */
    private Presupuesto resolverPresupuestoPorOwner(UUID presupuestoPublicId, Long callerUsuarioId) {
        return presupuestoRepository
                .findByPublicIdAndOwnerScope(presupuestoPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));
    }

    /**
     * Resuelve el capítulo por UUIDv7 restringido al presupuesto del path. Si
     * pertenece a OTRO presupuesto del mismo owner → 400 {@code validacion};
     * si no existe o es ajeno → 404 {@code no-encontrado} (RNF-05).
     */
    private Capitulo resolverCapituloEnPresupuesto(UUID capituloPublicId, Long presupuestoId, Long callerUsuarioId) {
        if (capituloPublicId == null) {
            throw ProblemaException.noEncontrado("Capítulo no encontrado");
        }
        Optional<Capitulo> enPresupuesto =
                capituloRepository.findByPublicIdEnPresupuesto(capituloPublicId, presupuestoId);
        if (enPresupuesto.isPresent()) {
            return enPresupuesto.get();
        }
        if (capituloRepository.existeEnOtroPresupuestoDelOwner(capituloPublicId, presupuestoId, callerUsuarioId)) {
            throw ProblemaException.validacion("El capítulo indicado pertenece a otro presupuesto");
        }
        throw ProblemaException.noEncontrado("Capítulo no encontrado");
    }

    /**
     * Resuelve un rubro por UUIDv7 restringido al capítulo del path. Si
     * pertenece a OTRO capítulo del mismo presupuesto → 400 {@code validacion}
     * (cross-capítulo); si pertenece a OTRO presupuesto → 400 vía
     * {@link RubroRepository#existeEnOtroPresupuestoDelOwner}; si ajeno o
     * inexistente → 404.
     */
    private Rubro resolverRubroEnCapitulo(
            UUID rubroPublicId, Long capituloId, Long presupuestoId, Long callerUsuarioId) {
        if (rubroPublicId == null) {
            throw ProblemaException.noEncontrado("Rubro no encontrado");
        }
        Optional<Rubro> enCapitulo = rubroRepository.findByPublicIdEnCapitulo(rubroPublicId, capituloId);
        if (enCapitulo.isPresent()) {
            return enCapitulo.get();
        }
        // Distinguimos cross-capítulo (400) de cross-presupuesto (400 con mensaje
        // específico) — ambos casos son 400 validacion, pero el primero tiene
        // mensaje más útil.
        Rubro delOwner = rubroRepository
                .findByPublicIdAndOwnerScope(rubroPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Rubro no encontrado"));
        Capitulo capituloReal = capituloRepository.findById(delOwner.capituloId);
        if (capituloReal != null && presupuestoId.equals(capituloReal.presupuestoId)) {
            throw ProblemaException.validacion("El rubro indicado pertenece a otro capítulo del presupuesto");
        }
        throw ProblemaException.validacion("El rubro indicado pertenece a otro presupuesto");
    }

    /** Resuelve un APU por UUIDv7 con scope de owner (404 si ajeno o inexistente). */
    private Apu resolverApuPorOwner(UUID apuPublicId, Long callerUsuarioId) {
        return apuRepository
                .findByPublicIdAndOwnerScope(apuPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("APU no encontrado"));
    }

    /**
     * Recarga el árbol completo del presupuesto y lo mapea al
     * {@link PresupuestoResponse} con inyección de UUIDs públicos de APUs.
     * Misma estrategia que {@code CapituloService.cargarArbolRecalculado} y
     * {@code PresupuestoService.obtenerArbol} para preservar la forma
     * estable del read model (P-30).
     */
    private PresupuestoResponse cargarArbolRecalculado(Long presupuestoId) {
        Presupuesto presupuesto = presupuestoRepository.findById(presupuestoId);
        if (presupuesto == null) {
            throw ProblemaException.noEncontrado("Presupuesto no encontrado");
        }
        List<Capitulo> capitulos = capituloRepository.listarPorPresupuesto(presupuestoId);
        List<Long> capituloIds = capitulos.stream().map(c -> c.id).toList();
        List<Rubro> rubros = rubroRepository.listarPorCapitulos(capituloIds);
        Map<Long, UUID> apuPublicos = resolverApuPublicos(rubros);
        return PresupuestoMapper.toPresupuestoResponseConInyecciones(presupuesto, capitulos, rubros, apuPublicos);
    }

    /** Devuelve un mapa {@code BIGINT-apu → publicId} para emitir {@code apuId} como UUIDv7. */
    private Map<Long, UUID> resolverApuPublicos(List<Rubro> rubros) {
        Map<Long, UUID> result = new HashMap<>();
        if (rubros.isEmpty()) {
            return result;
        }
        List<Long> apuIds =
                new ArrayList<>(rubros.stream().map(r -> r.apuId).distinct().toList());
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
