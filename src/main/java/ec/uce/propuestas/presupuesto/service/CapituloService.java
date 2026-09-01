package ec.uce.propuestas.presupuesto.service;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.presupuesto.dto.CapituloCrearRequest;
import ec.uce.propuestas.presupuesto.dto.CapituloEditarRequest;
import ec.uce.propuestas.presupuesto.dto.CapituloMoverRequest;
import ec.uce.propuestas.presupuesto.dto.PresupuestoResponse;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Plan 022 — capa de servicio del agregado {@code Presupuesto → Capitulo →
 * Rubro} para las mutaciones del árbol de capítulos (P-28 escritura).
 *
 * <p>Responsabilidades cubiertas por este plan:
 * <ul>
 *   <li>{@link #crear(UUID, CapituloCrearRequest, Long)} — POST crear capítulo
 *       raíz o subcapítulo con {@code item} autogenerado, orden opcional
 *       (omitido = append).</li>
 *   <li>{@link #editarDescripcion(UUID, UUID, CapituloEditarRequest, Long)} —
 *       PUT editar descripción (item / orden / parent no cambian).</li>
 *   <li>{@link #mover(UUID, UUID, CapituloMoverRequest, Long)} — PATCH mover
 *       (mismo padre / cruzar padre). Rechaza self/descendant cycle antes de
 *       tocar el árbol; renumera TODOS los hermanos afectados y recalcula los
 *       {@code item} del subárbol movido.</li>
 *   <li>{@link #eliminar(UUID, UUID, Long)} — DELETE subárbol (cascade
 *       {@code capitulo.parent_id} + {@code rubro.capitulo_id}; los APUs
 *       sobreviven).</li>
 * </ul>
 *
 * <h2>Renumeración atómica (invariante del plan)</h2>
 *
 * <p>Toda mutación estructural termina en {@link #renumerarArbol(Long)}, que:
 * <ol>
 *   <li>normaliza el {@code orden} de cada nivel a {@code 1..n} contiguo (sin
 *       huecos ni empates), y</li>
 *   <li>recomputa recursivamente el {@code item} de TODO el árbol
 *       ({@code padre.item + "." + orden}; raíz = {@code orden}).</li>
 * </ol>
 *
 * <p>La escritura de los {@code item} se hace en <strong>dos pasadas</strong>
 * porque {@code UNIQUE (presupuesto_id, item)} se valida por sentencia: la
 * pasada 1 «aparca» cada fila cuyo item cambia en un valor temporal
 * {@code "~<id>"} (espacio de nombres disjunto de los items canónicos, que
 * nunca empiezan por {@code ~}, y que cabe en {@code VARCHAR(20)}) y hace
 * {@code flush()}; la pasada 2 asigna los items definitivos, que ya no pueden
 * colisionar con nadie. Sin ese aparcado, permutar hermanos
 * ({@code "3" → "1"}, {@code "1" → "2"}…) o subir una rama de nivel
 * ({@code "2" → "1"} mientras otra fila aún tiene {@code "1"}) violaría la
 * constraint a mitad del flush.</p>
 *
 * <p>Todas las mutaciones operan sobre <strong>entidades gestionadas</strong> y
 * nunca con UPDATE masivos: un {@code UPDATE} JPQL no invalida la caché de
 * primer nivel, de modo que el propio {@code RecalculoService} —que en la misma
 * transacción lee capítulos y reescribe {@code total}— acabaría persistiendo
 * {@code item}/{@code orden} stale sobre lo ya escrito.</p>
 *
 * <p>Cada mutación termina con {@link RecalculoService#recalcular(Alcance)}
 * sobre {@link Alcance.Version} del presupuesto del path (write-through de
 * totales raíz → capítulos → rubros), siempre después de un {@code flush()}
 * para que el snapshot del motor vea los nuevos {@code item}/{@code orden}/
 * {@code parentId}.</p>
 *
 * <p>Convenciones de respuesta: cada método devuelve un
 * {@link PresupuestoResponse} completo y fresco (árbol recalculado); el resource
 * decide el status (201 en crear, 200 en el resto). El shape es el estable de
 * Plan 021 y no se amplía.</p>
 *
 * <p>Decisión de seam: este servicio <strong>no</strong> introduce variantes
 * nuevas de {@link Alcance} (D-08 + DM §17 #17). Los APUs sobreviven al borrado
 * de un capítulo (D-09 + V001 §3): la cascade borra los rubros del subárbol y
 * deja intacto el catálogo de APUs.</p>
 */
@ApplicationScoped
public class CapituloService {

    /**
     * Prefijo del {@code item} temporal usado por la pasada 1 de la
     * renumeración. Ningún item canónico empieza por este carácter, así que el
     * espacio de nombres temporal es disjunto del definitivo y
     * {@code "~" + id} siempre cabe en {@code capitulo.item VARCHAR(20)}.
     */
    private static final String PREFIJO_ITEM_APARCADO = "~";

    @Inject
    CapituloRepository capituloRepository;

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    RubroRepository rubroRepository;

    @Inject
    RecalculoService recalculoService;

    /**
     * Plan 022 — crea un capítulo (raíz o sub) y devuelve el árbol recalculado
     * del presupuesto. El {@code item} se calcula en backend; {@code orden} es
     * opcional (omitido = append al final del nivel).
     *
     * <p>Reglas: descripción {@code @NotBlank @Size(max=255)}; {@code orden}
     * (si viene) ∈ {@code [1, hermanos+1]}; {@code parentId} (si viene) debe
     * pertenecer al presupuesto del path (otro presupuesto del owner → 400;
     * inexistente o ajeno → 404). Insertar en una posición intermedia empuja a
     * los hermanos siguientes y renumera el nivel completo.</p>
     */
    @Transactional
    public PresupuestoResponse crear(UUID presupuestoPublicId, CapituloCrearRequest req, Long callerUsuarioId) {
        Presupuesto presupuesto = resolverPresupuestoPorOwner(presupuestoPublicId, callerUsuarioId);
        Long presupuestoId = presupuesto.id;
        Capitulo padre = resolverPadre(req.parentId(), presupuestoId, callerUsuarioId);
        Long padreId = padre == null ? null : padre.id;

        List<Capitulo> hermanos = capituloRepository.listarHermanosEnPresupuesto(presupuestoId, padreId);
        short posicion = req.orden() == null ? (short) (hermanos.size() + 1) : req.orden();
        validarPosicion(posicion, hermanos.size() + 1);

        Capitulo nuevo = new Capitulo();
        nuevo.presupuestoId = presupuestoId;
        nuevo.parentId = padreId;
        nuevo.descripcion = req.descripcion();
        nuevo.orden = posicion;
        // Item temporal: el definitivo lo escribe la renumeración. Insertar ya con
        // el item final chocaría con el hermano que todavía lo ocupa (los INSERT
        // se ejecutan antes que los UPDATE dentro del mismo flush).
        nuevo.item = itemAparcadoProvisional();
        nuevo.total = BigDecimal.ZERO;
        capituloRepository.persist(nuevo);
        // Materializa el INSERT: el id BIGINT y el public_id (default uuidv7())
        // deben existir antes de reordenar y de leer el árbol de vuelta.
        capituloRepository.getEntityManager().flush();

        colocarEntreHermanos(hermanos, nuevo, posicion);
        renumerarArbol(presupuestoId);

        recalculoService.recalcular(new Alcance.Version(presupuestoId));
        return cargarArbolRecalculado(presupuestoId);
    }

    /**
     * Plan 022 — edita la descripción de un capítulo. Item, parent y orden no
     * cambian (no hay renumeración). Se recalcula igualmente para mantener la
     * misma postcondición que el resto de mutaciones: la respuesta es el árbol
     * con totales write-through frescos.
     */
    @Transactional
    public PresupuestoResponse editarDescripcion(
            UUID presupuestoPublicId, UUID capituloPublicId, CapituloEditarRequest req, Long callerUsuarioId) {
        Presupuesto presupuesto = resolverPresupuestoPorOwner(presupuestoPublicId, callerUsuarioId);
        Long presupuestoId = presupuesto.id;
        Capitulo cap = resolverCapituloEnPresupuesto(capituloPublicId, presupuestoId, callerUsuarioId);

        cap.descripcion = req.descripcion();
        capituloRepository.getEntityManager().flush();

        recalculoService.recalcular(new Alcance.Version(presupuestoId));
        return cargarArbolRecalculado(presupuestoId);
    }

    /**
     * Plan 022 — mueve un capítulo (con su subárbol) a un nuevo padre y/o
     * posición. Reglas, en este orden:
     * <ol>
     *   <li>{@code parentId} (si viene) pertenece al presupuesto del path.</li>
     *   <li>El nuevo padre no es el propio capítulo ni un descendiente suyo
     *       (400 antes de escribir nada).</li>
     *   <li>{@code orden} ∈ {@code [1, hermanos destino + 1]}, contando los
     *       hermanos SIN el nodo movido (que libera su slot al salir).</li>
     *   <li>El nodo se inserta en la secuencia destino y se renumera el árbol:
     *       el nivel de origen se compacta, el de destino se abre y los
     *       {@code item} de la rama movida se recomponen con el prefijo
     *       nuevo.</li>
     * </ol>
     */
    @Transactional
    public PresupuestoResponse mover(
            UUID presupuestoPublicId, UUID capituloPublicId, CapituloMoverRequest req, Long callerUsuarioId) {
        Presupuesto presupuesto = resolverPresupuestoPorOwner(presupuestoPublicId, callerUsuarioId);
        Long presupuestoId = presupuesto.id;
        Capitulo cap = resolverCapituloEnPresupuesto(capituloPublicId, presupuestoId, callerUsuarioId);
        Capitulo nuevoPadre = resolverPadre(req.parentId(), presupuestoId, callerUsuarioId);
        Long nuevoPadreId = nuevoPadre == null ? null : nuevoPadre.id;

        // Ciclos primero (self o descendiente): la transacción no debe llegar a
        // escribir nada si el destino es inválido. parentId null (mover a raíz)
        // nunca crea ciclo.
        if (capituloRepository.esDescendienteOigual(cap.id, nuevoPadreId)) {
            throw ProblemaException.validacion("El parentId indicado crearía un ciclo en el árbol de capítulos");
        }

        List<Capitulo> hermanosDestino =
                new ArrayList<>(capituloRepository.listarHermanosEnPresupuesto(presupuestoId, nuevoPadreId));
        hermanosDestino.removeIf(h -> cap.id.equals(h.id));
        validarPosicion(req.orden(), hermanosDestino.size() + 1);

        cap.parentId = nuevoPadreId;
        colocarEntreHermanos(hermanosDestino, cap, req.orden());
        renumerarArbol(presupuestoId);

        recalculoService.recalcular(new Alcance.Version(presupuestoId));
        return cargarArbolRecalculado(presupuestoId);
    }

    /**
     * Plan 022 — elimina un capítulo y todo su subárbol. Las FK
     * {@code capitulo.parent_id} y {@code rubro.capitulo_id} son
     * {@code ON DELETE CASCADE}, así que Postgres —no Hibernate— borra
     * descendientes y rubros. Los APUs sobreviven (D-09 + V001 §3: la FK
     * {@code rubro.apu_id → apu.id} es {@code ON DELETE RESTRICT}, y al
     * desaparecer los rubros el catálogo de APUs queda intacto y reutilizable).
     *
     * <p>Secuencia: {@code delete} → {@code flush()} (materializa la cascade) →
     * {@code clear()} (la sesión no puede seguir cacheando filas que la cascade
     * borró) → renumeración del árbol superviviente (los hermanos se compactan a
     * {@code 1..n}) → recálculo.</p>
     */
    @Transactional
    public PresupuestoResponse eliminar(UUID presupuestoPublicId, UUID capituloPublicId, Long callerUsuarioId) {
        Presupuesto presupuesto = resolverPresupuestoPorOwner(presupuestoPublicId, callerUsuarioId);
        Long presupuestoId = presupuesto.id;
        Capitulo cap = resolverCapituloEnPresupuesto(capituloPublicId, presupuestoId, callerUsuarioId);

        capituloRepository.delete(cap);
        capituloRepository.getEntityManager().flush();
        capituloRepository.getEntityManager().clear();

        renumerarArbol(presupuestoId);

        recalculoService.recalcular(new Alcance.Version(presupuestoId));
        return cargarArbolRecalculado(presupuestoId);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Renumeración atómica
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Normaliza el {@code orden} de cada nivel a {@code 1..n} contiguo y
     * reescribe los {@code item} de todo el árbol del presupuesto en dos pasadas
     * (ver el javadoc de la clase). Idempotente: si nada cambió, no emite
     * ningún UPDATE de item.
     */
    private void renumerarArbol(Long presupuestoId) {
        List<Capitulo> todos = capituloRepository.listarPorPresupuesto(presupuestoId);
        if (todos.isEmpty()) {
            return;
        }

        Map<Long, List<Capitulo>> hijosPorPadre = new HashMap<>();
        List<Capitulo> raices = new ArrayList<>();
        for (Capitulo c : todos) {
            if (c.parentId == null) {
                raices.add(c);
            } else {
                hijosPorPadre
                        .computeIfAbsent(c.parentId, k -> new ArrayList<>())
                        .add(c);
            }
        }
        // El orden en memoria ya refleja la posición pedida por la mutación (que
        // asignó orden contiguo al nivel tocado); el id desempata de forma
        // estable si dos hermanos comparten orden por un estado heredado.
        Comparator<Capitulo> porPosicion = Comparator.comparingInt(
                        (Capitulo c) -> c.orden == null ? Short.MAX_VALUE : c.orden)
                .thenComparingLong(c -> c.id);
        raices.sort(porPosicion);
        hijosPorPadre.values().forEach(hijos -> hijos.sort(porPosicion));

        Map<Long, String> itemsObjetivo = new LinkedHashMap<>();
        asignarNivel(raices, null, hijosPorPadre, itemsObjetivo, new HashSet<>());

        // Pasada 1 — aparcar los items que cambian para liberar sus valores.
        boolean aparcados = false;
        for (Capitulo c : todos) {
            String objetivo = itemsObjetivo.get(c.id);
            if (objetivo == null || objetivo.equals(c.item) || c.item.startsWith(PREFIJO_ITEM_APARCADO)) {
                continue;
            }
            c.item = PREFIJO_ITEM_APARCADO + c.id;
            aparcados = true;
        }
        if (aparcados) {
            capituloRepository.getEntityManager().flush();
        }

        // Pasada 2 — items definitivos (ningún valor destino está ya ocupado).
        for (Capitulo c : todos) {
            String objetivo = itemsObjetivo.get(c.id);
            if (objetivo != null && !objetivo.equals(c.item)) {
                c.item = objetivo;
            }
        }
        capituloRepository.getEntityManager().flush();
    }

    /**
     * Asigna {@code orden} contiguo a un nivel y acumula el {@code item}
     * objetivo de cada nodo, recursivamente. {@code itemPadre == null} = nivel
     * raíz. El conjunto {@code visitados} es una defensa barata: si la BD
     * tuviera un ciclo accidental, el recorrido termina en vez de colgarse.
     */
    private void asignarNivel(
            List<Capitulo> hermanos,
            String itemPadre,
            Map<Long, List<Capitulo>> hijosPorPadre,
            Map<Long, String> itemsObjetivo,
            Set<Long> visitados) {
        short posicion = 0;
        for (Capitulo c : hermanos) {
            if (!visitados.add(c.id)) {
                continue;
            }
            posicion++;
            c.orden = posicion;
            String item = itemPadre == null ? String.valueOf(posicion) : itemPadre + "." + posicion;
            itemsObjetivo.put(c.id, item);
            asignarNivel(hijosPorPadre.getOrDefault(c.id, List.of()), item, hijosPorPadre, itemsObjetivo, visitados);
        }
    }

    /**
     * Inserta {@code nodo} en la posición 1-based indicada dentro de la
     * secuencia de hermanos y reasigna {@code orden = 1..n} a todo el nivel. El
     * nodo se retira antes de insertarse (reordenar dentro del mismo padre) y la
     * posición se acota al tamaño del nivel para no dejar huecos.
     */
    private static void colocarEntreHermanos(List<Capitulo> hermanos, Capitulo nodo, short posicion) {
        List<Capitulo> nivel = new ArrayList<>(hermanos);
        nivel.removeIf(h -> nodo.id.equals(h.id));
        int indice = Math.max(0, Math.min(posicion - 1, nivel.size()));
        nivel.add(indice, nodo);
        for (int i = 0; i < nivel.size(); i++) {
            nivel.get(i).orden = (short) (i + 1);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers privados
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Resuelve el presupuesto por UUIDv7 + owner-scope. Una UUID ajena o
     * inexistente devuelve 404 (RNF-05: nunca 403).
     */
    private Presupuesto resolverPresupuestoPorOwner(UUID presupuestoPublicId, Long callerUsuarioId) {
        return presupuestoRepository
                .findByPublicIdAndOwnerScope(presupuestoPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));
    }

    /**
     * Resuelve un capítulo por UUIDv7 restringido al presupuesto del path. Si
     * pertenece a otro presupuesto del mismo owner (p. ej. otra versión del
     * proyecto) → 400 {@code validacion}; si no existe o es de otro usuario →
     * 404 {@code no-encontrado}.
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
     * Resuelve el padre opcional. {@code null} (raíz) devuelve {@code null}; en
     * otro caso delega en {@link #resolverCapituloEnPresupuesto(UUID, Long, Long)},
     * que diferencia «400 pertenece a otro presupuesto» de «404 no existe».
     */
    private Capitulo resolverPadre(UUID parentPublicId, Long presupuestoId, Long callerUsuarioId) {
        if (parentPublicId == null) {
            return null;
        }
        return resolverCapituloEnPresupuesto(parentPublicId, presupuestoId, callerUsuarioId);
    }

    /**
     * Valida la posición destino: {@code 1 ≤ orden ≤ maximo}, donde
     * {@code maximo = hermanos + 1} (append al final). El bean validation ya
     * cubre {@code @NotNull @Min(1)} en mover; esta comprobación es la única en
     * crear (donde {@code orden} es opcional) y la que conoce el tamaño real del
     * nivel destino.
     */
    private static void validarPosicion(Short orden, int maximo) {
        if (orden == null || orden < 1 || orden > maximo) {
            throw ProblemaException.validacion("orden fuera del rango permitido [1, " + maximo + "]");
        }
    }

    /**
     * Item temporal para un capítulo recién insertado: pertenece al espacio
     * aparcado ({@code ~}) y es único sin necesidad de conocer el id todavía no
     * asignado. Longitud 19 ≤ {@code VARCHAR(20)}.
     */
    private static String itemAparcadoProvisional() {
        return PREFIJO_ITEM_APARCADO
                + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
    }

    /**
     * Recarga el árbol completo del presupuesto y lo mapea al
     * {@link PresupuestoResponse} con inyección de UUIDs públicos de APUs.
     * Misma estrategia que {@code PresupuestoService.obtenerArbol} para
     * preservar la forma estable del read model (P-30). La cabecera se relee por
     * id: tras el recálculo (y tras el {@code clear()} del borrado) el
     * {@code total} vigente vive en la instancia gestionada, no en la que
     * resolvió el owner-scope.
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

    /**
     * Devuelve un mapa {@code BIGINT-apu → publicId} para emitir el campo
     * {@code apuId} como UUIDv7 en el read model (P-29 contrato). Misma
     * implementación que {@code PresupuestoService.resolverApuPublicos} — vive
     * aquí para no abrir un seam en otra capa.
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
