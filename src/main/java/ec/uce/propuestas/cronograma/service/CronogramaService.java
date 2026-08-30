package ec.uce.propuestas.cronograma.service;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.cronograma.dto.*;
import ec.uce.propuestas.cronograma.entity.Actividad;
import ec.uce.propuestas.cronograma.entity.Cronograma;
import ec.uce.propuestas.cronograma.entity.UnidadTiempo;
import ec.uce.propuestas.cronograma.mapper.CronogramaMapper;
import ec.uce.propuestas.cronograma.repository.ActividadRepository;
import ec.uce.propuestas.cronograma.repository.CronogramaRepository;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import ec.uce.propuestas.presupuesto.service.PresupuestoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class CronogramaService {

    @Inject
    CronogramaRepository cronogramaRepository;

    @Inject
    ActividadRepository actividadRepository;

    @Inject
    PresupuestoService presupuestoService;

    @Inject
    RubroRepository rubroRepository;

    public CronogramaResponse obtener(Long presupuestoId) {
        Presupuesto p = presupuestoService.validar(presupuestoId);
        Cronograma c = cronogramaRepository
                .findByPresupuesto(presupuestoId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Cronograma no encontrado"));
        return buildResponse(c, p);
    }

    public CronogramaResponse obtenerPorId(Long cronogramaId) {
        Cronograma c = cronogramaRepository
                .findByIdOptional(cronogramaId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Cronograma no encontrado"));
        Presupuesto p = presupuestoService.validar(c.presupuestoId);
        return buildResponse(c, p);
    }

    @Transactional
    public CronogramaResponse crear(Long presupuestoId, CronogramaCrearRequest req) {
        Presupuesto p = presupuestoService.validar(presupuestoId);

        if (cronogramaRepository.findByPresupuesto(presupuestoId).isPresent()) {
            throw ProblemaException.cronogramaYaExiste("Este presupuesto ya tiene un cronograma configurado");
        }

        UnidadTiempo ut;
        try {
            ut = UnidadTiempo.valueOf(req.unidadTiempo());
        } catch (IllegalArgumentException e) {
            throw ProblemaException.validacion("unidadTiempo debe ser SEMANA o MES");
        }

        Cronograma c = new Cronograma();
        c.presupuestoId = presupuestoId;
        c.unidadTiempo = ut;
        c.numeroPeriodos = req.numeroPeriodos();
        cronogramaRepository.persist(c);

        List<Rubro> rubros = rubroRepository.listByPresupuesto(presupuestoId);
        for (Rubro r : rubros) {
            Actividad a = new Actividad();
            a.cronogramaId = c.id;
            a.rubroId = r.id;
            a.pesoPonderado = computePeso(r, p);
            actividadRepository.persist(a);
        }

        return buildResponse(c, p);
    }

    @Transactional
    public CronogramaResponse configurar(Long cronogramaId, CronogramaConfigurarRequest req) {
        Cronograma c = cronogramaRepository
                .findByIdOptional(cronogramaId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Cronograma no encontrado"));
        Presupuesto p = presupuestoService.validar(c.presupuestoId);

        if (req.unidadTiempo() != null) {
            try {
                c.unidadTiempo = UnidadTiempo.valueOf(req.unidadTiempo());
            } catch (IllegalArgumentException e) {
                throw ProblemaException.validacion("unidadTiempo debe ser SEMANA o MES");
            }
        }

        if (req.numeroPeriodos() != null) {
            short newPeriods = req.numeroPeriodos();
            if (newPeriods < c.numeroPeriodos) {
                boolean wouldLoseData = checkDataLoss(c.id, newPeriods);
                if (wouldLoseData && !Boolean.TRUE.equals(req.confirmarPerdida())) {
                    throw ProblemaException.reduccionPeriodosRequiereConfirmacion(
                            "Reducir los periodos eliminará datos de avance en los periodos superiores a "
                                    + newPeriods);
                }
                if (wouldLoseData) {
                    truncateAvance(c.id, newPeriods);
                }
            }
            c.numeroPeriodos = newPeriods;
        }

        cronogramaRepository.persist(c);
        return buildResponse(c, p);
    }

    @Transactional
    public CronogramaResponse actualizarAvance(Long cronogramaId, Long actividadId, ActividadAvanceRequest req) {
        Cronograma c = cronogramaRepository
                .findByIdOptional(cronogramaId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Cronograma no encontrado"));
        Presupuesto p = presupuestoService.validar(c.presupuestoId);

        Actividad a = actividadRepository
                .findByIdOptional(actividadId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Actividad no encontrada"));
        if (!a.cronogramaId.equals(cronogramaId)) {
            throw ProblemaException.noEncontrado("Actividad no pertenece a este cronograma");
        }

        for (String key : req.avancePorPeriodo().keySet()) {
            int period;
            try {
                period = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                throw ProblemaException.validacion("Las claves de avancePorPeriodo deben ser números de periodo");
            }
            if (period < 1 || period > c.numeroPeriodos) {
                throw ProblemaException.validacion("Periodo " + key + " fuera de rango (1.." + c.numeroPeriodos + ")");
            }
        }

        a.avancePorPeriodo = new HashMap<>(req.avancePorPeriodo());
        actividadRepository.persist(a);
        return buildResponse(c, p);
    }

    @Transactional
    public CronogramaResponse marcarRevisado(Long cronogramaId) {
        Cronograma c = cronogramaRepository
                .findByIdOptional(cronogramaId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Cronograma no encontrado"));
        Presupuesto p = presupuestoService.validar(c.presupuestoId);
        c.totalGeneralRevisado = p.total;
        c.fechaRevision = Instant.now();
        cronogramaRepository.persist(c);
        return buildResponse(c, p);
    }

    private CronogramaResponse buildResponse(Cronograma c, Presupuesto p) {
        List<Actividad> acts = actividadRepository.listByCronograma(c.id);
        List<Rubro> rubros = rubroRepository.listByPresupuesto(c.presupuestoId);
        Map<Long, Rubro> rubrosById = rubros.stream().collect(Collectors.toMap(r -> r.id, Function.identity()));
        return CronogramaMapper.toResponse(c, p.total, acts, rubrosById);
    }

    private BigDecimal computePeso(Rubro r, Presupuesto p) {
        if (p.total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return r.precioTotal.divide(p.total, 4, RoundingMode.HALF_UP);
    }

    private boolean checkDataLoss(Long cronogramaId, short newMax) {
        List<Actividad> acts = actividadRepository.listByCronograma(cronogramaId);
        for (Actividad a : acts) {
            for (Map.Entry<String, BigDecimal> entry : a.avancePorPeriodo.entrySet()) {
                int period = Integer.parseInt(entry.getKey());
                if (period > newMax && entry.getValue().compareTo(BigDecimal.ZERO) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private void truncateAvance(Long cronogramaId, short newMax) {
        List<Actividad> acts = actividadRepository.listByCronograma(cronogramaId);
        for (Actividad a : acts) {
            a.avancePorPeriodo.entrySet().removeIf(e -> {
                try {
                    return Integer.parseInt(e.getKey()) > newMax;
                } catch (NumberFormatException ex) {
                    return true;
                }
            });
            actividadRepository.persist(a);
        }
    }
}
