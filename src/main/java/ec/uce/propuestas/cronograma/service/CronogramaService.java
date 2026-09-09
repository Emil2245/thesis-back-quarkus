package ec.uce.propuestas.cronograma.service;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.cronograma.dto.ActividadProgramarRequest;
import ec.uce.propuestas.cronograma.dto.CronogramaConfigurarRequest;
import ec.uce.propuestas.cronograma.dto.CronogramaCrearRequest;
import ec.uce.propuestas.cronograma.dto.CronogramaResponse;
import ec.uce.propuestas.cronograma.dto.PerdidaAvanceResponse;
import ec.uce.propuestas.cronograma.entity.Actividad;
import ec.uce.propuestas.cronograma.entity.Cronograma;
import ec.uce.propuestas.cronograma.mapper.CronogramaMapper;
import ec.uce.propuestas.cronograma.mapper.CronogramaMapper.ActividadConRubro;
import ec.uce.propuestas.cronograma.repository.ActividadRepository;
import ec.uce.propuestas.cronograma.repository.CronogramaRepository;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import ec.uce.propuestas.usuario.audit.EventoLogActividad;
import ec.uce.propuestas.usuario.audit.service.LogActividadService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plan 028 (P-33) — ciclo de vida del agregado de cronograma: alta 1:1 con
 * autoimportación, lectura owner-scoped y reemplazo de configuración.
 *
 * <p>Decisiones locked:
 * <ul>
 *   <li><strong>Sección crítica.</strong> El alta bloquea la fila del
 *       presupuesto ({@code SELECT ... FOR UPDATE}) tras resolver el owner y
 *       recién entonces comprueba/inserta. No se puede bloquear la fila del
 *       cronograma porque todavía no existe; la {@code UNIQUE
 *       (cronograma.presupuesto_id)} sigue siendo la defensa final y una
 *       carrera se traduce a 409 {@code cronograma-ya-existe}, nunca 500.</li>
 *   <li><strong>Autoimportación.</strong> Exactamente una actividad por rubro
 *       de la versión, en orden presupuestario ({@code item}, desempate por id
 *       interno), con {@code avancePorPeriodo = {}} y peso cerrado por
 *       {@link PesoPonderadoCalculador}. El body nunca aporta actividades,
 *       identidades ni pesos. La copia de versión (Plan 024) NO pasa por aquí:
 *       su SQL nativo ya inserta cronograma y actividades, de modo que no se
 *       autoimporta dos veces.</li>
 *   <li><strong>Reducción.</strong> Reducir períodos dejando claves fuera de
 *       rango, o cambiar la unidad con mapas no vacíos, exige
 *       {@code confirmarPerdida=true}; sin confirmación se devuelve 409
 *       {@code configuracion-cronograma-requiere-confirmacion} con la lista
 *       determinista de pérdidas y sin mutación alguna (D-10). La
 *       confirmación revalida bajo lock y elimina únicamente las claves fuera
 *       del nuevo rango.</li>
 *   <li><strong>Revisión.</strong> La creación captura total, fingerprint
 *       canónico y fecha en la misma transacción, por lo que nace no stale. La
 *       configuración no refresca esos marcadores; el comando explícito de
 *       revisión continúa fuera del alcance de este plan.</li>
 * </ul>
 *
 * <p>Plan 029 (P-34) — extiende con programación de actividades:
 * <ul>
 *   <li><strong>Atomicidad.</strong> Cada PATCH adquiere el lock del
 *       presupuesto, refresca el cronograma y la actividad bajo el lock,
 *       revalida el mapa completo bajo el {@code numeroPeriodos} vigente,
 *       persiste una sola vez y devuelve la respuesta canónica completa. Un
 *       4xx deja el agregado intacto.</li>
 *   <li><strong>Operaciones semánticas.</strong> Las cuatro operaciones
 *       congeladas por Plan 026 ({@code REEMPLAZAR_AVANCES},
 *       {@code DISTRIBUIR_UNIFORME}, {@code MOVER_SEGMENTO},
 *       {@code REDIMENSIONAR_SEGMENTO}) se sirven aquí sin aliases; la
 *       frontera común de validación es
 *       {@link AvancePatchParser}; los algoritmos puros viven en
 *       {@link AvancePatchCalculos}.</li>
 *   <li><strong>Borrador válido.</strong> Un mapa incompleto, sobreasignado
 *       o vacío se persiste como borrador con desviación visible; no se
 *       rechaza automáticamente.</li>
 * </ul>
 */
