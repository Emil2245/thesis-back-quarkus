package ec.uce.propuestas.cronograma.mapper;

import ec.uce.propuestas.cronograma.dto.ActividadResponse;
import ec.uce.propuestas.cronograma.dto.CronogramaResponse;
import ec.uce.propuestas.cronograma.entity.Actividad;
import ec.uce.propuestas.cronograma.entity.Cronograma;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import java.math.BigDecimal;
import java.util.*;

public final class CronogramaMapper {

    private CronogramaMapper() {}

    public static CronogramaResponse toResponse(
            Cronograma c, BigDecimal totalGeneral, List<Actividad> actividades, Map<Long, Rubro> rubrosById) {
        boolean desactualizado = c.totalGeneralRevisado != null && totalGeneral.compareTo(c.totalGeneralRevisado) != 0;
        List<ActividadResponse> acts = actividades.stream()
                .map(a -> toActividadResponse(a, rubrosById.get(a.rubroId)))
                .toList();

        Map<String, BigDecimal> aggAvance = new TreeMap<>();
        for (int p = 1; p <= c.numeroPeriodos; p++) {
            String key = String.valueOf(p);
            BigDecimal sum = BigDecimal.ZERO;
            for (Actividad a : actividades) {
                BigDecimal v = a.avancePorPeriodo.get(key);
                if (v != null) sum = sum.add(v);
            }
            aggAvance.put(key, sum);
        }

        Map<String, BigDecimal> aggAcumulado = new TreeMap<>();
        BigDecimal running = BigDecimal.ZERO;
        for (int p = 1; p <= c.numeroPeriodos; p++) {
            String key = String.valueOf(p);
            running = running.add(aggAvance.getOrDefault(key, BigDecimal.ZERO));
            aggAcumulado.put(key, running);
        }

        return new CronogramaResponse(
                c.id,
                c.presupuestoId,
                c.unidadTiempo.name(),
                c.numeroPeriodos,
                totalGeneral,
                c.totalGeneralRevisado,
                c.fechaRevision,
                desactualizado,
                acts,
                aggAvance,
                aggAcumulado);
    }

    private static ActividadResponse toActividadResponse(Actividad a, Rubro r) {
        BigDecimal sumaAvance = a.avancePorPeriodo.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal desviacion = sumaAvance.subtract(a.pesoPonderado);
        return new ActividadResponse(
                a.id,
                a.rubroId,
                r != null ? r.item : null,
                r != null ? r.descripcion : null,
                r != null ? r.precioTotal : BigDecimal.ZERO,
                a.pesoPonderado,
                a.avancePorPeriodo,
                desviacion);
    }
}
