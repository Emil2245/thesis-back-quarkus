package ec.uce.propuestas.cronograma.export;

import ec.uce.propuestas.cronograma.dto.ProyeccionExportacion;
import ec.uce.propuestas.cronograma.entity.Actividad;
import ec.uce.propuestas.cronograma.entity.Cronograma;
import ec.uce.propuestas.cronograma.mapper.CronogramaMapper;
import ec.uce.propuestas.cronograma.repository.ActividadRepository;
import ec.uce.propuestas.cronograma.service.MonedaRacionalCalculador;
import ec.uce.propuestas.presupuesto.entity.Capitulo;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.repository.CapituloRepository;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan 031 (P-37) — construye las proyecciones inmutables que consumen los
 * writers (XLSX, PDF, MSPDI) a partir del snapshot canónico del preflight.
 *
 * <p>Decisión locked (Plan 031 §Writers): los writers NO recalculan pesos,
 * segmentos, avances ni montos. Toda la derivación numérica vive aquí
 * (delegando al {@link MonedaRacionalCalculador} del Plan 030), de modo que
 * las tres salidas comparten el mismo origen.</p>
 *
 * <p>El orden de rubros sigue el árbol recursivo
 * {@code capítulo → subcapítulo → rubro}, que es el mismo árbol del
 * {@code GanttBloqueResponse} de Plan 030.</p>
 *
 * <p>Anti-N+1 (audit closure §N+1): el batch load de rubros se hace UNA vez
 * por presupuesto, indexado por {@code Long id}; los writers iteran sobre la
 * actividad y buscan el rubro en el índice (O(1)). Cero
 * {@code rubroRepository.findById(...)} dentro del loop.</p>
 */
@ApplicationScoped
public class CronogramaProyeccionExportService {

    @Inject
    ActividadRepository actividadRepository;

    @Inject
    CapituloRepository capituloRepository;

    @Inject
    RubroRepository rubroRepository;

    /**
     * Proyección XLSX/PDF: misma estructura que la hoja del cronograma
     * valorizado. Las claves activas son exactamente las que la actividad
     * tiene con valor (incluido 0.0000); las ausentes se renderizan como
     * celda vacía.
     */
    @Transactional
    public CronogramaXlsxWriter.ProyeccionXlsx construirProyeccionHoja(
            CronogramaExportPreflightService.SnapshotCompleto snap) {
        Cronograma cronograma = snap.cronograma();
        Presupuesto presupuesto = snap.presupuesto();
        Proyecto proyecto = snap.proyecto();

        int n = cronograma.numeroPeriodos == null ? 0 : cronograma.numeroPeriodos.intValue();

        // Batch load: UNA sola consulta para todos los rubros del presupuesto,
        // indexada por Long id — cierra el N+1 detectado en el audit.
        List<Rubro> rubrosPlanos = rubroRepository.listarPorPresupuesto(presupuesto.id);
        java.util.Map<Long, Rubro> rubroPorId = new LinkedHashMap<>(rubrosPlanos.size() * 2);
        for (Rubro r : rubrosPlanos) {
            rubroPorId.put(r.id, r);
        }

        List<CronogramaXlsxWriter.FilaHoja> filas = new ArrayList<>();
        List<Actividad> actividades = actividadRepository.listarPorCronograma(cronograma.id);
        // Reutilizamos el árbol de Plan 030 — el cierre por actividad + el
        // cálculo monetario es exactamente el mismo.
        Map<Long, ActividadCerrada> actPorRubro = new LinkedHashMap<>();
        for (Actividad act : actividades) {
            Rubro rubro = rubroPorId.get(act.rubroId);
            if (rubro == null) continue;
            Map<String, String> mapa = CronogramaMapper.leerMapa(act.avancePorPeriodo);
            BigDecimal peso = act.pesoPonderado == null ? BigDecimal.ZERO : act.pesoPonderado.setScale(4);
            BigDecimal suma = BigDecimal.ZERO.setScale(4);
            for (String v : mapa.values()) {
                suma = suma.add(new BigDecimal(v)).setScale(4, RoundingMode.HALF_UP);
            }
            BigDecimal desviacion = peso.subtract(suma).setScale(4, RoundingMode.HALF_UP);
            MonedaRacionalCalculador.DistribucionMonto dinero = MonedaRacionalCalculador.distribuir(
                    rubro.precioTotal == null ? BigDecimal.ZERO : rubro.precioTotal, act.pesoPonderado, n, mapa);
            actPorRubro.put(rubro.id, new ActividadCerrada(rubro, mapa, peso, desviacion, dinero.porPeriodo()));
        }

        List<Capitulo> planos = capituloRepository.listarPorPresupuestoOrdenado(presupuesto.id);
        java.util.Map<Long, List<Rubro>> rubrosPorCapitulo = new LinkedHashMap<>();
        for (Rubro r : rubrosPlanos) {
            rubrosPorCapitulo
                    .computeIfAbsent(r.capituloId, k -> new ArrayList<>())
                    .add(r);
        }
        java.util.Map<Long, NodoCap> nodos = new LinkedHashMap<>();
        for (Capitulo c : planos) {
            nodos.put(c.id, new NodoCap(c, rubrosPorCapitulo.getOrDefault(c.id, List.of())));
        }
        List<NodoCap> raices = new ArrayList<>();
        for (Capitulo c : planos) {
            NodoCap nodo = nodos.get(c.id);
            if (c.parentId == null) {
                raices.add(nodo);
            } else {
                NodoCap padre = nodos.get(c.parentId);
                if (padre != null) padre.hijos.add(nodo);
                else raices.add(nodo);
            }
        }
        for (NodoCap r : raices) {
            r.recolectar(filas, actPorRubro, n);
        }

        return new CronogramaXlsxWriter.ProyeccionXlsx(
                proyecto.codigo == null ? "" : proyecto.codigo,
                proyecto.nombreProyecto == null ? "" : proyecto.nombreProyecto,
                proyecto.anio == null ? 0 : proyecto.anio.intValue(),
                cronograma.unidadTiempo,
                n,
                proyecto.fechaInicio,
                List.copyOf(filas));
    }

