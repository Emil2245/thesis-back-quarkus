package ec.uce.propuestas.recalculo.internal;

import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.entity.ApuDetalle;
import ec.uce.propuestas.apu.entity.ApuSeccion;
import ec.uce.propuestas.apu.repository.ApuDetalleRepository;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.repository.ApuSeccionRepository;
import ec.uce.propuestas.apu.service.ApuCalculoService;
import ec.uce.propuestas.common.ItemJerarquico;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.cronograma.service.AvancePatchParser;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import ec.uce.propuestas.motor.ActividadSnapshot;
import ec.uce.propuestas.motor.ApuSnapshot;
import ec.uce.propuestas.motor.CapituloSnapshot;
import ec.uce.propuestas.motor.CronogramaSnapshot;
import ec.uce.propuestas.motor.FilaSnapshot;
import ec.uce.propuestas.motor.ParametrosCalculo;
import ec.uce.propuestas.motor.RubroSnapshot;
import ec.uce.propuestas.motor.VersionSnapshot;
import ec.uce.propuestas.presupuesto.entity.Capitulo;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.repository.CapituloRepository;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import ec.uce.propuestas.proyecto.entity.ParametrosProyecto;
import ec.uce.propuestas.proyecto.service.ParametrosProyectoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Helper package-localizado (visible al paquete {@code ec.uce.propuestas.recalculo}
 * porque Java exige visibilidad pública para cruzar límites de subpaquete).
 * Construye el {@link VersionSnapshot} que consume el motor, leyendo desde la
 * base de datos el árbol completo de capítulos, rubros, APUs y sus filas.
 *
 * <p>El motor no se reabre; este helper se limita a mapear entidades JPA →
 * snapshots del motor. La frontera APU→Rubro se aplica únicamente dentro del
 * motor, en {@code internal/Consolidador}.
 *
 * <p>Regla de disciplina: este builder no es un seam adicional; los tests del
 * módulo profundo verifican la semántica vía {@link ec.uce.propuestas.recalculo.RecalculoService}
 * por su API pública, no tocando el builder directamente.
 */
@ApplicationScoped
public class VersionSnapshotBuilder {

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    CapituloRepository capituloRepository;

    @Inject
    RubroRepository rubroRepository;

    @Inject
    ApuRepository apuRepository;

    @Inject
    ApuSeccionRepository seccionRepository;

    @Inject
    ApuDetalleRepository detalleRepository;

    @Inject
    InsumoRepository insumoRepository;

    @Inject
    ParametrosProyectoService parametrosService;

    @Inject
    ec.uce.propuestas.cronograma.repository.ActividadRepository actividadRepoBuilder;

    @Inject
    ec.uce.propuestas.cronograma.repository.CronogramaRepository cronogramaRepoBuilder;

    /**
     * Construye el {@link VersionSnapshot} de la versión indicada. Carga el árbol
     * completo de capítulos (raíz → subcapítulos), los rubros de cada capítulo,
     * el APU vinculado a cada rubro y las filas de cada APU. El cronograma, si
     * existe, se mapea a {@link CronogramaSnapshot} cargando actividades
     * persistidas (mapa numérico por rubro codigo) — Plan 029.
     */
    public VersionSnapshot build(Long presupuestoId) {
        Presupuesto presupuesto = presupuestoRepository.findById(presupuestoId);
        if (presupuesto == null) {
            throw ProblemaException.noEncontrado("Presupuesto no encontrado");
        }
        ParametrosProyecto params = parametrosService.obtenerOCrear(presupuesto.proyectoId);
        ParametrosCalculo motorParams =
                new ParametrosCalculo(params.porcentajeHerramientaMenor, params.porcentajeIndirecto);

        // All tree state belongs to this build and this budget only.
        Map<Long, List<Capitulo>> hijosPorPadre = new HashMap<>();
        for (Capitulo capitulo : capituloRepository.listarPorPresupuesto(presupuestoId)) {
            hijosPorPadre
                    .computeIfAbsent(capitulo.parentId, ignored -> new ArrayList<>())
                    .add(capitulo);
        }
        for (List<Capitulo> hermanos : hijosPorPadre.values()) {
            hermanos.sort(Comparator.comparingInt(capitulo -> capitulo.orden));
        }
        List<Capitulo> capitulosRaiz = hijosPorPadre.getOrDefault(null, List.of());
        List<Long> alcanzables = new ArrayList<>();
        for (Capitulo raiz : capitulosRaiz) {
            agregarAlcanzables(raiz, hijosPorPadre, alcanzables);
        }
        Map<Long, List<Rubro>> rubrosPorCapitulo = new HashMap<>();
        for (Rubro rubro : rubroRepository.listarPorCapitulos(alcanzables)) {
            rubrosPorCapitulo
                    .computeIfAbsent(rubro.capituloId, ignored -> new ArrayList<>())
                    .add(rubro);
        }
        for (List<Rubro> rubros : rubrosPorCapitulo.values()) {
            rubros.sort(Comparator.comparing(rubro -> rubro.item, ItemJerarquico.ORDEN));
        }
        Map<Long, ApuSnapshot> apus = cargarApus(rubrosPorCapitulo, presupuesto, params);
        List<CapituloSnapshot> raices = new ArrayList<>();
        for (Capitulo raiz : capitulosRaiz) {
            raices.add(construirCapitulo(raiz, 1, hijosPorPadre, rubrosPorCapitulo, apus));
        }
        CronogramaSnapshot cronogramaSnap = cargarCronogramaSnapshot(presupuestoId);
        return new VersionSnapshot(motorParams, raices, cronogramaSnap);
    }

