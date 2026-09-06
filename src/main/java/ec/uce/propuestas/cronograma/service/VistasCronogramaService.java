package ec.uce.propuestas.cronograma.service;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.cronograma.dto.ActividadCronogramaResponse;
import ec.uce.propuestas.cronograma.dto.CapituloCronogramaResponse;
import ec.uce.propuestas.cronograma.dto.CronogramaResponse;
import ec.uce.propuestas.cronograma.dto.CronogramaVistasResponse;
import ec.uce.propuestas.cronograma.dto.CurvaSResponse;
import ec.uce.propuestas.cronograma.dto.GanttBloqueResponse;
import ec.uce.propuestas.cronograma.dto.PeriodoValorizadoResponse;
import ec.uce.propuestas.cronograma.dto.PuntoCurvaSResponse;
import ec.uce.propuestas.cronograma.dto.RubroCronogramaResponse;
import ec.uce.propuestas.cronograma.dto.SegmentoResponse;
import ec.uce.propuestas.cronograma.dto.TotalesCronogramaResponse;
import ec.uce.propuestas.cronograma.dto.ValorizadoBloqueResponse;
import ec.uce.propuestas.cronograma.entity.Actividad;
import ec.uce.propuestas.cronograma.entity.Cronograma;
import ec.uce.propuestas.cronograma.mapper.CronogramaMapper;
import ec.uce.propuestas.cronograma.mapper.CronogramaMapper.ActividadConRubro;
import ec.uce.propuestas.cronograma.repository.ActividadRepository;
import ec.uce.propuestas.cronograma.repository.CronogramaRepository;
import ec.uce.propuestas.presupuesto.entity.Capitulo;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.repository.CapituloRepository;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plan 030 (P-35/P-36) — orquestación de las tres vistas de cronograma y de la
 * acción idempotente de revisión.
 *
 * <p>Decisiones locked:
 * <ul>
 *   <li><b>Forma canónica.</b> La raíz sólo expone {@code cronogramaId}; los
 *     bloques viven en {@code gantt}, {@code valorizado} y {@code curvaS}. El
 *     bloque {@code gantt} lleva el {@code CronogramaResponse} canónico
 *     (idéntico a {@code GET /presupuestos/{id}/cronograma}) más la jerarquía
 *     recursiva capítulo→rubro→actividad. Los capítulos son nodos
 *     estructurales — NUNCA adjuntan la primera actividad del primer rubro
 *     (TC-P35-01).</li>
 *   <li><b>Una sola proyección.</b>
 *     {@link #obtenerVistas(UUID, Long)} carga cronograma, capítulos
 *     ordenados, rubros del presupuesto y actividades con su rubro en
 *     consultas acotadas (sin N+1), reconstruye el árbol en memoria y deriva
 *     los tres bloques (gantt / valorizado / curvaS) sobre esa misma fuente.
 *     Ningún derivado se persiste.</li>
 *   <li><b>Identidad pública.</b> Los {@code BIGINT} internos nunca aparecen
 *     en los DTOs: sólo UUIDv7.</li>
 *   <li><b>Marcadores stale.</b> Compara {@code totalGeneral} con
 *     {@code totalGeneralRevisado} y el fingerprint canónico actual contra
 *     {@code presupuestoFingerprintRevisado}. Detecta cambios compensados
 *     con total igual (Plan 026 §4).</li>
 *   <li><b>Revisado idempotente.</b> {@link #marcarRevisado(UUID, Long)}
 *     adquiere el lock pesimista del presupuesto, refresca la fila, captura
 *     el snapshot canónico (total + fingerprint + fecha) y devuelve el
 *     {@code CronogramaResponse} fresco. Si total+fingerprint coinciden con
 *     los marcadores ya capturados, NO muta {@code fechaRevision} (segunda
 *     llamada es no-op); una repetición devuelve la misma respuesta.</li>
 *   <li><b>Borrador / completo.</b> {@code estadoDistribucion} es
 *     {@code BORRADOR} cuando alguna actividad tiene desviación distinta de
 *     {@code 0.0000} o el avance final es distinto de {@code 100.0000}; si
 *     no, {@code COMPLETO}. La alerta de desactualización no bloquea la
 *     exportación (Plan 026 §4 — ejes ortogonales).</li>
 *   <li><b>Total cero / presupuesto vacío.</b> Devuelve respuesta con totales
 *     en 0, estado {@code BORRADOR} y bloques vacíos; nunca divide por
 *     cero.</li>
 * </ul>
 *
 * <p>El método de revisión es el ÚNICO comando que escribe los marcadores
 * {@code totalGeneralRevisado}, {@code presupuestoFingerprintRevisado} y
 * {@code fechaRevision} en {@code cronograma}; las otras rutas (alta,
 * configuración, programación de actividades) los conservan intactos.</p>
 */
@ApplicationScoped
public class VistasCronogramaService {

    @Inject
    CronogramaRepository cronogramaRepository;

    @Inject
    ActividadRepository actividadRepository;

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    CapituloRepository capituloRepository;

    @Inject
    RubroRepository rubroRepository;

    @Inject
    CronogramaMapper cronogramaMapper;

    // ──────────────────────────────────────────────────────────────────────
    // GET /cronogramas/{id}/vistas
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Lectura owner-scoped de la respuesta canónica de tres bloques. La ruta
     * HTTP valida previamente que el UUID sea UUIDv7; aquí se resuelve owner
     * (404 si ajeno o inexistente) y se construye la proyección sin escribir.
     */
    public CronogramaVistasResponse obtenerVistas(UUID cronogramaPublicId, Long callerUsuarioId) {
        Cronograma cronograma = cronogramaRepository
                .findByPublicIdAndOwnerScope(cronogramaPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Cronograma no encontrado"));
        Presupuesto presupuesto = presupuestoRepository.findById(cronograma.presupuestoId);
        if (presupuesto == null) {
            throw ProblemaException.noEncontrado("Presupuesto no encontrado");
        }
        return construirVistas(cronograma, presupuesto);
    }

    // ──────────────────────────────────────────────────────────────────────
    // POST /cronogramas/{id}/revisado
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Marca el cronograma como revisado. Sección crítica: lock pesimista del
     * presupuesto → refresh → captura snapshot canónico (total, fingerprint,
     * fecha) → flush → {@link CronogramaResponse} fresco.
     *
     * <p>Idempotencia semántica: si {@code totalGeneral} y el fingerprint ya
     * coinciden con los marcadores capturados, NO se modifica
     * {@code fechaRevision} y NO se flushea — la respuesta devuelta es
     * exactamente la misma que la última llamada exitosa (Plan 026 §4).</p>
     */
    @Transactional
    public CronogramaResponse marcarRevisado(UUID cronogramaPublicId, Long callerUsuarioId) {
        Cronograma cronograma = cronogramaRepository
                .findByPublicIdAndOwnerScope(cronogramaPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Cronograma no encontrado"));

        // Lock pesimista del presupuesto: serializa con PATCH/PUT/revisado y
        // con cualquier mutación presupuestaria (V009, Plan 026 §4).
        presupuestoRepository.lockPresupuestoRow(cronograma.presupuestoId);
        cronogramaRepository.getEntityManager().refresh(cronograma);
        Presupuesto presupuesto = presupuestoRepository.findById(cronograma.presupuestoId);
        presupuestoRepository.getEntityManager().refresh(presupuesto);

        BigDecimal totalActual = escala6(presupuesto.total);
        String fingerprintActual = fingerprint(presupuesto.id);

        boolean yaRevisado = cronograma.presupuestoFingerprintRevisado != null
                && cronograma.totalGeneralRevisado != null
                && escala6(cronograma.totalGeneralRevisado).compareTo(totalActual) == 0
                && cronograma.presupuestoFingerprintRevisado.trim().equals(fingerprintActual);

        if (!yaRevisado) {
            cronograma.totalGeneralRevisado = totalActual;
            cronograma.presupuestoFingerprintRevisado = fingerprintActual;
            cronograma.fechaRevision = Instant.now();
            cronogramaRepository.flush();
        }

        return respuesta(cronograma, presupuesto);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Proyección común
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Construye la respuesta canónica de vistas sobre una sola proyección en
     * memoria. Las consultas abiertas son exactamente cuatro (cronograma +
     * capítulos ordenados + rubros del presupuesto + actividades con rubro);
     * ninguna iteración posterior dispara nuevas queries.
     */
    private CronogramaVistasResponse construirVistas(Cronograma cronograma, Presupuesto presupuesto) {
        int numeroPeriodos = cronograma.numeroPeriodos == null ? 0 : cronograma.numeroPeriodos.intValue();
        BigDecimal totalGeneral = escala6(presupuesto.total);

        // (1) Carga de los datos primarios.
        List<Capitulo> capitulosPlanos = capituloRepository.listarPorPresupuestoOrdenado(presupuesto.id);
        List<Rubro> rubrosPlanos = rubroRepository.listarPorPresupuesto(presupuesto.id);
        rubrosPlanos.sort(
                Comparator.comparing((Rubro r) -> r.item == null ? "" : r.item).thenComparing(r -> r.id));
        List<ActividadConRubro> actividades = cargarActividades(cronograma.id);
        String fingerprintActual = fingerprint(presupuesto.id);

        // (2) Cierre numérico por actividad: monto por período + acumulado.
        List<ActividadCerrada> actividadesCerradas = new ArrayList<>(actividades.size());
        for (ActividadConRubro par : actividades) {
            actividadesCerradas.add(cerrarActividad(par, numeroPeriodos));
        }

        // (3) Índices para componer la jerarquía sin más consultas.
        Map<Long, List<Rubro>> rubrosPorCapitulo = new LinkedHashMap<>();
        for (Rubro r : rubrosPlanos) {
            rubrosPorCapitulo
                    .computeIfAbsent(r.capituloId, k -> new ArrayList<>())
                    .add(r);
        }
        Map<Long, ActividadCerrada> actPorRubro = new LinkedHashMap<>();
        for (ActividadCerrada ac : actividadesCerradas) {
            actPorRubro.put(ac.rubroIdInterno, ac);
        }

        // (4) Totales cronograma: avancePorPeriodo, acumulado, montoPorPeriodo,
        //     montoAcumulado. Σ de actividad avanza a actividad, completos.
        List<BigDecimal> parcialPorcentaje = new ArrayList<>(numeroPeriodos);
        List<BigDecimal> parcialMonto = new ArrayList<>(numeroPeriodos);
        for (int i = 0; i < numeroPeriodos; i++) {
            parcialPorcentaje.add(BigDecimal.ZERO.setScale(4));
            parcialMonto.add(BigDecimal.ZERO.setScale(6));
        }
        BigDecimal avanceFinal = BigDecimal.ZERO.setScale(4);
        boolean completo = !actividadesCerradas.isEmpty();
        for (ActividadCerrada ac : actividadesCerradas) {
            BigDecimal sumaAvance = BigDecimal.ZERO.setScale(4);
            for (Map.Entry<String, String> e : ac.avancePorPeriodo.entrySet()) {
                int p = Integer.parseInt(e.getKey());
                BigDecimal v = new BigDecimal(e.getValue());
                if (p >= 1 && p <= numeroPeriodos) {
                    parcialPorcentaje.set(
                            p - 1, parcialPorcentaje.get(p - 1).add(v).setScale(4, RoundingMode.HALF_UP));
                }
                sumaAvance = sumaAvance.add(v).setScale(4, RoundingMode.HALF_UP);
            }
            avanceFinal = avanceFinal.add(sumaAvance).setScale(4, RoundingMode.HALF_UP);
            boolean precioPositivo = ac.precioTotal.compareTo(BigDecimal.ZERO) > 0;
            boolean desviada = sumaAvance.compareTo(ac.peso) != 0;
            boolean sinClaveConPrecio = precioPositivo && ac.avancePorPeriodo.isEmpty();
            if (desviada || sinClaveConPrecio) {
                completo = false;
            }
            for (Map.Entry<String, String> e : ac.montoPorPeriodo.entrySet()) {
                int p = Integer.parseInt(e.getKey());
                BigDecimal v = new BigDecimal(e.getValue());
                if (p >= 1 && p <= numeroPeriodos) {
                    parcialMonto.set(p - 1, parcialMonto.get(p - 1).add(v).setScale(6, RoundingMode.HALF_UP));
                }
            }
        }
        if (avanceFinal.compareTo(new BigDecimal("100.0000")) != 0) {
            completo = false;
        }

        List<BigDecimal> acumuladoPorcentaje = new ArrayList<>(numeroPeriodos);
        BigDecimal ac = BigDecimal.ZERO.setScale(4);
        for (BigDecimal p : parcialPorcentaje) {
            ac = ac.add(p).setScale(4, RoundingMode.HALF_UP);
            acumuladoPorcentaje.add(ac);
        }
        List<BigDecimal> acumuladoMonto = new ArrayList<>(numeroPeriodos);
        BigDecimal acM = BigDecimal.ZERO.setScale(6);
        for (BigDecimal m : parcialMonto) {
            acM = acM.add(m).setScale(6, RoundingMode.HALF_UP);
            acumuladoMonto.add(acM);
        }
        BigDecimal montoTotalGeneral =
                escala6(acumuladoMonto.isEmpty() ? BigDecimal.ZERO : acumuladoMonto.get(acumuladoMonto.size() - 1));

        // (5) Construcción de la jerarquía recursiva (misma referencia para
        //     gantt.capitulos y valorizado.capitulos — son inmutables).
        List<CapituloCronogramaResponse> jerarquia =
                construirJerarquia(capitulosPlanos, rubrosPorCapitulo, actPorRubro);

        // (6) CronogramaResponse canónico del bloque gantt: usa el mapper
        //     existente con el fingerprint actual para que coincida con el GET
        //     presupuesto/{id}/cronograma. El mapper también produce los
        //     marcadores stale (totalGeneralRevisado, desactualizado,
        //     presupuestoFingerprintRevisado) de forma coherente.
        CronogramaResponse cronogramaCompleto =
                cronogramaMapper.toResponse(cronograma, presupuesto, actividades, fingerprintActual);

        // (7) Bloques diferenciados sobre la misma proyección.
        GanttBloqueResponse gantt = new GanttBloqueResponse(cronogramaCompleto, List.copyOf(jerarquia));
        ValorizadoBloqueResponse valorizado = construirValorizado(
                jerarquia,
                parcialPorcentaje,
                acumuladoPorcentaje,
                parcialMonto,
                acumuladoMonto,
                montoTotalGeneral,
                avanceFinal);
        CurvaSResponse curvaS = construirCurvaS(parcialPorcentaje, acumuladoPorcentaje, parcialMonto, acumuladoMonto);

        return new CronogramaVistasResponse(cronograma.publicId, gantt, valorizado, curvaS);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers de respuesta de CronogramaResponse (usado por /revisado)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Devuelve el {@link CronogramaResponse} canónico del cronograma + su
     * presupuesto. Lo usa {@link #marcarRevisado(UUID, Long)} para responder
     * a {@code POST /cronogramas/{id}/revisado} (Plan 026 §3 — el contrato
     * canónico exige {@code CronogramaResponse}, no la respuesta de vistas).
     */
    private CronogramaResponse respuesta(Cronograma cronograma, Presupuesto presupuesto) {
        List<ActividadConRubro> actividades = cargarActividades(cronograma.id);
        return cronogramaMapper.toResponse(cronograma, presupuesto, actividades, fingerprint(presupuesto.id));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Cierre por actividad
    // ──────────────────────────────────────────────────────────────────────

    private ActividadCerrada cerrarActividad(ActividadConRubro par, int numeroPeriodos) {
        Actividad actividad = par.actividad();
        Rubro rubro = par.rubro();
        Map<String, String> mapa = CronogramaMapper.leerMapa(actividad.avancePorPeriodo);
        BigDecimal peso = escala4(actividad.pesoPonderado);
        BigDecimal sumaAvance = BigDecimal.ZERO.setScale(4);
        for (String v : mapa.values()) {
            sumaAvance = sumaAvance.add(new BigDecimal(v)).setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal desviacion = peso.subtract(sumaAvance).setScale(4, RoundingMode.HALF_UP);
        List<SegmentoResponse> segmentos = calcularSegmentos(mapa);

        MonedaRacionalCalculador.DistribucionMonto dinero = MonedaRacionalCalculador.distribuir(
                rubro.precioTotal == null ? BigDecimal.ZERO : rubro.precioTotal,
                actividad.pesoPonderado == null ? BigDecimal.ZERO : actividad.pesoPonderado,
                numeroPeriodos,
                mapa);

        // Monto acumulado del rubro por período activo (Σ 1..t sobre claves activas).
        Map<String, String> montoAcumulado = new LinkedHashMap<>();
        BigDecimal corrido = BigDecimal.ZERO.setScale(6);
        for (Map.Entry<String, String> e : dinero.porPeriodo().entrySet()) {
            corrido = corrido.add(new BigDecimal(e.getValue())).setScale(6, RoundingMode.HALF_UP);
            montoAcumulado.put(e.getKey(), corrido.toPlainString());
        }

        return new ActividadCerrada(
                actividad.publicId,
                rubro.id,
                rubro.publicId,
                par.capituloItem(),
                rubro.item,
                rubro.codigo,
                escala6(rubro.precioTotal),
                peso,
                mapa,
                segmentos,
                desviacion,
                dinero.porPeriodo(),
                montoAcumulado,
                dinero.total());
    }

    private List<CapituloCronogramaResponse> construirJerarquia(
            List<Capitulo> capitulosPlanos,
            Map<Long, List<Rubro>> rubrosPorCapitulo,
            Map<Long, ActividadCerrada> actPorRubro) {
        Map<Long, NodoCapitulo> nodos = new LinkedHashMap<>();
        List<NodoCapitulo> raices = new ArrayList<>();
        for (Capitulo c : capitulosPlanos) {
            nodos.put(c.id, new NodoCapitulo(c, rubrosPorCapitulo.getOrDefault(c.id, List.of())));
        }
        for (Capitulo c : capitulosPlanos) {
            NodoCapitulo n = nodos.get(c.id);
            if (c.parentId == null) {
                raices.add(n);
            } else {
                NodoCapitulo padre = nodos.get(c.parentId);
                if (padre != null) {
                    padre.hijos.add(n);
                } else {
                    raices.add(n); // huérfano (no debería ocurrir) → raíz defensiva
                }
            }
        }
        return raices.stream().map(n -> n.toDto(actPorRubro)).toList();
    }

    private ValorizadoBloqueResponse construirValorizado(
            List<CapituloCronogramaResponse> jerarquia,
            List<BigDecimal> parcialPorcentaje,
            List<BigDecimal> acumuladoPorcentaje,
            List<BigDecimal> parcialMonto,
            List<BigDecimal> acumuladoMonto,
            BigDecimal montoTotalGeneral,
            BigDecimal avanceFinalPorcentaje) {
        List<PeriodoValorizadoResponse> periodos = new ArrayList<>(parcialPorcentaje.size());
        for (int i = 0; i < parcialPorcentaje.size(); i++) {
            periodos.add(new PeriodoValorizadoResponse(
                    i + 1,
                    parcialPorcentaje.get(i).toPlainString(),
                    acumuladoPorcentaje.get(i).toPlainString(),
                    parcialMonto.get(i).toPlainString(),
                    acumuladoMonto.get(i).toPlainString()));
        }
        TotalesCronogramaResponse totales = new TotalesCronogramaResponse(
                avanceFinalPorcentaje.setScale(4, RoundingMode.HALF_UP).toPlainString(),
                montoTotalGeneral.setScale(6, RoundingMode.HALF_UP).toPlainString(),
                acumuladoPorcentaje.isEmpty()
                        ? "0.0000"
                        : acumuladoPorcentaje
                                .get(acumuladoPorcentaje.size() - 1)
                                .setScale(4, RoundingMode.HALF_UP)
                                .toPlainString());
        return new ValorizadoBloqueResponse(List.copyOf(periodos), List.copyOf(jerarquia), totales);
    }

    private CurvaSResponse construirCurvaS(
            List<BigDecimal> parcialPorcentaje,
            List<BigDecimal> acumuladoPorcentaje,
            List<BigDecimal> parcialMonto,
            List<BigDecimal> acumuladoMonto) {
        List<PuntoCurvaSResponse> puntos = new ArrayList<>();
        for (int i = 0; i < parcialPorcentaje.size(); i++) {
            puntos.add(new PuntoCurvaSResponse(
                    i + 1,
                    parcialPorcentaje.get(i).toPlainString(),
                    acumuladoPorcentaje.get(i).toPlainString(),
                    parcialMonto.get(i).toPlainString(),
                    acumuladoMonto.get(i).toPlainString()));
        }
        return new CurvaSResponse(List.copyOf(puntos));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private List<ActividadConRubro> cargarActividades(Long cronogramaId) {
        return actividadRepository.listarConRubroPorCronograma(cronogramaId).stream()
                .map(par -> new ActividadConRubro((Actividad) par[0], (Rubro) par[1], (String) par[2]))
                .toList();
    }

    private String fingerprint(Long presupuestoId) {
        return PresupuestoFingerprint.calcular(actividadRepository.listarSnapshotPresupuesto(presupuestoId).stream()
                .map(fila -> new PresupuestoFingerprint.RubroSnapshot(
                        (String) fila[0], (String) fila[1], (String) fila[2], (BigDecimal) fila[3]))
                .toList());
    }

    private static BigDecimal escala4(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal escala6(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * Runs máximos de períodos activos consecutivos derivados del mapa de
     * avance. Réplica local del algoritmo privado de {@code CronogramaMapper}
     * para no ensanchar el seam del módulo (las vistas son un servicio nuevo,
     * no una mutación del mapper).
     */
    private static List<SegmentoResponse> calcularSegmentos(Map<String, String> mapa) {
        if (mapa == null || mapa.isEmpty()) {
            return List.of();
        }
        List<Integer> periodos = new ArrayList<>();
        for (String k : mapa.keySet()) {
            periodos.add(Integer.parseInt(k));
        }
        periodos.sort(Integer::compareTo);
        List<SegmentoResponse> resultado = new ArrayList<>();
        int inicio = periodos.get(0);
        int fin = inicio;
        for (int i = 1; i < periodos.size(); i++) {
            int actual = periodos.get(i);
            if (actual == fin + 1) {
                fin = actual;
            } else {
                resultado.add(new SegmentoResponse(inicio, fin));
                inicio = actual;
                fin = actual;
            }
        }
        resultado.add(new SegmentoResponse(inicio, fin));
        return List.copyOf(resultado);
    }

    /** Estado de una actividad cerrada: rubro, pesos, avances, montos. */
    private record ActividadCerrada(
            UUID actividadId,
            Long rubroIdInterno,
            UUID rubroId,
            String capituloItem,
            String rubroItem,
            String rubroCodigo,
            BigDecimal precioTotal,
            BigDecimal peso,
            Map<String, String> avancePorPeriodo,
            List<SegmentoResponse> segmentos,
            BigDecimal desviacion,
            Map<String, String> montoPorPeriodo,
            Map<String, String> montoAcumuladoPorPeriodo,
            BigDecimal montoTotal) {}

    /** Nodo mutable del árbol de capítulos para reconstruir recursivamente. */
    private static final class NodoCapitulo {

        final Capitulo cap;
        final List<Rubro> rubrosList;
        final List<NodoCapitulo> hijos = new ArrayList<>();

        NodoCapitulo(Capitulo c, List<Rubro> rubros) {
            this.cap = c;
            this.rubrosList = rubros == null ? List.of() : rubros;
        }

        CapituloCronogramaResponse toDto(Map<Long, ActividadCerrada> actPorRubro) {
            // Los rubros se ordenan por item + id (BIGINT) — orden estable.
            List<Rubro> rubrosOrdenados = new ArrayList<>(rubrosList);
            rubrosOrdenados.sort(Comparator.comparing((Rubro r) -> r.item == null ? "" : r.item)
                    .thenComparing(r -> r.id));

            List<RubroCronogramaResponse> rubroDtos = new ArrayList<>();
            for (Rubro r : rubrosOrdenados) {
                ActividadCerrada ac = actPorRubro.get(r.id);
                ActividadCronogramaResponse actividadDto = ac == null
                        ? null
                        : new ActividadCronogramaResponse(
                                ac.actividadId,
                                ac.rubroId,
                                r.item,
                                r.codigo,
                                r.descripcion,
                                r.unidad,
                                escala6(r.cantidad).toPlainString(),
                                escala6(r.precioUnitario).toPlainString(),
                                escala6(r.precioTotal).toPlainString(),
                                ac.peso.toPlainString(),
                                ac.avancePorPeriodo,
                                ac.segmentos,
                                ac.desviacion.toPlainString());
                rubroDtos.add(new RubroCronogramaResponse(
                        r.publicId,
                        r.item,
                        r.codigo,
                        r.descripcion,
                        r.unidad,
                        escala6(r.cantidad).toPlainString(),
                        escala6(r.precioUnitario).toPlainString(),
                        escala6(r.precioTotal).toPlainString(),
                        ac == null ? null : ac.montoPorPeriodo,
                        ac == null
                                ? null
                                : ac.montoTotal
                                        .setScale(6, RoundingMode.HALF_UP)
                                        .toPlainString(),
                        actividadDto));
            }
            List<CapituloCronogramaResponse> hijosDto = new ArrayList<>();
            for (NodoCapitulo h : hijos) {
                hijosDto.add(h.toDto(actPorRubro));
            }
            return new CapituloCronogramaResponse(
                    cap.publicId, cap.item, cap.descripcion, List.copyOf(hijosDto), List.copyOf(rubroDtos));
        }
    }
}