    /**
     * Variante canónica que ENVUELVE la hoja XLSX con los totales fila-a-fila
     * pre-computados ({@code ProyeccionExportacion}). Única entrada que deben
     * consumir los writers XLSX y PDF. La fórmula de Suma parcial / Suma
     * acumulado vive exclusivamente en
     * {@link ProyeccionExportacion#de(CronogramaXlsxWriter.ProyeccionXlsx)};
     * este método solo delega para no duplicar la regla canónica.
     */
    @Transactional
    public ProyeccionExportacion construirProyeccionCompleta(CronogramaExportPreflightService.SnapshotCompleto snap) {
        return ProyeccionExportacion.de(construirProyeccionHoja(snap));
    }

    /** Proyección MSPDI: misma lectura pero expone los rubros en orden presupuestario. */
    @Transactional
    public CronogramaMspdiWriter.ProyeccionMspdi construirProyeccionMspdi(
            CronogramaExportPreflightService.SnapshotCompleto snap) {
        Cronograma cronograma = snap.cronograma();
        Presupuesto presupuesto = snap.presupuesto();
        Proyecto proyecto = snap.proyecto();
        int n = cronograma.numeroPeriodos == null ? 0 : cronograma.numeroPeriodos.intValue();

        // Fail-fast (audit closure §null publicId): un proyecto sin publicId
        // poblado indica una corrupción del dominio (la columna se genera por
        // defecto V001 §2). Nunca se emite identidad aleatoria para MSPDI.
        if (proyecto.publicId == null) {
            throw new IllegalStateException("Proyecto sin publicId poblado; la identidad MSPDI debe ser determinista");
        }

        // Batch load rubros: cierra el N+1 del audit (audit closure §N+1).
        List<Rubro> rubrosPlanos = rubroRepository.listarPorPresupuesto(presupuesto.id);
        java.util.Map<Long, Rubro> rubroPorId = new LinkedHashMap<>(rubrosPlanos.size() * 2);
        for (Rubro r : rubrosPlanos) {
            rubroPorId.put(r.id, r);
        }

        List<CronogramaMspdiWriter.RubroMspdi> rubrosMspdi = new ArrayList<>();
        List<Actividad> actividades = actividadRepository.listarPorCronograma(cronograma.id);
        for (Actividad act : actividades) {
            Rubro rubro = rubroPorId.get(act.rubroId);
            if (rubro == null) continue;
            Map<String, String> mapa = CronogramaMapper.leerMapa(act.avancePorPeriodo);

            List<Integer> activos = new ArrayList<>();
            List<BigDecimal> porcentajes = new ArrayList<>();
            for (int p = 1; p <= n; p++) {
                String v = mapa.get(String.valueOf(p));
                if (v != null) {
                    activos.add(p);
                    porcentajes.add(new BigDecimal(v).setScale(4));
                }
            }
            rubrosMspdi.add(new CronogramaMspdiWriter.RubroMspdi(
                    rubro.publicId,
                    rubro.item == null ? "" : rubro.item,
                    rubro.codigo == null ? "" : rubro.codigo,
                    rubro.descripcion == null ? "" : rubro.descripcion,
                    rubro.unidad == null ? "" : rubro.unidad,
                    rubro.cantidad == null ? BigDecimal.ZERO : rubro.cantidad.setScale(6),
                    rubro.precioUnitario == null ? BigDecimal.ZERO : rubro.precioUnitario.setScale(6),
                    rubro.precioTotal == null ? BigDecimal.ZERO : rubro.precioTotal.setScale(6),
                    act.pesoPonderado == null ? BigDecimal.ZERO : act.pesoPonderado.setScale(4),
                    List.copyOf(activos),
                    List.copyOf(porcentajes)));
        }

        // Identidad determinista del proyecto: el publicId se deriva al
        // CalendarUID/UID del Project MSPDI dentro del writer (positive int
        // derivado del UUIDv7, módulo Integer.MAX_VALUE / 2 — ver
        // CronogramaMspdiWriter#uidDeterminista).
        return new CronogramaMspdiWriter.ProyeccionMspdi(
                proyecto.publicId,
                proyecto.codigo == null ? "" : proyecto.codigo,
                proyecto.nombreProyecto == null ? "" : proyecto.nombreProyecto,
                proyecto.anio == null ? 0 : proyecto.anio.intValue(),
                cronograma.unidadTiempo,
                n,
                proyecto.fechaInicio == null ? LocalDate.now() : proyecto.fechaInicio,
                List.copyOf(rubrosMspdi));
    }