@ApplicationScoped
public class CronogramaService {

    @Inject
    CronogramaRepository cronogramaRepository;

    @Inject
    ActividadRepository actividadRepository;

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    RubroRepository rubroRepository;

    @Inject
    CronogramaMapper mapper;

    @Inject
    LogActividadService logActividadService;

    /** Orden presupuestario canónico: {@code item} ascendente, desempate por id interno. */
    private static final Comparator<Rubro> ORDEN_PRESUPUESTARIO =
            Comparator.comparing((Rubro r) -> r.item == null ? "" : r.item).thenComparing(r -> r.id);

    // ──────────────────────────────────────────────────────────────────────
    // GET /presupuestos/{presupuestoId}/cronograma
    // ──────────────────────────────────────────────────────────────────────

    /** Lectura owner-scoped. Sin cronograma → 404 (nunca un 200 con objeto vacío). */
    public CronogramaResponse obtener(UUID presupuestoPublicId, Long callerUsuarioId) {
        Presupuesto presupuesto = resolverPresupuesto(presupuestoPublicId, callerUsuarioId);
        Cronograma cronograma = cronogramaRepository
                .findByPresupuestoAndOwnerScope(presupuesto.id, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Cronograma no encontrado"));
        List<ActividadConRubro> actividades = cargarActividades(cronograma.id);
        return respuesta(cronograma, presupuesto, actividades);
    }

    // ──────────────────────────────────────────────────────────────────────
    // POST /presupuestos/{presupuestoId}/cronograma
    // ──────────────────────────────────────────────────────────────────────

    @Transactional
    public CronogramaResponse crear(UUID presupuestoPublicId, CronogramaCrearRequest request, Long callerUsuarioId) {
        Presupuesto presupuesto = resolverPresupuesto(presupuestoPublicId, callerUsuarioId);

        // Serializa la sección crítica «comprobar 1:1 → insertar»: la fila del
        // cronograma aún no existe, así que se bloquea la del presupuesto.
        presupuestoRepository.lockPresupuestoRow(presupuesto.id);
        presupuestoRepository.getEntityManager().refresh(presupuesto);

        if (cronogramaRepository
                .findByPresupuestoAndOwnerScope(presupuesto.id, callerUsuarioId)
                .isPresent()) {
            throw ProblemaException.conflicto(
                    "cronograma-ya-existe", "El presupuesto ya tiene un cronograma configurado");
        }

        Cronograma cronograma = new Cronograma();
        cronograma.presupuestoId = presupuesto.id;
        cronograma.unidadTiempo = request.unidadTiempo();
        cronograma.numeroPeriodos = (short) request.numeroPeriodos();
        try {
            cronogramaRepository.persist(cronograma);
            cronogramaRepository.flush();
        } catch (PersistenceException e) {
            if (esUniqueCronogramaPresupuesto(e)) {
                throw ProblemaException.conflicto(
                        "cronograma-ya-existe", "El presupuesto ya tiene un cronograma configurado");
            }
            throw e;
        }

        List<Rubro> rubros = new ArrayList<>(rubroRepository.listarPorPresupuesto(presupuesto.id));
        rubros.sort(ORDEN_PRESUPUESTARIO);
        List<BigDecimal> pesos = PesoPonderadoCalculador.calcular(
                rubros.stream().map(r -> r.precioTotal).toList(), presupuesto.total);

        for (int i = 0; i < rubros.size(); i++) {
            Actividad actividad = new Actividad();
            actividad.cronogramaId = cronograma.id;
            actividad.rubroId = rubros.get(i).id;
            actividad.pesoPonderado = pesos.get(i);
            actividad.avancePorPeriodo = "{}";
            actividadRepository.persist(actividad);
        }
        actividadRepository.flush();

        List<ActividadConRubro> actividades = cargarActividades(cronograma.id);
        cronograma.totalGeneralRevisado = escala6(presupuesto.total);
        cronograma.presupuestoFingerprintRevisado = fingerprint(presupuesto.id);
        cronograma.fechaRevision = Instant.now();
        cronogramaRepository.flush();

        return respuesta(cronograma, presupuesto, actividades);
    }

    // ──────────────────────────────────────────────────────────────────────
    // PATCH /cronogramas/{cronogramaId}/actividades/{actividadId}
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Lee el {@code numeroPeriodos} vigente del cronograma. Usado por la
     * frontera ({@link ec.uce.propuestas.cronograma.resource.ActividadProgramarResource})
     * para validar que las claves del body caigan en {@code 1..n} antes de
     * delegar a {@link #programarActividad}. Lanza 404 si el cronograma es
     * ajeno o no existe.
     */
    public int numeroPeriodosDe(UUID cronogramaPublicId, Long callerUsuarioId) {
        Cronograma cronograma = cronogramaRepository
                .findByPublicIdAndOwnerScope(cronogramaPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Cronograma no encontrado"));
        return cronograma.numeroPeriodos == null ? 0 : cronograma.numeroPeriodos.intValue();
    }

    /**
     * Punto de entrada del PATCH: las cuatro operaciones semánticas se
     * evalúan bajo lock pesimista de la fila del {@code presupuesto},
     * refrescan la actividad, revalidan y persisten el mapa completo en una
     * sola transacción. El error 4xx deja el estado intacto (atomicidad de
     * validación).
     */
    @Transactional
    public CronogramaResponse programarActividad(
            UUID cronogramaPublicId,
            UUID actividadPublicId,
            ActividadProgramarRequest operacion,
            Long callerUsuarioId) {
        Cronograma cronograma = cronogramaRepository
                .findByPublicIdAndOwnerScope(cronogramaPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Cronograma no encontrado"));

        // Serializa contra la sincronización central (CronogramaSincronizacionService)
        // y contra otras mutaciones PATCH/PUT que ya usan el mismo lock.
        presupuestoRepository.lockPresupuestoRow(cronograma.presupuestoId);
        cronogramaRepository.getEntityManager().refresh(cronograma);
        Presupuesto presupuesto = presupuestoRepository.findById(cronograma.presupuestoId);
        presupuestoRepository.getEntityManager().refresh(presupuesto);

        Actividad actividad = actividadRepository
                .findByPublicIdAndCronogramaAndOwnerScope(actividadPublicId, cronogramaPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Actividad no encontrada"));
        actividadRepository.getEntityManager().refresh(actividad);

        int numeroPeriodos = cronograma.numeroPeriodos == null ? 0 : cronograma.numeroPeriodos.intValue();
        revalidarRangosVigentes(operacion, numeroPeriodos);
        Map<String, String> mapaActual = CronogramaMapper.leerMapa(actividad.avancePorPeriodo);

        Map<String, String> mapaDestino =
                aplicarOperacion(mapaActual, operacion, numeroPeriodos, actividad.pesoPonderado);

        actividad.avancePorPeriodo = CronogramaMapper.escribirMapa(mapaDestino);
        actividadRepository.persist(actividad);
        actividadRepository.flush();

        List<ActividadConRubro> actualizadas = cargarActividades(cronograma.id);
        logActividadService.emitir(
                callerUsuarioId,
                EventoLogActividad.CRONOGRAMA_EDITADO,
                "cronograma",
                cronograma.publicId,
                Map.of("operacion", operacionAuditoria(operacion)));
        return respuesta(cronograma, presupuesto, actualizadas);
    }

    /**
     * Revalida bajo el lock los rangos que el recurso parseó antes de entrar
     * a la sección crítica, usando el {@code numeroPeriodos} ya refrescado.
     */
    private static void revalidarRangosVigentes(ActividadProgramarRequest operacion, int numeroPeriodos) {
        if (operacion instanceof ActividadProgramarRequest.Reemplazar r) {
            for (String clave : r.avancePorPeriodo().keySet()) {
                try {
                    int periodo = Integer.parseInt(clave);
                    if (periodo < 1 || periodo > numeroPeriodos) {
                        throw ProblemaException.validacion(
                                "Clave de período fuera del rango 1.." + numeroPeriodos + ": " + clave);
                    }
                } catch (NumberFormatException ex) {
                    throw ProblemaException.validacion("Clave de período debe ser un entero positivo: " + clave);
                }
            }
        } else if (operacion instanceof ActividadProgramarRequest.Distribuir d) {
            for (int periodo : d.periodos()) {
                if (periodo < 1 || periodo > numeroPeriodos) {
                    throw ProblemaException.validacion("Período fuera del rango 1.." + numeroPeriodos);
                }
            }
        }
    }

    private static String operacionAuditoria(ActividadProgramarRequest operacion) {
        if (operacion instanceof ActividadProgramarRequest.Reemplazar) {
            return "programar.reemplazar_avances";
        }
        if (operacion instanceof ActividadProgramarRequest.Distribuir) {
            return "programar.distribuir_uniforme";
        }
        if (operacion instanceof ActividadProgramarRequest.Mover) {
            return "programar.mover_segmento";
        }
        if (operacion instanceof ActividadProgramarRequest.Redimensionar) {
            return "programar.redimensionar_segmento";
        }
        throw ProblemaException.validacion("Operación no reconocida");
    }

    private static Map<String, String> aplicarOperacion(
            Map<String, String> mapaActual,
            ActividadProgramarRequest operacion,
            int numeroPeriodos,
            BigDecimal pesoPonderado) {
        if (operacion instanceof ActividadProgramarRequest.Reemplazar r) {
            return r.avancePorPeriodo();
        }
        if (operacion instanceof ActividadProgramarRequest.Distribuir d) {
            BigDecimal base = pesoPonderado == null ? BigDecimal.ZERO : pesoPonderado;
            return AvancePatchCalculos.distribucionUniforme(base, d.periodos(), numeroPeriodos);
        }
        if (operacion instanceof ActividadProgramarRequest.Mover m) {
            AvancePatchCalculos.ResultadoMap r =
                    AvancePatchCalculos.mover(mapaActual, m.inicio(), m.fin(), m.delta(), numeroPeriodos);
            return r.mapa();
        }
        if (operacion instanceof ActividadProgramarRequest.Redimensionar rz) {
            AvancePatchCalculos.ResultadoMap r = AvancePatchCalculos.redimensionar(
                    mapaActual, rz.inicio(), rz.fin(), rz.nuevoInicio(), rz.nuevoFin(), numeroPeriodos);
            return r.mapa();
        }
        throw ProblemaException.validacion("Operación no reconocida");
    }

    // ──────────────────────────────────────────────────────────────────────
    // PUT /cronogramas/{cronogramaId}/configuracion
    // ──────────────────────────────────────────────────────────────────────

    @Transactional
    public CronogramaResponse configurar(
            UUID cronogramaPublicId, CronogramaConfigurarRequest request, Long callerUsuarioId) {
        Cronograma cronograma = cronogramaRepository
                .findByPublicIdAndOwnerScope(cronogramaPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Cronograma no encontrado"));

        // La consulta owner-scoped ocurre antes del lock y puede dejar una
        // instancia administrada obsoleta mientras espera. Se refresca dentro
        // de la sección crítica antes de comparar o cargar los mapas.
        presupuestoRepository.lockPresupuestoRow(cronograma.presupuestoId);
        presupuestoRepository.getEntityManager().refresh(cronograma);
        Presupuesto presupuesto = presupuestoRepository.findById(cronograma.presupuestoId);
        presupuestoRepository.getEntityManager().refresh(presupuesto);
        List<ActividadConRubro> actividades = cargarActividades(cronograma.id);

        int nuevoNumero = request.numeroPeriodos();
        boolean cambiaUnidad = !cronograma.unidadTiempo.equals(request.unidadTiempo());
        boolean hayDatos = false;

        List<PerdidaAvanceResponse> perdidas = new ArrayList<>();
        Map<Long, Map<String, String>> mapasConservados = new LinkedHashMap<>();
        for (ActividadConRubro par : actividades) {
            Map<String, String> mapa = CronogramaMapper.leerMapa(par.actividad().avancePorPeriodo);
            hayDatos = hayDatos || !mapa.isEmpty();
            Map<String, String> conservado = new LinkedHashMap<>();
            for (Map.Entry<String, String> entrada : mapa.entrySet()) {
                int periodo = Integer.parseInt(entrada.getKey());
                if (periodo > nuevoNumero || cambiaUnidad) {
                    perdidas.add(new PerdidaAvanceResponse(par.actividad().publicId, periodo, entrada.getValue()));
                }
                if (periodo <= nuevoNumero) {
                    conservado.put(entrada.getKey(), entrada.getValue());
                }
            }
            mapasConservados.put(par.actividad().id, conservado);
        }

        boolean requiereConfirmacion = !perdidas.isEmpty() || (cambiaUnidad && hayDatos);
        if (requiereConfirmacion && !request.confirmarPerdida()) {
            // Ninguna mutación ocurrió todavía: la validación precede al cambio.
            throw new CronogramaConflictoException(
                    "La reconfiguración requiere confirmación explícita antes de perder datos "
                            + "o cambiar la unidad de tiempo",
                    perdidas);
        }

        cronograma.unidadTiempo = request.unidadTiempo();
        cronograma.numeroPeriodos = (short) nuevoNumero;
        if (!perdidas.isEmpty()) {
            for (ActividadConRubro par : actividades) {
                par.actividad().avancePorPeriodo =
                        CronogramaMapper.escribirMapa(mapasConservados.get(par.actividad().id));
            }
        }
        cronogramaRepository.flush();

        List<ActividadConRubro> actualizadas = cargarActividades(cronograma.id);
        logActividadService.emitir(
                callerUsuarioId,
                EventoLogActividad.CRONOGRAMA_EDITADO,
                "cronograma",
                cronograma.publicId,
                Map.of("operacion", "configurar"));
        return respuesta(cronograma, presupuesto, actualizadas);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private Presupuesto resolverPresupuesto(UUID presupuestoPublicId, Long callerUsuarioId) {
        return presupuestoRepository
                .findByPublicIdAndOwnerScope(presupuestoPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));
    }

    private CronogramaResponse respuesta(
            Cronograma cronograma, Presupuesto presupuesto, List<ActividadConRubro> actividades) {
        return mapper.toResponse(cronograma, presupuesto, actividades, fingerprint(presupuesto.id));
    }

    private String fingerprint(Long presupuestoId) {
        return PresupuestoFingerprint.calcular(actividadRepository.listarSnapshotPresupuesto(presupuestoId).stream()
                .map(fila -> new PresupuestoFingerprint.RubroSnapshot(
                        (String) fila[0], (String) fila[1], (String) fila[2], (BigDecimal) fila[3]))
                .toList());
    }

    private List<ActividadConRubro> cargarActividades(Long cronogramaId) {
        return actividadRepository.listarConRubroPorCronograma(cronogramaId).stream()
                .map(par -> new ActividadConRubro((Actividad) par[0], (Rubro) par[1], (String) par[2]))
                .toList();
    }

    static boolean esUniqueCronogramaPresupuesto(PersistenceException error) {
        boolean sqlState23505 = false;
        boolean constraintCorrecta = false;
        for (Throwable actual = error; actual != null; actual = actual.getCause()) {
            if (actual instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                sqlState23505 = true;
            }
            String mensaje = actual.getMessage();
            if (mensaje != null && mensaje.contains("cronograma_presupuesto_id_key")) {
                constraintCorrecta = true;
            }
        }
        return sqlState23505 && constraintCorrecta;
    }

    private static BigDecimal escala6(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(6, RoundingMode.HALF_UP);
    }
}
