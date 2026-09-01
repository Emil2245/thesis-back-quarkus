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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cálculo de un APU (RNF-02 a nivel APU). Plan 023 introduce el seam
 * read-only {@link #calcular(Apu)} que construye el {@link ApuSnapshot}
 * desde la BD, llama a {@link Motor#calcularApu} y devuelve el
 * {@link ApuCalculado} <b>sin</b> persistir derivados — para alimentar el
 * resumen por componente (P-30) y otros consumidores on-demand sin tocar
 * la BD. {@link #recalcular(Apu)} delega en {@link #calcular(Apu)} y
 * luego persiste los derivados (totales del APU, subtotales de sección,
 * costo/costo_hora por fila), preservando exactamente el comportamiento
 * previo del write-through.
 *
 * <p>No toca nada dentro de {@code motor/}.</p>
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

    /**
     * Plan 023 — read-only: construye el snapshot del APU, llama al motor y
     * devuelve el {@link ApuCalculado} sin persistir nada. Pensado para
     * consumidores on-demand (P-30 resumen, cálculo proyectado) que no quieren
     * pagar el costo del write-through.
     *
     * <p>Se ejecuta dentro de la transacción del caller (no se anota con
     * {@code @Transactional} propio): si el caller está dentro de una
     * transacción más amplia, comparte su contexto y ve la sesión de la
     * misma forma que {@code recalcular}. El motor es puro Java y no requiere
     * contexto transaccional propio.</p>
     */
    public ApuCalculado calcular(Apu apu) {
        Long proyectoId = apuRepository
                .proyectoDePresupuesto(apu.presupuestoId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));
        ParametrosProyecto params = parametrosService.obtenerEfectivosSinCrear(proyectoId);

        List<ApuSeccion> secciones = seccionRepository.listarDeApu(apu.id);
        secciones.sort(Comparator.comparingInt(s -> s.tipo.ordinal()));

        List<FilaSnapshot> filas = new ArrayList<>();

        for (ApuSeccion seccion : secciones) {
            List<ApuDetalle> detalles = detalleRepository.listarDeSeccion(seccion.id);
            for (ApuDetalle d : detalles) {
                Insumo insumo = d.insumoId == null ? null : insumoRepository.findById(d.insumoId);
                filas.add(snapshotDeDetalle(d, seccion.tipo, insumo, params.porcentajeHerramientaMenor));
            }
        }

        return Motor.calcularApu(
                new ApuSnapshot(apu.codigo, apu.porcentajeIndirecto, filas),
                new ParametrosCalculo(params.porcentajeHerramientaMenor, params.porcentajeIndirecto));
    }

    /**
     * Write-through del cálculo de un APU (RNF-02, a nivel APU). Plan 023
     * refactor: delega en {@link #calcular(Apu)} (read-only, recién
     * introducido) y luego persiste los derivados sobre la salida del motor.
     * Comportamiento y semántica idénticos al release previo (Plan 013).
     */
    @Transactional
    public ApuCalculado recalcular(Apu apu) {
        ApuCalculado out = calcular(apu);

        List<ApuSeccion> secciones = seccionRepository.listarDeApu(apu.id);
        secciones.sort(Comparator.comparingInt(s -> s.tipo.ordinal()));

        List<ApuDetalle> entidades = new ArrayList<>();
        for (ApuSeccion seccion : secciones) {
            entidades.addAll(detalleRepository.listarDeSeccion(seccion.id));
        }

        List<FilaCalculada> calc = out.filas();
        // Layout de out.filas() = [M (HM primero si existe), N, O, P]. Las
        // entidades vienen en orden persistido (orden ascendente por sección,
        // HM puede estar en cualquier posición dentro de M). Usamos el mapa
        // compartido para NO asumir índices paralelos — sin esto, mover el HM
        // a un orden no primero mezclaría su costo con el de las filas no-HM.
        Map<Long, Integer> calcIdx = mapearDetallesACalc(entidades, secciones);
        Long proyectoId = apuRepository
                .proyectoDePresupuesto(apu.presupuestoId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));
        ParametrosProyecto params = parametrosService.obtenerOCrear(proyectoId);
        for (ApuDetalle d : entidades) {
            FilaCalculada fc = calc.get(calcIdx.get(d.id));
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

    /**
     * Construye la fila snapshot que alimenta al motor. Plan 04 (P-26):
     * si la fila es una "pendiente" (sin insumo resuelto desde la carga de
     * plantilla) y no trae override manual, se fuerza {@code overridePrecio
     * = 0} en la columna de override de la sección — esto permite que el
     * motor calcule 0 sin NPE y mantiene {@code precioInsumo} coherente con
     * la realidad (no se inventa un precio). Para filas HM se sigue el
     * camino habitual (cantidad = %HM, precioInsumo/overridePrecio null).
     */
    public static FilaSnapshot snapshotDeDetalle(
            ApuDetalle d, SeccionTipo tipo, Insumo insumo, BigDecimal porcentajeHm) {
        if (d.esHerramientaMenor) {
            return new FilaSnapshot(
                    SeccionTipo.EQUIPO, true, porcentajeHm.multiply(BigDecimal.valueOf(100)), null, null, null);
        }
        BigDecimal precioInsumo = insumo == null ? null : insumo.precioUnitario;
        BigDecimal override = overrideDeDetalle(d, tipo);
        // Pendiente (Plan 04 §4.4): sin insumo y sin override → escribimos
        // override = 0 en la columna de la sección para que el motor no NPEe
        // al multiplicar por null. Las filas reales (con insumoId set) no
        // entran aquí.
        if (insumo == null && override == null) {
            override = BigDecimal.ZERO;
        }
        return new FilaSnapshot(tipo, false, d.cantidad, d.rendimiento, precioInsumo, override);
    }

    public static BigDecimal overrideDeDetalle(ApuDetalle d, SeccionTipo tipo) {
        return switch (tipo) {
            case EQUIPO, MANO_OBRA -> d.tarifaJornal;
            case MATERIAL, TRANSPORTE -> d.precioUnitarioTarifa;
        };
    }

    public static String descripcionHm(BigDecimal porcentajeHm) {
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
            for (ApuDetalle d : detalles) {
                Insumo insumo = d.insumoId == null ? null : insumoRepository.findById(d.insumoId);
                filas.add(snapshotDeDetalle(d, seccion.tipo, insumo, params.porcentajeHerramientaMenor));
                entidades.add(d);
            }
        }

        ApuCalculado out = Motor.calcularApu(
                new ApuSnapshot(apu.codigo, apu.porcentajeIndirecto, filas),
                new ParametrosCalculo(params.porcentajeHerramientaMenor, params.porcentajeIndirecto));

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
        // Plan 015: descuento por APU retirado. La DTO ya no expone `descuento`.
        return new ApuCalculoParametros(params.porcentajeHerramientaMenor, ciDefault, ciAplicado);
    }

    private List<ApuCalculoSeccion> buildSecciones(
            List<ApuSeccion> secciones, List<ApuDetalle> entidades, ApuCalculado out) {
        // ApuCalculado.todasFilas está en orden M (HM primero, luego resto de Equipo), N, O, P.
        // entidades está en orden persistido (orden ascendente dentro de sección).
        // Hay que casar cada entidad con su FilaCalculada por sección/posición/HM;
        // NO se asume que el índice i de entidades coincide con el de calc. El
        // helper compartido se reutiliza desde recalcular para evitar drift.
        Map<Long, ApuSeccion> seccionById = new HashMap<>();
        for (ApuSeccion s : secciones) seccionById.put(s.id, s);

        Map<Long, Integer> calcIdxByDetalleId = mapearDetallesACalc(entidades, secciones);

        List<FilaCalculada> calc = out.filas();
        Map<SeccionTipo, List<ApuCalculoLinea>> porSeccion = new EnumMap<>(SeccionTipo.class);
        for (SeccionTipo t : SeccionTipo.values()) porSeccion.put(t, new ArrayList<>());
        for (ApuDetalle d : entidades) {
            ApuSeccion seccion = seccionById.get(d.seccionId);
            FilaCalculada fc = calc.get(calcIdxByDetalleId.get(d.id));
            Insumo insumo = d.insumoId == null ? null : insumoRepository.findById(d.insumoId);
            porSeccion.get(seccion.tipo).add(buildLinea(d, fc, out, insumo));
        }
        // Plan 03 — el response sale en orden persistido (orden ascendente), nunca HM-primero.
        for (List<ApuCalculoLinea> ls : porSeccion.values()) {
            ls.sort(Comparator.comparingInt(ApuCalculoLinea::orden));
        }

        List<ApuCalculoSeccion> result = new ArrayList<>();
        for (ApuSeccion s : secciones) {
            BigDecimal subtotal = subtotalDeSeccion(out, s.tipo);
            List<ApuCalculoLinea> lineas = porSeccion.get(s.tipo);
            String operacion = operacionSeccion(lineas);
            result.add(new ApuCalculoSeccion(s.tipo, escala6(subtotal), operacion, escala6(subtotal), lineas));
        }
        return result;
    }

    /**
     * Construye el mapa {@code entidad.id → índice en out.filas()} que
     * reproduce el layout del motor (M con HM primero si existe, N, O, P).
     * Se comparte entre el write-through de {@link #recalcular} y el armado
     * del response en {@link #proyectar} para que ambos casen cada
     * {@link ApuDetalle} con su {@link FilaCalculada} correctamente. Sin
     * este helper, un HM movido a un orden distinto de 1 mezclaba su
     * costo/costoHora con las filas no-HM (Plan 03 + Plan 04 §3 fix).
     */
    public static Map<Long, Integer> mapearDetallesACalc(List<ApuDetalle> entidades, List<ApuSeccion> secciones) {
        Map<Long, SeccionTipo> tipoPorSeccion = new HashMap<>();
        for (ApuSeccion s : secciones) tipoPorSeccion.put(s.id, s.tipo);

        Map<SeccionTipo, Integer> totalByTipo = new EnumMap<>(SeccionTipo.class);
        for (SeccionTipo t : SeccionTipo.values()) totalByTipo.put(t, 0);
        for (ApuDetalle e : entidades) {
            SeccionTipo t = tipoPorSeccion.get(e.seccionId);
            if (t == null) {
                throw ProblemaException.validacion("Sección no encontrada para fila " + e.id);
            }
            totalByTipo.merge(t, 1, Integer::sum);
        }

        int mStart = 0;
        int nStart = mStart + totalByTipo.get(SeccionTipo.EQUIPO);
        int oStart = nStart + totalByTipo.get(SeccionTipo.MANO_OBRA);
        int pStart = oStart + totalByTipo.get(SeccionTipo.MATERIAL);

        Map<Long, Integer> out = new HashMap<>();
        int mNonHmCursor = mStart + 1; // salta el slot HM
        int nCursor = nStart;
        int oCursor = oStart;
        int pCursor = pStart;
        for (ApuDetalle e : entidades) {
            SeccionTipo t = tipoPorSeccion.get(e.seccionId);
            if (t == SeccionTipo.EQUIPO) {
                out.put(e.id, e.esHerramientaMenor ? mStart : mNonHmCursor++);
            } else if (t == SeccionTipo.MANO_OBRA) {
                out.put(e.id, nCursor++);
            } else if (t == SeccionTipo.MATERIAL) {
                out.put(e.id, oCursor++);
            } else {
                out.put(e.id, pCursor++);
            }
        }
        return out;
    }

    private ApuCalculoLinea buildLinea(ApuDetalle d, FilaCalculada fc, ApuCalculado out, Insumo insumo) {
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
        java.util.UUID insumoPublicId = insumo == null ? null : insumo.publicId;
        return new ApuCalculoLinea(
                d.publicId,
                d.orden,
                fc.seccion(),
                fc.esHerramientaMenor(),
                insumoPublicId,
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
        BigDecimal ci = escala6(out.costoIndirecto());
        BigDecimal ct = escala6(out.costoTotal());
        // Plan 015: P-24/S-24 withdrawn — DTO ya no expone cdAjustado ni
        // operacionCdAjustado. Queda {cd, ci, ct} únicamente.
        return new ApuCalculoResumen(cd, ci, ct);
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
