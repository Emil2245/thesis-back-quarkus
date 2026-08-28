package ec.uce.propuestas.apu.service;

import ec.uce.propuestas.apu.dto.ApuCalculoLinea;
import ec.uce.propuestas.apu.dto.ApuCalculoParametros;
import ec.uce.propuestas.apu.dto.ApuCalculoResponse;
import ec.uce.propuestas.apu.dto.ApuCalculoResumen;
import ec.uce.propuestas.apu.dto.ApuCalculoSeccion;
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
import java.math.RoundingMode;
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
                new ApuSnapshot(apu.codigo, apu.porcentajeIndirecto, filas),
                new ParametrosCalculo(
                        params.porcentajeHerramientaMenor, params.porcentajeIndirecto, apu.porcentajeDescuento));

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
                    SeccionTipo.EQUIPO, true, porcentajeHm.multiply(BigDecimal.valueOf(100)), null, null, null);
        }
        BigDecimal precioInsumo = insumo == null ? null : insumo.precioUnitario;
        return new FilaSnapshot(tipo, false, d.cantidad, d.rendimiento, precioInsumo, overrideDeDetalle(d, tipo));
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

    /**
     * P-27 (dossier §B.8). Proyecta el cálculo actual de un APU como
     * {@link ApuCalculoResponse} sin escribir nada en BD. Lee el estado
     * persistido (filas + parámetros del proyecto), delega en
     * {@link Motor#calcularApu} (puro) y arma la estructura semántica:
     * parámetros efectivos, 4 secciones (con líneas + operación/resultado a 6
     * dp) y resumen CD / CD ajustado / CI / CT.
     */
    public ApuCalculoResponse proyectar(Apu apu) {
        if (apu == null) {
            throw ProblemaException.noEncontrado("APU no encontrado");
        }
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
                new ApuSnapshot(apu.codigo, apu.porcentajeIndirecto, filas),
                new ParametrosCalculo(
                        params.porcentajeHerramientaMenor, params.porcentajeIndirecto, apu.porcentajeDescuento));

        return new ApuCalculoResponse(
                apu.publicId,
                apu.codigo,
                buildParametros(params, apu),
                buildSecciones(secciones, entidades, out),
                buildResumen(out, apu));
    }

    private ApuCalculoParametros buildParametros(ParametrosProyecto params, Apu apu) {
        BigDecimal ciDefault = params.porcentajeIndirecto;
        BigDecimal ciAplicado = apu.porcentajeIndirecto != null
                ? apu.porcentajeIndirecto
                : (ciDefault != null ? ciDefault : BigDecimal.ZERO);
        BigDecimal descuento = apu.porcentajeDescuento == null ? BigDecimal.ZERO : apu.porcentajeDescuento;
        return new ApuCalculoParametros(params.porcentajeHerramientaMenor, ciDefault, ciAplicado, descuento);
    }

    private List<ApuCalculoSeccion> buildSecciones(
            List<ApuSeccion> secciones, List<ApuDetalle> entidades, ApuCalculado out) {
        // ApuCalculado.todasFilas está en orden M (HM primero, luego resto de Equipo), N, O, P.
        // entidades mantiene el mismo orden que las filas (mismo recorrido en proyectar() y recalcular()).
        List<FilaCalculada> calc = out.filas();
        List<List<ApuCalculoLinea>> porSeccion = new ArrayList<>();
        for (int i = 0; i < secciones.size(); i++) {
            porSeccion.add(new ArrayList<>());
        }
        for (int i = 0; i < entidades.size(); i++) {
            ApuDetalle d = entidades.get(i);
            FilaCalculada fc = calc.get(i);
            ApuSeccion seccion = secciones.stream()
                    .filter(s -> s.id.equals(d.seccionId))
                    .findFirst()
                    .orElseThrow();
            int idx = secciones.indexOf(seccion);
            porSeccion.get(idx).add(buildLinea(d, fc, out));
        }

        List<ApuCalculoSeccion> result = new ArrayList<>();
        for (int i = 0; i < secciones.size(); i++) {
            ApuSeccion s = secciones.get(i);
            BigDecimal subtotal = subtotalDeSeccion(out, s.tipo);
            List<ApuCalculoLinea> lineas = porSeccion.get(i);
            String operacion = operacionSeccion(lineas);
            result.add(new ApuCalculoSeccion(s.tipo, escala6(subtotal), operacion, escala6(subtotal), lineas));
        }
        return result;
    }

    private ApuCalculoLinea buildLinea(ApuDetalle d, FilaCalculada fc, ApuCalculado out) {
        String operacion;
        BigDecimal resultado = escala6(fc.costoFila());
        if (fc.esHerramientaMenor()) {
            // HM: porcentaje × subtotalN (N se calcula primero en el motor).
            BigDecimal cantidad = fc.cantidad() == null ? BigDecimal.ZERO : fc.cantidad();
            BigDecimal pctDecimal = cantidad.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            operacion = pctDecimal.toPlainString() + " × "
                    + escala6(out.subtotalN()).toPlainString();
        } else if (fc.seccion() == SeccionTipo.EQUIPO || fc.seccion() == SeccionTipo.MANO_OBRA) {
            operacion = escala6(fc.cantidad()).toPlainString()
                    + " × " + escala6(fc.precioUnitarioEfectivo()).toPlainString()
                    + " × " + escala6(fc.rendimiento()).toPlainString();
        } else {
            // MATERIAL / TRANSPORTE
            operacion = escala6(fc.cantidad()).toPlainString() + " × "
                    + escala6(fc.precioUnitarioEfectivo()).toPlainString();
        }
        return new ApuCalculoLinea(
                d.publicId,
                d.orden,
                fc.seccion(),
                fc.esHerramientaMenor(),
                d.insumoId,
                d.descripcion,
                d.cantidad,
                d.rendimiento,
                fc.precioUnitarioEfectivo(),
                fc.costoHora(),
                operacion,
                resultado);
    }

    private static String operacionSeccion(List<ApuCalculoLinea> lineas) {
        if (lineas.isEmpty()) return "0";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lineas.size(); i++) {
            if (i > 0) sb.append(" + ");
            sb.append(lineas.get(i).resultado().toPlainString());
        }
        return sb.toString();
    }

    private ApuCalculoResumen buildResumen(ApuCalculado out, Apu apu) {
        BigDecimal cd = escala6(out.costoDirecto());
        BigDecimal cdAjustado = escala6(out.costoDirectoAjustado());
        BigDecimal ci = escala6(out.costoIndirecto());
        BigDecimal ct = escala6(out.costoTotal());
        BigDecimal descuento = apu.porcentajeDescuento == null ? BigDecimal.ZERO : apu.porcentajeDescuento;
        BigDecimal factor = BigDecimal.ONE.subtract(descuento);
        String operacionCdAjustado = cd.toPlainString() + " × " + factor.toPlainString();
        return new ApuCalculoResumen(cd, cdAjustado, operacionCdAjustado, ci, ct);
    }

    private static BigDecimal subtotalDeSeccion(ApuCalculado out, SeccionTipo tipo) {
        return switch (tipo) {
            case EQUIPO -> out.subtotalM();
            case MANO_OBRA -> out.subtotalN();
            case MATERIAL -> out.subtotalO();
            case TRANSPORTE -> out.subtotalP();
        };
    }

    private static BigDecimal escala6(BigDecimal value) {
        if (value == null) return null;
        return value.setScale(6, RoundingMode.HALF_UP);
    }
}
