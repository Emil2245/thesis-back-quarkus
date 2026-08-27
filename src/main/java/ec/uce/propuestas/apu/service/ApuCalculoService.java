package ec.uce.propuestas.apu.service;

import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.entity.ApuDetalle;
import ec.uce.propuestas.apu.entity.ApuSeccion;
import ec.uce.propuestas.apu.repository.ApuDetalleRepository;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.repository.ApuSeccionRepository;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import ec.uce.propuestas.motor.*;
import ec.uce.propuestas.proyecto.entity.ParametrosProyecto;
import ec.uce.propuestas.proyecto.service.ParametrosProyectoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Write-through del cálculo de un APU (RNF-02, a nivel APU). Construye el
 * {@link ApuSnapshot} desde la BD, llama a {@link Motor#calcularApu} y persiste
 * los derivados (totales del APU, subtotales de sección, costo/costo_hora por
 * fila). No toca nada dentro de {@code motor/}.
 */
@ApplicationScoped
public class ApuCalculoService {

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

    @Transactional
    public ApuCalculado recalcular(Apu apu) {
        Long proyectoId = apuRepository
                .proyectoDePresupuesto(apu.presupuestoId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));
        ParametrosProyecto params = parametrosService.obtenerOCrear(proyectoId);

        List<ApuSeccion> secciones = seccionRepository.listarDeApu(apu.id);
        secciones.sort(Comparator.comparingInt(s -> s.tipo.ordinal()));

        List<FilaSnapshot> filas = new ArrayList<>();
        List<ApuDetalle> entidades = new ArrayList<>();

        for (ApuSeccion seccion : secciones) {
            List<ApuDetalle> detalles = detalleRepository.listarDeSeccion(seccion.id);
            if (seccion.tipo == SeccionTipo.EQUIPO) {
                detalles.sort(Comparator.comparingInt((ApuDetalle d) -> d.esHerramientaMenor ? 0 : 1)
                        .thenComparing(d -> d.orden));
            }
            for (ApuDetalle d : detalles) {
                Insumo insumo = d.insumoId == null ? null : insumoRepository.findById(d.insumoId);
                filas.add(snapshotDeDetalle(d, seccion.tipo, insumo, params.porcentajeHerramientaMenor));
                entidades.add(d);
            }
        }

        ApuCalculado out = Motor.calcularApu(
                new ApuSnapshot(apu.codigo, false, filas),
                new ParametrosCalculo(
                        params.porcentajeHerramientaMenor,
                        params.porcentajeIndirecto,
                        apu.porcentajeIndirecto,
                        apu.porcentajeDescuento));

        List<FilaCalculada> calc = out.filas();
        for (int i = 0; i < entidades.size(); i++) {
            ApuDetalle d = entidades.get(i);
            FilaCalculada fc = calc.get(i);
            d.costo = fc.costoFila();
            if (fc.costoHora() != null) d.costoHora = fc.costoHora();
            if (d.esHerramientaMenor) {
                d.descripcion = descripcionHm(params.porcentajeHerramientaMenor);
            }
            detalleRepository.persist(d);
        }

        for (ApuSeccion s : secciones) {
            s.subtotal = switch (s.tipo) {
                case EQUIPO -> out.subtotalM();
                case MANO_OBRA -> out.subtotalN();
                case MATERIAL -> out.subtotalO();
                case TRANSPORTE -> out.subtotalP();
            };
            seccionRepository.persist(s);
        }

        apu.costoDirecto = out.costoDirecto();
        apu.costoIndirecto = out.costoIndirecto();
        apu.costoTotal = out.costoTotal();
        apuRepository.persist(apu);
        return out;
    }

    /** Resuelve el precio efectivo de una fila: COALESCE(override, Insumo.precio). */
    public BigDecimal precioEfectivo(ApuDetalle d, SeccionTipo tipo, Insumo insumo) {
        if (d.esHerramientaMenor) return null;
        BigDecimal override = overrideDeDetalle(d, tipo);
        return override != null ? override : (insumo == null ? null : insumo.precioUnitario);
    }

    static FilaSnapshot snapshotDeDetalle(ApuDetalle d, SeccionTipo tipo, Insumo insumo, BigDecimal porcentajeHm) {
        if (d.esHerramientaMenor) {
            return new FilaSnapshot(
                    SeccionTipo.EQUIPO, true, porcentajeHm.multiply(BigDecimal.valueOf(100)), null, null, null, null);
        }
        BigDecimal precioInsumo = insumo == null ? null : insumo.precioUnitario;
        return new FilaSnapshot(tipo, false, d.cantidad, d.rendimiento, precioInsumo, overrideDeDetalle(d, tipo), null);
    }

    static BigDecimal overrideDeDetalle(ApuDetalle d, SeccionTipo tipo) {
        return switch (tipo) {
            case EQUIPO, MANO_OBRA -> d.tarifaJornal;
            case MATERIAL, TRANSPORTE -> d.precioUnitarioTarifa;
        };
    }

    static String descripcionHm(BigDecimal porcentajeHm) {
        String pct = porcentajeHm
                .multiply(BigDecimal.valueOf(100))
                .stripTrailingZeros()
                .toPlainString();
        return "Herramienta Menor " + pct + "%MO";
    }
}
