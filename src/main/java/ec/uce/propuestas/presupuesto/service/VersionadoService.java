package ec.uce.propuestas.presupuesto.service;

import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.entity.ApuDetalle;
import ec.uce.propuestas.apu.entity.ApuSeccion;
import ec.uce.propuestas.apu.repository.ApuDetalleRepository;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.repository.ApuSeccionRepository;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.presupuesto.dto.CapituloRaizComparacion;
import ec.uce.propuestas.presupuesto.dto.ComparacionItem;
import ec.uce.propuestas.presupuesto.dto.ComparacionVersionesResponse;
import ec.uce.propuestas.presupuesto.dto.PresupuestoVersionCrearRequest;
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
import ec.uce.propuestas.recalculo.Alcance;
import ec.uce.propuestas.recalculo.RecalculoService;
import ec.uce.propuestas.usuario.audit.EventoLogActividad;
import ec.uce.propuestas.usuario.audit.service.LogActividadService;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plan 024 (P-31) — capa de servicio del versionado del presupuesto.
 *
 * <p>Responsabilidades cubiertas por este plan:
 * <ul>
 *   <li>{@link #copiarVersion(UUID, UUID, PresupuestoVersionCrearRequest, Long)} —
 *       deep copy íntegro del origen: capítulos (con jerarquía), rubros, APUs
 *       (con sus 4 secciones y detalles), cronograma y actividad. Los insumos
 *       NO se copian — la base es del proyecto (DM §3). Una sola operación
 *       transaccional, terminada con
 *       {@link RecalculoService#recalcular(Alcance)} sobre
 *       {@link Alcance.Version} del nuevo id para que los totales del nuevo
 *       presupuesto coincidan bit-a-bit con los del origen (TC-P31-01).</li>
 *   <li>{@link #marcarVigente(UUID, Long)} — toggle transaccional de la
 *       bandera {@code es_vigente}: marca el presupuesto del path como
 *       vigente y desmarca cualquier otra vigente del mismo proyecto (la
 *       invariante «exactamente una vigente» la protege el índice único
 *       parcial {@code ux_presupuesto_vigente} de V001 §2.8; este servicio
 *       no introduce restricción nueva).</li>
 *   <li>{@link #eliminar(UUID, Long)} — borra físicamente el presupuesto si
 *       no es vigente (cascadea capítulos, rubros, APUs, secciones,
 *       detalles, cronograma, actividad por V001). Si es vigente, devuelve
 *       409 {@code version-vigente-protegida}.</li>
 *   <li>{@link #comparar(UUID, UUID, Long)} — devuelve totales lado a lado y
 *       desglose por capítulo raíz (sólo el primer nivel, ordenado por
 *       {@code item} ascendente). Rechaza self (400) y distinto proyecto
 *       (400).</li>
 * </ul>
 *
 * <p>Decisiones locked:
 * <ul>
 *   <li>Las FK {@code cronograma.presupuesto_id} y {@code actividad.rubro_id}
 *       se remapean con SQL nativo dentro de este mismo servicio (no se
 *       introducen entidades JPA {@code cronograma}/{@code actividad} — están
 *       diferidas a I-08, ver {@code docs/modulos/05-presupuesto/06-versionado-comparacion.md}
 *       STOP (D)). El resto del deep copy pasa por el ORM estándar, igual que
 *       {@code ApuDuplicarService}.</li>
 *   <li>Se copia {@code especificacion_tecnica} del APU (P-31 contrato
 *       preserva el pliego); {@code porcentaje_descuento} es inerte (Plan 015)
 *       y el deep copy NO la propaga — la columna queda en su default 0.</li>
 *   <li>El remapeo de FK entre origen y copia usa claves de dominio estables
 *       dentro de cada presupuesto (no BIGINTs, que cambian): capítulos vía
 *       {@code item} (UNIQUE por presupuesto), APUs vía {@code codigo}
 *       (UNIQUE por presupuesto). Esa elección evita la fragilidad de
 *       emparejar filas por orden de inserción.</li>
 *   <li>La nueva versión se inserta con {@code total = 0}; los totales se
 *       recalculan al final invocando el write-through de
 *       {@link RecalculoService}.</li>
 *   <li>Pessimistic lock por proyecto ({@code SELECT id FROM proyecto WHERE id =
 *       FOR UPDATE}) antes del INSERT evita que dos POSTs concurrentes
 *       elijan el mismo {@code version} (la UNIQUE {@code (proyecto_id,
 *       version)} de V001 protegería, pero se traduce en 500 sin este
 *       lock). La operación es data-access only (vive en
 *       {@code PresupuestoRepository#lockProyectoRow}) — este servicio no
 *       modifica el módulo {@code proyecto}.</li>
 *   <li>El origen no se muta: ninguna asignación de campos de origen, ningún
 *       DELETE sobre filas del origen, ningún UPDATE de totales del origen.
 *       Las pruebas TC-P31-01 y TC-P31-04 verifican explícitamente que el
 *       origen permanece bit-a-bit intacto.</li>
 * </ul>
 */
@ApplicationScoped
public class VersionadoService {

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    CapituloRepository capituloRepository;

    @Inject
    RubroRepository rubroRepository;

    @Inject
    ApuRepository apuRepository;

    @Inject
    ApuSeccionRepository apuSeccionRepository;

    @Inject
    ApuDetalleRepository apuDetalleRepository;

    @Inject
    ProyectoRepository proyectoRepository;

    @Inject
    RecalculoService recalculoService;

    @Inject
    LogActividadService logActividadService;

    /**
     * Devuelve el EntityManager del contexto de persistencia actual reusando
     * el accessor de Panache. Útil para flushes explícitos entre inserciones
     * del deep copy y para las nativas de SQL de cronograma y actividad (sin
     * entidad JPA — I-08 las introduce). Se accede vía el repositorio de
     * presupuesto (patrón del módulo) en lugar de inyectar el EM directamente.
     */
    private jakarta.persistence.EntityManager em() {
        return presupuestoRepository.getEntityManager();
    }

    // ──────────────────────────────────────────────────────────────────────
    // POST /proyectos/{proyectoId}/presupuestos — copiar versión (deep copy)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Plan 024 — deep copy completo. Verifica owner del proyecto del path y
     * owner+proyecto del origen: ajeno al proyecto del path es 400
     * {@code validacion}; ajeno al caller es 404 {@code no-encontrado}.
     *
     * <p>El método entero corre en una transacción; si el deep copy o el
     * recálculo fallan, todo aborta (rollback del {@code INSERT} inicial,
     * del lock pesimista y de las inserciones cascadas).</p>
     */
    @Transactional
    public PresupuestoVersionResponse copiarVersion(
            UUID proyectoPublicId, UUID origenPublicId, PresupuestoVersionCrearRequest req, Long callerUsuarioId) {

        Proyecto proyecto = resolverProyecto(proyectoPublicId, callerUsuarioId);
        Presupuesto origen = resolverOrigenEnProyecto(proyecto.id, origenPublicId, callerUsuarioId);

        // Lock pesimista del proyecto: serializa la sección crítica
        // «leer max(version) → insertar nueva versión» para que dos POSTs
        // concurrentes no elijan la misma `version` y choquen con la UNIQUE.
        presupuestoRepository.lockProyectoRow(proyecto.id);

        Short maxVersion = presupuestoRepository.maxVersionDeProyecto(proyecto.id);
        short nuevaVersion = (short) (maxVersion == null ? 1 : maxVersion + 1);

        Presupuesto nuevo = new Presupuesto();
        nuevo.proyectoId = proyecto.id;
        nuevo.version = nuevaVersion;
        nuevo.esVigente = false;
        nuevo.origenId = origen.id;
        nuevo.notas = req.notas();
        nuevo.porcentajeIndirecto = origen.porcentajeIndirecto;
        nuevo.total = BigDecimal.ZERO;
        presupuestoRepository.persist(nuevo);
        em().flush();

        copiarCapitulosRecursivo(origen.id, nuevo.id);
        em().flush();

        copiarApus(origen.id, nuevo.id);
        em().flush();

        copiarRubros(origen.id, nuevo.id);
        em().flush();

        copiarCronogramaYActividad(origen.id, nuevo.id);
        em().flush();

        // Recálculo write-through (P-30): totales raíz → capítulos → rubros.
        // Sin esto los totales quedan en 0 y el contrato P-31 (totales
        // bit-a-bit idénticos al origen) no se cumple.
        recalculoService.recalcular(new Alcance.Version(nuevo.id));
        em().flush();
        em().clear();

        Presupuesto persistido = presupuestoRepository.findById(nuevo.id);
        if (persistido == null) {
            // No debería ocurrir — fue persistido arriba. Defensa de cinturón.
            throw ProblemaException.noEncontrado("Presupuesto no encontrado");
        }
        logActividadService.emitir(
                callerUsuarioId,
                EventoLogActividad.PRESUPUESTO_VERSION_CREADA,
                "presupuesto",
                persistido.publicId,
                Map.of("presupuestoOrigenId", origen.publicId, "versionNueva", persistido.version.intValue()));
        return PresupuestoMapper.toVersionResponse(persistido, origen.publicId);
    }

    /**
     * Plan 024 — copia capítulos preservando jerarquía. Recorrido DFS
     * pre-order sobre la jerarquía origen: cada nivel conserva el orden por
     * {@code orden} y los {@code item} exactos (la propia estructura del
     * origen ya los garantiza). El {@code parent_id} se remite al id recién
     * asignado en la copia.
     */
    private void copiarCapitulosRecursivo(Long origenPresupuestoId, Long nuevoPresupuestoId) {
        Map<Long, Long> viejoIdANuevoId = new HashMap<>();
        List<Capitulo> raicesOrigen = capituloRepository
                .find(
                        "presupuestoId = :pid and parentId is null order by orden",
                        Parameters.with("pid", origenPresupuestoId))
                .list();
        for (Capitulo raiz : raicesOrigen) {
            copiarSubarbolDeCapitulos(raiz, null, nuevoPresupuestoId, viejoIdANuevoId);
        }
    }

    private void copiarSubarbolDeCapitulos(
            Capitulo src, Long nuevoParentId, Long nuevoPresupuestoId, Map<Long, Long> viejoIdANuevoId) {
        Capitulo nuevo = new Capitulo();
        nuevo.presupuestoId = nuevoPresupuestoId;
        nuevo.parentId = nuevoParentId;
        nuevo.item = src.item;
        nuevo.descripcion = src.descripcion;
        nuevo.orden = src.orden;
        nuevo.total = BigDecimal.ZERO;
        capituloRepository.persist(nuevo);
        em().flush();
        viejoIdANuevoId.put(src.id, nuevo.id);

        List<Capitulo> hijos = capituloRepository.listarHermanosEnPresupuesto(src.presupuestoId, src.id);
        // `listarHermanosEnPresupuesto` ya ordena por `orden`, pero por
        // defensa aplicamos un sort secundario por id para empates estables.
        hijos.sort(Comparator.comparingInt((Capitulo c) -> c.orden == null ? Short.MAX_VALUE : c.orden)
                .thenComparing(c -> c.id));
        for (Capitulo hijo : hijos) {
            copiarSubarbolDeCapitulos(hijo, nuevo.id, nuevoPresupuestoId, viejoIdANuevoId);
        }
    }

    /**
     * Plan 024 — clona cada APU del presupuesto origen a uno nuevo del
     * destino (preservando {@code codigo}, {@code descripcion}, {@code unidad},
     * {@code porcentaje_indirecto} y {@code especificacion_tecnica}; el
     * {@code porcentaje_descuento} es inerte desde Plan 015 y queda en su
     * default 0). Crea las 4 secciones canónicas y todos los detalles,
     * manteniendo el orden, los overrides y el {@code insumoId} (la base es
     * del proyecto, no se clona — DM §3).
     */
    private void copiarApus(Long origenPresupuestoId, Long nuevoPresupuestoId) {
        List<Apu> apusOrigen = apuRepository.listarDePresupuesto(origenPresupuestoId, null, 0, Integer.MAX_VALUE);
        for (Apu origen : apusOrigen) {
            Apu copia = new Apu();
            copia.presupuestoId = nuevoPresupuestoId;
            copia.codigo = origen.codigo;
            copia.descripcion = origen.descripcion;
            copia.unidad = origen.unidad;
            copia.porcentajeIndirecto = origen.porcentajeIndirecto;
            copia.especificacionTecnica = origen.especificacionTecnica;
            copia.costoDirecto = BigDecimal.ZERO;
            copia.costoIndirecto = BigDecimal.ZERO;
            copia.costoTotal = BigDecimal.ZERO;
            apuRepository.persist(copia);
            em().flush();

            copiarSeccionesYDetalles(origen.id, copia.id);
        }
    }

    private void copiarSeccionesYDetalles(Long origenApuId, Long copiaApuId) {
        // El repository ya ordena por `orden` ascendente; preservamos la
        // jerarquía canónica M/N/O/P y el orden original tal cual.
        List<ApuSeccion> secciones = apuSeccionRepository.listarDeApu(origenApuId);
        for (ApuSeccion src : secciones) {
            ApuSeccion dst = new ApuSeccion();
            dst.apuId = copiaApuId;
            dst.tipo = src.tipo;
            dst.subtotal = BigDecimal.ZERO;
            dst.orden = src.orden;
            apuSeccionRepository.persist(dst);
            em().flush();

            List<ApuDetalle> detalles = apuDetalleRepository.listarDeSeccion(src.id);
            for (ApuDetalle d : detalles) {
                ApuDetalle copia = new ApuDetalle();
                copia.seccionId = dst.id;
                copia.insumoId = d.insumoId;
                copia.descripcion = d.descripcion;
                copia.orden = d.orden;
                copia.esHerramientaMenor = d.esHerramientaMenor;
                copia.cantidad = d.cantidad;
                copia.tarifaJornal = d.tarifaJornal;
                copia.costoHora = BigDecimal.ZERO;
                copia.rendimiento = d.rendimiento;
                copia.unidad = d.unidad;
                copia.precioUnitarioTarifa = d.precioUnitarioTarifa;
                copia.costo = BigDecimal.ZERO;
                apuDetalleRepository.persist(copia);
            }
        }
    }

    /**
     * Plan 024 — clona rubros con FK remapeadas vía claves de dominio:
     * {@code item} del capítulo y {@code codigo} del APU. Como en una sola
     * versión {@code item} es UNIQUE y {@code codigo} también, basta con estas
     * claves para remitir las FK sin riesgo de ambigüedad.
     *
     * <p>Los totales quedan en 0; el write-through de
     * {@link RecalculoService} los propaga al final del deep copy.</p>
     */
    private void copiarRubros(Long origenPresupuestoId, Long nuevoPresupuestoId) {
        Map<String, Long> itemANuevoCap = itemANuevoId(origenPresupuestoId, nuevoPresupuestoId);
        Map<String, Long> codigoANuevoApu = codigoANuevoId(origenPresupuestoId, nuevoPresupuestoId);

        List<Rubro> origenRubros = rubroRepository.listarPorPresupuesto(origenPresupuestoId);
        for (Rubro r : origenRubros) {
            Capitulo capOrigen = capituloRepository.findById(r.capituloId);
            if (capOrigen == null) {
                continue;
            }
            Long nuevoCapId = itemANuevoCap.get(capOrigen.item);
            if (nuevoCapId == null) {
                continue;
            }

            Apu apuOrigen = apuRepository.findById(r.apuId);
            if (apuOrigen == null) {
                continue;
            }
            Long nuevoApuId = codigoANuevoApu.get(apuOrigen.codigo);
            if (nuevoApuId == null) {
                continue;
            }

            Rubro copia = new Rubro();
            copia.capituloId = nuevoCapId;
            copia.apuId = nuevoApuId;
            copia.item = r.item;
            copia.codigo = r.codigo;
            copia.descripcion = r.descripcion;
            copia.unidad = r.unidad;
            copia.cantidad = r.cantidad;
            copia.precioUnitario = BigDecimal.ZERO;
            copia.precioTotal = BigDecimal.ZERO;
            rubroRepository.persist(copia);
        }
    }

    /**
     * Mapa {@code item → id nuevo} entre origen y copia, derivado de los
     * capítulos recién clonados (que ya fueron persistidos y flusheados). Como
     * {@code item} es UNIQUE por presupuesto, basta con un lookup directo en
     * la copia. Esta función resuelve la indirección al pasar de FK origen a
     * FK copia.
     */
    private Map<String, Long> itemANuevoId(Long origenPresupuestoId, Long nuevoPresupuestoId) {
        Map<String, Long> result = new HashMap<>();
        List<Capitulo> origenCapitulos = capituloRepository.listarPorPresupuesto(origenPresupuestoId);
        List<Capitulo> nuevosCapitulos = capituloRepository.listarPorPresupuesto(nuevoPresupuestoId);
        Map<String, Long> nuevoItemIndex = new HashMap<>();
        for (Capitulo nc : nuevosCapitulos) {
            nuevoItemIndex.put(nc.item, nc.id);
        }
        for (Capitulo oc : origenCapitulos) {
            Long nuevoId = nuevoItemIndex.get(oc.item);
            if (nuevoId != null) {
                result.put(oc.item, nuevoId);
            }
        }
        return result;
    }

    /** Mapa {@code codigo → id nuevo} entre origen y copia (APU). */
    private Map<String, Long> codigoANuevoId(Long origenPresupuestoId, Long nuevoPresupuestoId) {
        Map<String, Long> result = new HashMap<>();
        List<Apu> origen = apuRepository.listarDePresupuesto(origenPresupuestoId, null, 0, Integer.MAX_VALUE);
        List<Apu> nuevos = apuRepository.listarDePresupuesto(nuevoPresupuestoId, null, 0, Integer.MAX_VALUE);
        Map<String, Long> nuevoCodigoIndex = new HashMap<>();
        for (Apu a : nuevos) {
            nuevoCodigoIndex.put(a.codigo, a.id);
        }
        for (Apu a : origen) {
            Long nuevoId = nuevoCodigoIndex.get(a.codigo);
            if (nuevoId != null) {
                result.put(a.codigo, nuevoId);
            }
        }
        return result;
    }

    /**
     * Plan 024/027 — copia cronograma y actividad usando SQL nativo porque
     * existen entidades JPA para esas tablas (I-08 las introduce). Se
     * conservan {@code unidad_tiempo}, {@code numero_periodos},
     * {@code total_general_revisado}, {@code fecha_revision} y {@code presupuesto_fingerprint_revisado}
     * (añadido por V009) del origen;
     * {@code updated_at} lo refresca el disparador DDL ({@code DEFAULT now()}).
     *
     * <p>{@code public_id} se omite explícitamente: el DEFAULT
     * {@code uuidv7()} de la BD genera una identidad fresca para el
     * cronograma y para cada actividad copiada. El trigger de
     * inmutabilidad de V009 bloquea cualquier intento de reescribir el
     * {@code public_id} desde SQL posterior.</p>
     *
     * <p>Si el origen no tiene cronograma, no se hace nada. Para actividad, el
     * FK {@code rubro_id} se remite al rubro del destino con el mismo
     * {@code codigo} (la UNIQUE sobre {@code apu_id} garantiza el vínculo
     * 1:1 con el nuevo APU copiado).</p>
     */
    private void copiarCronogramaYActividad(Long origenPresupuestoId, Long nuevoPresupuestoId) {
        @SuppressWarnings("unchecked")
        List<Number> cronogramaIds = em().createNativeQuery("select id from cronograma where presupuesto_id = ?1")
                .setParameter(1, origenPresupuestoId)
                .getResultList();
        if (cronogramaIds.isEmpty()) {
            return;
        }
        long origenCronogramaId = cronogramaIds.get(0).longValue();

        Object nuevoCronogramaIdObj = em().createNativeQuery(
                        "insert into cronograma (presupuesto_id, unidad_tiempo, numero_periodos, "
                                + "total_general_revisado, fecha_revision, presupuesto_fingerprint_revisado, "
                                + "updated_at) "
                                + "select ?1, unidad_tiempo, numero_periodos, total_general_revisado, "
                                + "       fecha_revision, presupuesto_fingerprint_revisado, now() "
                                + "from cronograma where id = ?2 "
                                + "returning id")
                .setParameter(1, nuevoPresupuestoId)
                .setParameter(2, origenCronogramaId)
                .getSingleResult();
        long nuevoCronogramaId = ((Number) nuevoCronogramaIdObj).longValue();

        @SuppressWarnings("unchecked")
        List<Object[]> actividades = em().createNativeQuery(
                        "select a.peso_ponderado, a.avance_por_periodo::text, r.codigo "
                                + "from actividad a join rubro r on r.id = a.rubro_id "
                                + "where a.cronograma_id = ?1")
                .setParameter(1, origenCronogramaId)
                .getResultList();

        for (Object[] row : actividades) {
            BigDecimal pesoPonderado = (BigDecimal) row[0];
            String avancePorPeriodo = (String) row[1];
            String rubroCodigo = (String) row[2];

            @SuppressWarnings("unchecked")
            List<Number> nuevoRubroIds = em().createNativeQuery("select r.id from rubro r "
                            + "join capitulo c on c.id = r.capitulo_id "
                            + "where c.presupuesto_id = ?1 and r.codigo = ?2 "
                            + "limit 1")
                    .setParameter(1, nuevoPresupuestoId)
                    .setParameter(2, rubroCodigo)
                    .getResultList();
            if (nuevoRubroIds.isEmpty()) {
                continue;
            }
            long nuevoRubroId = nuevoRubroIds.get(0).longValue();

            em().createNativeQuery(
                            "insert into actividad (cronograma_id, rubro_id, peso_ponderado, avance_por_periodo) "
                                    + "values (?1, ?2, ?3, cast(?4 as jsonb))")
                    .setParameter(1, nuevoCronogramaId)
                    .setParameter(2, nuevoRubroId)
                    .setParameter(3, pesoPonderado)
                    .setParameter(4, avancePorPeriodo)
                    .executeUpdate();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // POST /presupuestos/{presupuestoId}/vigente
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Plan 024 — marcar vigente. Idempotente (200 si ya estaba vigente).
     * Resuelve owner; bloquea atómicamente la fila; cualquier otra vigente
     * del mismo proyecto pasa a {@code es_vigente = false} antes de activar la
     * nueva. La invariante «una vigente por proyecto» la protege el índice
     * parcial único de V001 §2.8.
     */
    @Transactional
    public PresupuestoVersionResponse marcarVigente(UUID presupuestoPublicId, Long callerUsuarioId) {
        Presupuesto p = presupuestoRepository
                .findByPublicIdAndOwnerScope(presupuestoPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));

        // Serializa todos los toggles del proyecto, incluso cuando dos
        // peticiones concurrentes apuntan a versiones distintas.
        presupuestoRepository.lockProyectoRow(p.proyectoId);
        em().refresh(p);

        if (p.esVigente) {
            UUID origenId = resolverOrigenPublicId(p.origenId);
            return PresupuestoMapper.toVersionResponse(p, origenId);
        }

        UUID previamenteVigenteId = presupuestoRepository
                .findVigenteDeProyecto(p.proyectoId)
                .map(vigente -> vigente.publicId)
                .orElse(null);

        // 1) Desmarcar cualquier vigente del mismo proyecto.
        em().createNativeQuery("update presupuesto set es_vigente = false, updated_at = now() "
                        + "where proyecto_id = ?1 and es_vigente = true and id <> ?2")
                .setParameter(1, p.proyectoId)
                .setParameter(2, p.id)
                .executeUpdate();

        // 2) Marcar la nueva vigente.
        em().createNativeQuery("update presupuesto set es_vigente = true, updated_at = now() where id = ?1")
                .setParameter(1, p.id)
                .executeUpdate();
        em().flush();
        em().clear();

        Presupuesto persisted = presupuestoRepository.findById(p.id);
        if (persisted == null) {
            throw ProblemaException.noEncontrado("Presupuesto no encontrado");
        }
        UUID origenId = resolverOrigenPublicId(persisted.origenId);
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("version", persisted.version.intValue());
        detalle.put("presupuestoPreviamenteVigenteId", previamenteVigenteId);
        logActividadService.emitir(
                callerUsuarioId,
                EventoLogActividad.PRESUPUESTO_VERSION_ACTIVADA,
                "presupuesto",
                persisted.publicId,
                detalle);
        return PresupuestoMapper.toVersionResponse(persisted, origenId);
    }

    // ──────────────────────────────────────────────────────────────────────
    // DELETE /presupuestos/{presupuestoId}
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Plan 024 — borrar una versión no vigente. La vigente devuelve 409
     * {@code version-vigente-protegida}. La cascade de V001 barre capítulos,
     * rubros, APUs, secciones, detalles y cronograma del presupuesto borrado;
     * una versión hermana permanece intacta.
     */
    @Transactional
    public void eliminar(UUID presupuestoPublicId, Long callerUsuarioId) {
        Presupuesto p = presupuestoRepository
                .findByPublicIdAndOwnerScope(presupuestoPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));

        if (p.esVigente) {
            throw ProblemaException.conflicto(
                    "version-vigente-protegida",
                    "La versión vigente no puede eliminarse; marque otra como vigente primero.");
        }

        em().createNativeQuery("delete from presupuesto where id = ?1")
                .setParameter(1, p.id)
                .executeUpdate();
        em().flush();
        em().clear();
    }

    // ──────────────────────────────────────────────────────────────────────
    // GET /presupuestos/{presupuestoId}/comparar?con=<UUIDv7>
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Plan 024 — comparar dos versiones. Rechaza self (400) y distinto
     * proyecto (400). Devuelve totales lado a lado y capítulos raíz ordenados
     * por {@code item} ascendente. Ajeno o inexistente devuelve 404.
     */
    @Transactional
    public ComparacionVersionesResponse comparar(UUID presupuestoPath, UUID presupuestoCon, Long callerUsuarioId) {
        if (presupuestoPath.equals(presupuestoCon)) {
            throw ProblemaException.validacion("No se puede comparar una versión consigo misma");
        }

        Presupuesto a = presupuestoRepository
                .findByPublicIdAndOwnerScope(presupuestoPath, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));
        Presupuesto b = presupuestoRepository
                .findByPublicIdAndOwnerScope(presupuestoCon, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));

        if (!a.proyectoId.equals(b.proyectoId)) {
            throw ProblemaException.validacion("Las versiones a comparar deben pertenecer al mismo proyecto");
        }

        return new ComparacionVersionesResponse(List.of(construirItem(a), construirItem(b)));
    }

    private ComparacionItem construirItem(Presupuesto p) {
        List<Capitulo> capitulos = capituloRepository.listarPorPresupuesto(p.id);
        List<Capitulo> raices = capitulos.stream()
                .filter(c -> c.parentId == null)
                .sorted(Comparator.comparing(c -> c.item == null ? "" : c.item))
                .toList();
        List<CapituloRaizComparacion> porRaiz = new ArrayList<>(raices.size());
        for (Capitulo c : raices) {
            porRaiz.add(new CapituloRaizComparacion(c.item, c.descripcion, decimalScale6(c.total)));
        }
        return new ComparacionItem(p.publicId, p.version, decimalScale6(p.total), porRaiz);
    }

    /**
     * Helper local equivalente a {@code PresupuestoMapper.toDecimalString},
     * replicado aquí porque la utilidad es package-private del módulo
     * {@code presupuesto/mapper} (no se incluye en los surfaces editables
     * de este plan). Produce una cadena decimal a escala 6 con
     * {@code HALF_UP}, conservando ceros a la derecha para un shape estable.
     */
    private static String decimalScale6(BigDecimal value) {
        BigDecimal nonNull = value == null ? BigDecimal.ZERO : value;
        return nonNull.setScale(ESCALA, RoundingMode.HALF_UP).toPlainString();
    }

    /** Escala contractual P-30 (P-31 hereda la convención). */
    private static final int ESCALA = 6;

    // ──────────────────────────────────────────────────────────────────────
    // Helpers de resolución
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Resuelve el proyecto del path (UUIDv7 + owner). Ajeno o inexistente →
     * 404.
     */
    private Proyecto resolverProyecto(UUID proyectoPublicId, Long callerUsuarioId) {
        return proyectoRepository
                .findByPublicIdAndOwnerScope(proyectoPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Proyecto no encontrado"));
    }

    /**
     * Resuelve el origen con dos pasos: primero por owner (ajeno o inexistente
     * → 404). Si pertenece al caller, verifica que sea del proyecto del path
     * (mismo owner pero OTRO proyecto → 400 {@code validacion}, ya que el
     * caller «ve» la fila pero el origen no es del proyecto donde se quiere
     * crear la copia).
     */
    private Presupuesto resolverOrigenEnProyecto(Long proyectoIdDelPath, UUID origenPublicId, Long callerUsuarioId) {
        Presupuesto origen = presupuestoRepository
                .findByPublicIdAndOwnerScope(origenPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));
        if (!origen.proyectoId.equals(proyectoIdDelPath)) {
            throw ProblemaException.validacion("El presupuesto origen debe pertenecer al mismo proyecto del path");
        }
        return origen;
    }

    /**
     * Resuelve el {@code publicId} UUIDv7 del presupuesto apuntado por el FK
     * interno {@code origenId}, o {@code null} si no hay origen o si la fila
     * fue borrada (cliente pudo borrar el origen — el contrato no rechaza el
     * caso para no perder la lectura del vigente). Usa
     * {@code findByIdOptional} para evitar NPE en filas inexistentes.
     */
    private UUID resolverOrigenPublicId(Long origenIdInterno) {
        if (origenIdInterno == null) {
            return null;
        }
        return presupuestoRepository
                .findByIdOptional(origenIdInterno)
                .map(o -> o.publicId)
                .orElse(null);
    }
}