    private record ActividadCerrada(
            Rubro rubro,
            Map<String, String> avancePorPeriodo,
            BigDecimal peso,
            BigDecimal desviacion,
            Map<String, String> montoPorPeriodo) {}

    /** Nodo mutable del árbol para recolección en pre-order. */
    private static final class NodoCap {
        final Capitulo cap;
        final List<Rubro> rubrosList;
        final List<NodoCap> hijos = new ArrayList<>();

        NodoCap(Capitulo c, List<Rubro> rubros) {
            this.cap = c;
            this.rubrosList = rubros == null ? List.of() : rubros;
        }

        void recolectar(List<CronogramaXlsxWriter.FilaHoja> out, Map<Long, ActividadCerrada> actPorRubro, int n) {
            // Rubros en orden estable: item + id.
            List<Rubro> orden = new ArrayList<>(rubrosList);
            orden.sort(Comparator.comparing((Rubro r) -> r.item == null ? "" : r.item)
                    .thenComparing(r -> r.id));
            for (Rubro r : orden) {
                ActividadCerrada ac = actPorRubro.get(r.id);
                if (ac == null) continue;
                List<BigDecimal> pctPeriodos = new ArrayList<>(n);
                for (int p = 1; p <= n; p++) {
                    String v = ac.avancePorPeriodo.get(String.valueOf(p));
                    pctPeriodos.add(v == null ? null : new BigDecimal(v).setScale(4));
                }
                Map<Integer, BigDecimal> monto = new LinkedHashMap<>();
                for (Map.Entry<String, String> e : ac.montoPorPeriodo.entrySet()) {
                    monto.put(Integer.parseInt(e.getKey()), new BigDecimal(e.getValue()).setScale(6));
                }
                // List.copyOf rechaza nulls; la proyección requiere permitir celdas
                // vacías (clave inactiva) en porcentajesPorPeriodo.
                List<BigDecimal> pctInmutable = java.util.Collections.unmodifiableList(new ArrayList<>(pctPeriodos));
                out.add(new CronogramaXlsxWriter.FilaHoja(
                        new CronogramaXlsxWriter.Fila(
                                r.item == null ? "" : r.item,
                                r.codigo == null ? "" : r.codigo,
                                r.descripcion == null ? "" : r.descripcion,
                                r.unidad == null ? "" : r.unidad,
                                r.cantidad == null ? BigDecimal.ZERO : r.cantidad.setScale(6),
                                r.precioUnitario == null ? BigDecimal.ZERO : r.precioUnitario.setScale(6),
                                r.precioTotal == null ? BigDecimal.ZERO : r.precioTotal.setScale(6),
                                ac.peso),
                        pctInmutable,
                        monto));
            }
            for (NodoCap h : hijos) {
                h.recolectar(out, actPorRubro, n);
            }
        }
    }
}
