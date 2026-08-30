package ec.uce.propuestas.presupuesto.service;

import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.entity.ApuDetalle;
import ec.uce.propuestas.apu.entity.ApuSeccion;
import ec.uce.propuestas.apu.repository.ApuDetalleRepository;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.repository.ApuSeccionRepository;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import ec.uce.propuestas.insumo.repository.BaseInsumosRepository;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import ec.uce.propuestas.motor.*;
import ec.uce.propuestas.presupuesto.dto.DescuentoGlobalPreviewResponse;
import ec.uce.propuestas.presupuesto.dto.DescuentoGlobalRequest;
import ec.uce.propuestas.presupuesto.dto.PresupuestoResponse;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import ec.uce.propuestas.proyecto.entity.ParametrosProyecto;
import ec.uce.propuestas.proyecto.service.ParametrosProyectoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;

@ApplicationScoped
public class DescuentoGlobalService {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    @Inject
    PresupuestoService presupuestoService;

    @Inject
    BaseInsumosRepository baseInsumosRepository;

    @Inject
    InsumoRepository insumoRepository;

    @Inject
    ApuRepository apuRepository;

    @Inject
    ApuSeccionRepository seccionRepository;

    @Inject
    ApuDetalleRepository detalleRepository;

    @Inject
    RubroRepository rubroRepository;

    @Inject
    ParametrosProyectoService parametrosService;

    @Inject
    RecalculoService recalculoService;

    public DescuentoGlobalPreviewResponse preview(Long presupuestoId, BigDecimal porcentaje) {
        Presupuesto p = presupuestoService.validar(presupuestoId);
        ParametrosProyecto params = parametrosService.obtenerOCrear(p.proyectoId);

        BaseInsumos base = baseInsumosRepository
                .findByProyecto(p.proyectoId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Base de insumos del proyecto no encontrada"));

        List<Insumo> insumos = insumoRepository.listarDeBase(base.id);
        Map<Long, BigDecimal> preciosSimulados = new HashMap<>();
        BigDecimal factor = BigDecimal.ONE.subtract(porcentaje, MC);

        for (Insumo ins : insumos) {
            if (ins.tipo != TipoInsumo.MANO_OBRA && ins.precioUnitario != null) {
                preciosSimulados.put(
                        ins.id, ins.precioUnitario.multiply(factor, MC).setScale(6, RoundingMode.HALF_UP));
            } else {
                preciosSimulados.put(ins.id, ins.precioUnitario);
            }
        }

        List<Apu> apus = apuRepository.list("presupuestoId", presupuestoId);
        List<DescuentoGlobalPreviewResponse.DescuentoApuPreview> porApu = new ArrayList<>();
        Map<Long, ApuCalculado> calculadosSimulados = new HashMap<>();

        for (Apu apu : apus) {
            List<ApuSeccion> secciones = seccionRepository.listarDeApu(apu.id);
            secciones.sort(Comparator.comparingInt(s -> s.tipo.ordinal()));

            List<FilaSnapshot> filas = new ArrayList<>();
            for (ApuSeccion seccion : secciones) {
                List<ApuDetalle> detalles = detalleRepository.listarDeSeccion(seccion.id);
                if (seccion.tipo == SeccionTipo.EQUIPO) {
                    detalles.sort(Comparator.comparingInt((ApuDetalle d) -> d.esHerramientaMenor ? 0 : 1)
                            .thenComparing(d -> d.orden));
                }
                for (ApuDetalle d : detalles) {
                    if (d.esHerramientaMenor) {
                        filas.add(new FilaSnapshot(
                                SeccionTipo.EQUIPO,
                                true,
                                params.porcentajeHerramientaMenor.multiply(BigDecimal.valueOf(100)),
                                null,
                                null,
                                null));
                    } else {
                        BigDecimal precioInsumo = d.insumoId == null ? null : preciosSimulados.get(d.insumoId);
                        BigDecimal override =
                                switch (seccion.tipo) {
                                    case EQUIPO, MANO_OBRA -> d.tarifaJornal;
                                    case MATERIAL, TRANSPORTE -> d.precioUnitarioTarifa;
                                };
                        filas.add(new FilaSnapshot(
                                seccion.tipo, false, d.cantidad, d.rendimiento, precioInsumo, override));
                    }
                }
            }

            ApuCalculado out = Motor.calcularApu(
                    new ApuSnapshot(apu.codigo, apu.porcentajeIndirecto, filas),
                    new ParametrosCalculo(
                            params.porcentajeHerramientaMenor, params.porcentajeIndirecto, apu.porcentajeDescuento));

            calculadosSimulados.put(apu.id, out);
            porApu.add(new DescuentoGlobalPreviewResponse.DescuentoApuPreview(
                    apu.id,
                    apu.codigo,
                    out.costoDirecto(),
                    out.costoDirectoAjustado(),
                    out.costoIndirecto(),
                    out.costoTotal()));
        }

        BigDecimal totalGeneralProyectado = BigDecimal.ZERO;
        List<Rubro> rubros = rubroRepository.listByPresupuesto(presupuestoId);
        for (Rubro r : rubros) {
            ApuCalculado sim = calculadosSimulados.get(r.apuId);
            if (sim != null && r.cantidad != null) {
                totalGeneralProyectado = totalGeneralProyectado.add(
                        r.cantidad.multiply(sim.costoTotal(), MC).setScale(6, RoundingMode.HALF_UP));
            }
        }

        return new DescuentoGlobalPreviewResponse(
                porcentaje, porApu, p.total, totalGeneralProyectado.setScale(6, RoundingMode.HALF_UP));
    }

    @Transactional
    public PresupuestoResponse aplicar(Long presupuestoId, DescuentoGlobalRequest req) {
        Presupuesto p = presupuestoService.validar(presupuestoId);

        BaseInsumos base = baseInsumosRepository
                .findByProyecto(p.proyectoId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Base de insumos del proyecto no encontrada"));

        List<Insumo> insumos = insumoRepository.listarDeBase(base.id);
        BigDecimal factor = BigDecimal.ONE.subtract(req.porcentaje(), MC);

        for (Insumo ins : insumos) {
            if (ins.tipo != TipoInsumo.MANO_OBRA && ins.precioUnitario != null) {
                ins.precioUnitario = ins.precioUnitario.multiply(factor, MC).setScale(6, RoundingMode.HALF_UP);
                insumoRepository.persist(ins);
            }
        }

        recalculoService.recalcular(presupuestoId);
        return presupuestoService.obtenerArbol(presupuestoId);
    }
}
