package ec.uce.propuestas.cronograma.mapper;

import ec.uce.propuestas.cronograma.dto.ActividadResponse;
import ec.uce.propuestas.cronograma.dto.CronogramaResponse;
import ec.uce.propuestas.cronograma.entity.Actividad;
import ec.uce.propuestas.cronograma.entity.Cronograma;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class CronogramaMapper {

    private CronogramaMapper() {}

    public static CronogramaResponse toResponse(
            Cronograma c, BigDecimal totalGeneral, List<Actividad> actividades, Map<Long, Rubro> rubrosById) {
        boolean desactualizado = c.totalGeneralRevisado != null && totalGeneral.compareTo(c.totalGeneralRevisado) != 0;
        List<ActividadResponse> acts = actividades.stream()
                .map(a -> toActividadResponse(a, rubrosById.get(a.rubroId)))
                .toList();
        return new CronogramaResponse(
                c.id,
                c.presupuestoId,
                c.unidadTiempo.name(),
                c.numeroPeriodos,
                totalGeneral,
                c.totalGeneralRevisado,
                c.fechaRevision,
                desactualizado,
                acts);
    }

    private static ActividadResponse toActividadResponse(Actividad a, Rubro r) {
        return new ActividadResponse(
                a.id,
                a.rubroId,
                r != null ? r.item : null,
                r != null ? r.descripcion : null,
                r != null ? r.precioTotal : BigDecimal.ZERO,
                a.pesoPonderado,
                a.avancePorPeriodo);
    }
}