    /**
     * Carga el cronograma persistido (cronograma + actividades) y lo mapea al
     * {@link CronogramaSnapshot} del motor. Devuelve {@code null} cuando el
     * presupuesto aún no tiene cronograma (P-32 + I-08); en ese caso el
     * {@link ec.uce.propuestas.motor.internal.Consolidador} omite el cálculo de
     * avances por período y el presupuesto se mantiene completo por totales.
     *
     * <p>El mapa se reconstruye leyendo {@code actividad.avance_por_periodo}
     * (JSONB) y proyectándolo al formato {@code Map<Integer, BigDecimal>} con
     * valores normalizados a escala 4; el join con el rubro se hace por el
     * {@code rubroCodigo} de la fila del rubro. El mapa vacío {@code {}} se
     * traduce a un mapa vacío para que el motor determine correctamente los
     * acumulados por período (incluida la participación cero de la actividad
     * en su rubro).</p>
     */
    private CronogramaSnapshot cargarCronogramaSnapshot(Long presupuestoId) {
        var cronograma = cronogramaRepoBuilder
                .find("presupuestoId = :pid", io.quarkus.panache.common.Parameters.with("pid", presupuestoId))
                .firstResult();
        if (cronograma == null) {
            return null;
        }
        int numeroPeriodos = cronograma.numeroPeriodos == null ? 0 : cronograma.numeroPeriodos.intValue();
        var entidadesActividad = actividadRepoBuilder.listarPorCronograma(cronograma.id);
        List<ActividadSnapshot> snaps = new ArrayList<>();
        for (var actividad : entidadesActividad) {
            var rubro = rubroRepository.findById(actividad.rubroId);
            if (rubro == null) {
                continue;
            }
            String codigo = rubro.codigo == null ? "" : rubro.codigo;
            java.util.Map<Integer, BigDecimal> mapaNumerico = new java.util.LinkedHashMap<>();
            String jsonb = actividad.avancePorPeriodo == null ? "{}" : actividad.avancePorPeriodo;
            try {
                var root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonb);
                if (root == null || !root.isObject()) {
                    throw ProblemaException.validacion("actividad.avance_por_periodo debe ser un objeto JSON");
                }
                var mapaValidado = AvancePatchParser.validarYNormalizarMapa(root, numeroPeriodos);
                for (var entrada : mapaValidado.entrySet()) {
                    mapaNumerico.put(Integer.parseInt(entrada.getKey()), new BigDecimal(entrada.getValue()));
                }
            } catch (ProblemaException ex) {
                throw ex;
            } catch (Exception ex) {
                throw ProblemaException.validacion(
                        "actividad.avance_por_periodo no es JSON válido: " + ex.getMessage());
            }
            snaps.add(new ActividadSnapshot(codigo, mapaNumerico));
        }
        return new CronogramaSnapshot(numeroPeriodos, snaps);
    }

    private void agregarAlcanzables(Capitulo cap, Map<Long, List<Capitulo>> hijosPorPadre, List<Long> alcanzables) {
        // Preserve root-based reachability: cross-budget parents and disconnected cycles stay excluded.
        alcanzables.add(cap.id);
        for (Capitulo hijo : hijosPorPadre.getOrDefault(cap.id, List.of())) {
            agregarAlcanzables(hijo, hijosPorPadre, alcanzables);
        }
    }

    private CapituloSnapshot construirCapitulo(
            Capitulo cap,
            int depth,
            Map<Long, List<Capitulo>> hijosPorPadre,
            Map<Long, List<Rubro>> rubrosPorCapitulo,
            Map<Long, ApuSnapshot> apus) {
        List<CapituloSnapshot> subs = new ArrayList<>();
        for (Capitulo hijo : hijosPorPadre.getOrDefault(cap.id, List.of())) {
            subs.add(construirCapitulo(hijo, depth + 1, hijosPorPadre, rubrosPorCapitulo, apus));
        }
        List<RubroSnapshot> rubros = new ArrayList<>();
        for (Rubro rubro : rubrosPorCapitulo.getOrDefault(cap.id, List.of())) {
            rubros.add(construirRubro(rubro, apus));
        }
        return new CapituloSnapshot(cap.item, cap.descripcion, depth, subs, rubros);
    }

    private RubroSnapshot construirRubro(Rubro r, Map<Long, ApuSnapshot> apus) {
        ApuSnapshot apu = apus.get(r.apuId);
        if (apu == null) {
            // D-09 garantiza 1:1 rubro↔apu; si falta, snapshot consistente con
            // la realidad — registramos un APU vacío para que Motor.calcularApu
            // no lance NPE y el total del rubro se quede en 0.
            ApuSnapshot vacio = new ApuSnapshot(r.codigo, null, List.of());
            return new RubroSnapshot(claveRubro(r.id), r.cantidad, vacio);
        }
        return new RubroSnapshot(claveRubro(r.id), r.cantidad, apu);
    }

    /**
     * Clave única del rubro en el snapshot (plan 033). {@code rubro.codigo} no
     * es único dentro de una versión (varios rubros pueden compartir código
     * con APU distintos), así que el campo {@code codigo} de
     * {@link RubroSnapshot} lleva el id interno; el consumidor
     * ({@code RecalculoService}) mapea los resultados con la misma clave.
     */
    public static String claveRubro(Long rubroId) {
        return "r" + rubroId;
    }

    private Map<Long, ApuSnapshot> cargarApus(
            Map<Long, List<Rubro>> rubrosPorCapitulo, Presupuesto presupuesto, ParametrosProyecto params) {
        // Hydrate only the graph referenced by reachable rubros, never the whole project.
        var ids = new HashSet<Long>();
        for (List<Rubro> rubros : rubrosPorCapitulo.values()) {
            for (Rubro rubro : rubros) {
                ids.add(rubro.apuId);
            }
        }
        List<Apu> apus = apuRepository.listarPorIds(ids);
        ids.clear();
        for (Apu apu : apus) {
            ids.add(apu.id);
        }
        Map<Long, List<ApuSeccion>> seccionesPorApu = new HashMap<>();
        List<ApuSeccion> secciones = seccionRepository.listarPorApus(ids);
        ids.clear();
        for (ApuSeccion seccion : secciones) {
            ids.add(seccion.id);
            seccionesPorApu
                    .computeIfAbsent(seccion.apuId, ignored -> new ArrayList<>())
                    .add(seccion);
        }
        Map<Long, List<ApuDetalle>> detallesPorSeccion = new HashMap<>();
        List<ApuDetalle> detalles = detalleRepository.listarPorSecciones(ids);
        ids.clear();
        for (ApuDetalle detalle : detalles) {
            detallesPorSeccion
                    .computeIfAbsent(detalle.seccionId, ignored -> new ArrayList<>())
                    .add(detalle);
            if (detalle.insumoId != null) {
                ids.add(detalle.insumoId);
            }
        }
        Map<Long, Insumo> insumos = new HashMap<>();
        for (Insumo insumo : insumoRepository.listarPorIds(ids)) {
            insumos.put(insumo.id, insumo);
        }
        // Foreign-budget references retain their own project's HM and creation semantics.
        Map<Long, Long> proyectosPorPresupuesto = new HashMap<>();
        proyectosPorPresupuesto.put(presupuesto.id, presupuesto.proyectoId);
        Map<Long, ParametrosProyecto> parametrosPorProyecto = new HashMap<>();
        parametrosPorProyecto.put(presupuesto.proyectoId, params);
        Map<Long, ApuSnapshot> snapshots = new HashMap<>();
        for (Apu apu : apus) {
            Long proyectoId = proyectosPorPresupuesto.computeIfAbsent(
                    apu.presupuestoId,
                    id -> apuRepository
                            .proyectoDePresupuesto(id)
                            .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado")));
            ParametrosProyecto propios =
                    parametrosPorProyecto.computeIfAbsent(proyectoId, parametrosService::obtenerOCrear);
            List<ApuSeccion> propias = seccionesPorApu.getOrDefault(apu.id, new ArrayList<>());
            propias.sort(Comparator.comparingInt(s -> s.tipo.ordinal()));
            List<FilaSnapshot> filas = new ArrayList<>();
            for (ApuSeccion seccion : propias) {
                for (ApuDetalle detalle : detallesPorSeccion.getOrDefault(seccion.id, List.of())) {
                    filas.add(ApuCalculoService.snapshotDeDetalle(
                            detalle, seccion.tipo, insumos.get(detalle.insumoId), propios.porcentajeHerramientaMenor));
                }
            }
            snapshots.put(apu.id, new ApuSnapshot(apu.codigo, apu.porcentajeIndirecto, filas));
        }
        return snapshots;
    }
}
