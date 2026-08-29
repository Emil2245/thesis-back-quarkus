package ec.uce.propuestas.apu.service;

import ec.uce.propuestas.apu.dto.*;
import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.entity.ApuDetalle;
import ec.uce.propuestas.apu.entity.ApuSeccion;
import ec.uce.propuestas.apu.mapper.ApuDetalleMapper;
import ec.uce.propuestas.apu.mapper.ApuMapper;
import ec.uce.propuestas.apu.mapper.ApuResumenMapper;
import ec.uce.propuestas.apu.repository.ApuDetalleRepository;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.repository.ApuSeccionRepository;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import ec.uce.propuestas.insumo.service.ResolverInsumoProyectoService;
import ec.uce.propuestas.motor.SeccionTipo;
import ec.uce.propuestas.plantilla.dto.AdvertenciaPlantillaResponse;
import ec.uce.propuestas.plantilla.entity.PlantillaApu;
import ec.uce.propuestas.plantilla.service.PlantillaApuService;
import ec.uce.propuestas.proyecto.entity.ParametrosProyecto;
import ec.uce.propuestas.proyecto.service.ParametrosProyectoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** CRUD del agregado {@code apu} + filas (P-19…P-22). Recibe usuarioId/proyectoId validados por el resource. */
@ApplicationScoped
public class ApuCrudService {

    @Inject
    ApuRepository apuRepository;

    @Inject
    ApuSeccionRepository seccionRepository;

    @Inject
    ApuDetalleRepository detalleRepository;

    @Inject
    InsumoRepository insumoRepository;

    @Inject
    ResolverInsumoProyectoService resolverInsumoProyecto;

    @Inject
    ParametrosProyectoService parametrosService;

    @Inject
    ApuCalculoService calculoService;

    @Inject
    PlantillaApuService plantillaApuService;

    /**
     * Plan 04 (P-26) — Crea un APU. Variante principal: si
     * {@code req.plantillaId()} viene, delega la materialización a
     * {@link PlantillaApuService#aplicarPlantilla} y devuelve el par
     * (response + advertencias) para que el resource decida el código HTTP
     * final. Sin plantillaId, el comportamiento es el de la versión previa.
     *
     * <p>Si viene {@code plantillaId} pero el caller no la ve (404), este
     * método propaga el 404 vía el seam {@link PlantillaApuService#cargarPorOwner}.
     */
    @Transactional
    public ResultadoCrear crear(Long presupuestoId, ApuCrearRequest req, Long callerUsuarioId) {
        if (req.codigo() != null
                && !req.codigo().isBlank()
                && apuRepository
                        .findByPresupuestoYCodigo(presupuestoId, req.codigo())
                        .isPresent()) {
            throw ProblemaException.codigoDuplicado("Código duplicado en esta versión del presupuesto");
        }

        Apu apu = new Apu();
        apu.presupuestoId = presupuestoId;
        apu.codigo = req.codigo() != null && !req.codigo().isBlank() ? req.codigo() : siguienteCodigo(presupuestoId);
        apu.descripcion = req.descripcion();
        apu.unidad = req.unidad();
        apuRepository.persist(apu);

        crearSecciones(apu);
        apuRepository.persist(apu);

        List<AdvertenciaPlantillaResponse> advertencias = List.of();
        if (req.tienePlantilla()) {
            // Resolver la plantilla aquí (con autorización del caller) y
            // delegar la materialización de filas al seam de PlantillaApuService.
            PlantillaApu plantilla = plantillaApuService.cargarPorOwner(req.plantillaId(), callerUsuarioId);
            advertencias = plantillaApuService.aplicarPlantilla(apu, plantilla);
        }

        calculoService.recalcular(apu);
        return new ResultadoCrear(respuestaCompleta(apu), advertencias);
    }

    /** Variante de compatibilidad: cuando no hay plantillaId, no necesita caller. */
    public ApuResponse crearComoRespuesta(Long presupuestoId, ApuCrearRequest req) {
        return crear(presupuestoId, req, null).apu();
    }

    /** Holder del resultado de crear — advertencias para que el resource decida el HTTP status. */
    public record ResultadoCrear(ApuResponse apu, List<AdvertenciaPlantillaResponse> advertencias) {

        public boolean tieneAdvertencias() {
            return advertencias != null && !advertencias.isEmpty();
        }
    }

    public Page<ApuResumenResponse> listar(Long presupuestoId, String q, int page, int size) {
        List<ApuResumenResponse> items = apuRepository.listarDePresupuesto(presupuestoId, q, page, size).stream()
                .map(a -> ApuResumenMapper.toResponse(a, apuRepository.estaVinculado(a.id)))
                .toList();
        long total = apuRepository.contarDePresupuesto(presupuestoId, q);
        return Page.of(items, total, page, size);
    }

    public ApuResponse obtener(Long apuId) {
        return respuestaCompleta(_validar(apuId));
    }

    @Transactional
    public ApuResponse editarCabecera(Long apuId, ApuPatchRequest req) {
        Apu apu = _validar(apuId);
        if (req.codigo() != null && req.codigo().isPresent()) {
            String nuevo = req.codigo().get();
            if (nuevo != null && !nuevo.isBlank()) {
                apuRepository
                        .findByPresupuestoYCodigo(apu.presupuestoId, nuevo)
                        .filter(o -> !o.id.equals(apuId))
                        .ifPresent(o -> {
                            throw ProblemaException.codigoDuplicado("Código duplicado en esta versión del presupuesto");
                        });
                apu.codigo = nuevo;
            }
        }
        if (req.descripcion() != null && req.descripcion().isPresent()) {
            String v = req.descripcion().get();
            if (v != null && !v.isBlank()) apu.descripcion = v;
        }
        if (req.unidad() != null && req.unidad().isPresent()) {
            String v = req.unidad().get();
            if (v != null && !v.isBlank()) apu.unidad = v;
        }
        apuRepository.persist(apu);
        return respuestaCompleta(apu);
    }

    @Transactional
    public ApuResponse actualizarPorcentajeIndirecto(Long apuId, BigDecimal valor) {
        Apu apu = _validar(apuId);
        validarPorcentaje(valor, BigDecimal.ONE, "porcentajeIndirecto");
        apu.porcentajeIndirecto = valor;
        apuRepository.persist(apu);
        calculoService.recalcular(apu);
        return respuestaCompleta(apu);
    }

    @Transactional
    public ApuResponse actualizarPorcentajeDescuento(Long apuId, BigDecimal valor) {
        Apu apu = _validar(apuId);
        BigDecimal efectivo = valor == null ? BigDecimal.ZERO : valor;
        validarPorcentaje(efectivo, new BigDecimal("0.5000"), "porcentajeDescuento");
        apu.porcentajeDescuento = efectivo;
        apuRepository.persist(apu);
        calculoService.recalcular(apu);
        return respuestaCompleta(apu);
    }

    /** P-45 (N04 §ESP). Límite 65 536 bytes UTF-8 (RNF-09). {@code texto} null o "" = limpiar. */
    @Transactional
    public ApuResponse guardarEspecificacionTecnica(Long apuId, String texto) {
        Apu apu = _validar(apuId);
        String normalizado = normalizarEspecificacion(texto);
        apu.especificacionTecnica = normalizado;
        apuRepository.persist(apu);
        return respuestaCompleta(apu);
    }

    /** P-45 (N04 §ESP). GET retorna una forma JSON estable e independiente del APU. */
    public EspecificacionTecnicaResponse obtenerEspecificacionTecnica(Long apuId) {
        Apu apu = _validar(apuId);
        return new EspecificacionTecnicaResponse(apu.publicId, apu.especificacionTecnica);
    }

    static String normalizarEspecificacion(String texto) {
        if (texto == null || texto.isEmpty()) {
            return null;
        }
        if (texto.getBytes(StandardCharsets.UTF_8).length > MAX_ET_BYTES) {
            throw ProblemaException.validacion("especificacionTecnica excede el límite de 65536 bytes UTF-8");
        }
        return texto;
    }

    private static final int MAX_ET_BYTES = 65_536;

    private static void validarPorcentaje(BigDecimal valor, BigDecimal maximo, String campo) {
        if (valor != null && (valor.signum() < 0 || valor.compareTo(maximo) > 0)) {
            throw ProblemaException.validacion(campo + " fuera del rango permitido");
        }
    }

    @Transactional
    public void eliminar(Long apuId) {
        Apu apu = _validar(apuId);
        if (apuRepository.estaVinculado(apuId)) {
            throw ProblemaException.apuReferenciado(
                    "El APU está vinculado a un rubro del presupuesto y no puede eliminarse");
        }
        apuRepository.delete(apu);
    }

    @Transactional
    public ApuResponse agregarDetalle(Long apuId, ApuDetalleCrearRequest req) {
        Apu apu = _validar(apuId);
        Long proyectoId = apuRepository
                .proyectoDePresupuesto(apu.presupuestoId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));

        // N04 §A9 — copia al usar: el insumo persistido en apu_detalle debe ser SIEMPRE
        // PROYECTO del proyecto del APU. El resolver materializa una copia si la fuente es
        // CENTRAL o PERSONAL (del dueño del proyecto), reusa si ya es PROYECTO del mismo
        // proyecto, o devuelve 404 si la fuente es ajena o PERSONAL de otro dueño.
        Insumo insumo = resolverInsumoProyecto.materializarOReusar(req.insumoId(), proyectoId);
        validarSeccionParaInsumo(req.seccionTipo(), insumo.tipo);

        ApuSeccion seccion = seccionRepository
                .findByApuYTipo(apuId, req.seccionTipo())
                .orElseThrow(() -> ProblemaException.noEncontrado("Sección no encontrada para el tipo indicado"));

        ApuDetalle d = new ApuDetalle();
        d.seccionId = seccion.id;
        d.insumoId = insumo.id;
        d.descripcion = insumo.descripcion;
        d.orden = (short) (detalleRepository.maxOrdenEnSeccion(seccion.id) + 1);
        d.esHerramientaMenor = false;
        d.cantidad = req.cantidad();
        d.rendimiento = rendimientoSegunSeccion(req.seccionTipo(), req.rendimiento());
        d.unidad = switch (req.seccionTipo()) {
            case EQUIPO, MANO_OBRA -> "h";
            case MATERIAL, TRANSPORTE -> insumo.unidad;
        };
        detalleRepository.persist(d);

        calculoService.recalcular(apu);
        return respuestaCompleta(apu);
    }

    @Transactional
    public ApuResponse editarDetalle(Long apuId, Long detalleId, ApuDetallePatchRequest req) {
        Apu apu = _validar(apuId);
        ApuDetalle d = resolverDetalle(apuId, detalleId);
        SeccionTipo tipo = tipoDeDetalle(apuId, d.seccionId);

        // Plan 03 — HM acepta SOLO `orden`. Cualquier otro campo editable
        // (cantidad/rendimiento/precioOverride) sigue 409 fila-protegida.
        if (d.esHerramientaMenor && tieneOtroCampoEditable(req)) {
            throw ProblemaException.filaProtegida("La fila de Herramienta Menor no es editable");
        }

        // Plan 03 — MOVE atómico dentro de la sección. `orden` solo se procesa
        // si viene explícitamente en el body (JsonNullable isPresent()).
        if (req.orden() != null && req.orden().isPresent()) {
            Integer nuevoOrden = req.orden().get();
            if (nuevoOrden == null) {
                throw ProblemaException.validacion("orden debe estar entre 1 y el número de filas de la sección");
            }
            reordenarEnSeccion(d, nuevoOrden);
        }

        if (req.cantidad() != null
                && req.cantidad().isPresent()
                && req.cantidad().get() != null) {
            BigDecimal v = req.cantidad().get();
            if (v.signum() <= 0) throw ProblemaException.validacion("cantidad debe ser mayor a 0");
            d.cantidad = v;
        }
        if (req.rendimiento() != null && req.rendimiento().isPresent()) {
            if (req.rendimiento().get() == null) {
                if (tipo == SeccionTipo.EQUIPO || tipo == SeccionTipo.MANO_OBRA) {
                    throw ProblemaException.validacion("rendimiento es obligatorio en EQUIPO/MANO_OBRA");
                }
                d.rendimiento = null;
            } else {
                BigDecimal v = req.rendimiento().get();
                if (v.signum() <= 0) throw ProblemaException.validacion("rendimiento debe ser mayor a 0");
                d.rendimiento = v;
            }
        }
        if (req.precioOverride() != null && req.precioOverride().isPresent()) {
            if (req.precioOverride().get() == null) {
                escribirOverride(d, tipo, null);
            } else {
                BigDecimal v = req.precioOverride().get();
                if (v.signum() <= 0) throw ProblemaException.validacion("precioOverride debe ser mayor a 0");
                escribirOverride(d, tipo, v);
            }
        }
        detalleRepository.persist(d);

        calculoService.recalcular(apu);
        return respuestaCompleta(apu);
    }

    /** HM: detecta si el request trae cualquier campo editable distinto de {@code orden}. */
    private static boolean tieneOtroCampoEditable(ApuDetallePatchRequest req) {
        return (req.cantidad() != null && req.cantidad().isPresent())
                || (req.rendimiento() != null && req.rendimiento().isPresent())
                || (req.precioOverride() != null && req.precioOverride().isPresent());
    }

    /**
     * MOVE atómico dentro de la sección de la fila {@code d}.
     *
     * <p>Semántica (Plan 03, N04 §A3):
     * <ul>
     *   <li>{@code new == old}: no-op.</li>
     *   <li>{@code new < old}: los hermanos con {@code orden} en {@code [new, old)}
     *       incrementan en 1; la fila movida toma {@code new}.</li>
     *   <li>{@code new > old}: los hermanos con {@code orden} en {@code (old, new]}
     *       decrementan en 1; la fila movida toma {@code new}.</li>
     * </ul>
     *
     * <p>El destino se valida contra {@code [1, count]} (count = filas
     * actuales de la sección); un valor fuera de rango devuelve 400
     * {@code validacion}. El MOVE es transaccional dentro del mismo
     * {@code @Transactional editarDetalle}.
     */
    private void reordenarEnSeccion(ApuDetalle d, Integer nuevoOrden) {
        List<ApuDetalle> hermanos = new ArrayList<>(detalleRepository.listarDeSeccion(d.seccionId));
        int count = hermanos.size();
        if (nuevoOrden < 1 || nuevoOrden > count) {
            throw ProblemaException.validacion("orden debe estar entre 1 y " + count);
        }
        short oldOrden = d.orden;
        if (nuevoOrden == oldOrden) {
            return; // no-op
        }
        if (nuevoOrden < oldOrden) {
            for (ApuDetalle h : hermanos) {
                if (h.id.equals(d.id)) continue;
                if (h.orden >= nuevoOrden && h.orden < oldOrden) {
                    h.orden = (short) (h.orden + 1);
                    detalleRepository.persist(h);
                }
            }
        } else {
            for (ApuDetalle h : hermanos) {
                if (h.id.equals(d.id)) continue;
                if (h.orden > oldOrden && h.orden <= nuevoOrden) {
                    h.orden = (short) (h.orden - 1);
                    detalleRepository.persist(h);
                }
            }
        }
        d.orden = nuevoOrden.shortValue();
    }

    @Transactional
    public ApuResponse eliminarDetalle(Long apuId, Long detalleId) {
        Apu apu = _validar(apuId);
        ApuDetalle d = resolverDetalle(apuId, detalleId);
        if (d.esHerramientaMenor) {
            throw ProblemaException.filaProtegida("La fila de Herramienta Menor no se puede eliminar");
        }
        detalleRepository.delete(d);
        calculoService.recalcular(apu);
        return respuestaCompleta(apu);
    }

    private Apu _validar(Long apuId) {
        Apu apu = apuRepository.findById(apuId);
        if (apu == null) throw ProblemaException.noEncontrado("APU no encontrado");
        return apu;
    }

    /** Reconstruye el response completo: secciones en orden M/N/O/P + precios efectivos. */
    public ApuResponse respuestaCompleta(Apu apu) {
        Long proyectoId = apuRepository
                .proyectoDePresupuesto(apu.presupuestoId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));
        ParametrosProyecto params = parametrosService.obtenerOCrear(proyectoId);
        BigDecimal ciEfectivo = apu.porcentajeIndirecto != null ? apu.porcentajeIndirecto : params.porcentajeIndirecto;

        List<ApuSeccion> secciones = seccionRepository.listarDeApu(apu.id);
        secciones.sort(Comparator.comparingInt(s -> s.tipo.ordinal()));
        List<ApuSeccionResponse> respSecciones = new ArrayList<>();

        for (ApuSeccion s : secciones) {
            List<ApuDetalle> detalles = detalleRepository.listarDeSeccion(s.id);
            List<ApuDetalleResponse> respDetalles = detalles.stream()
                    .map(d -> {
                        Insumo insumo = d.insumoId == null ? null : insumoRepository.findById(d.insumoId);
                        return ApuDetalleMapper.toResponse(
                                d, calculoService.precioEfectivo(d, s.tipo, insumo), overrideDe(d, s.tipo) == null);
                    })
                    .toList();
            respSecciones.add(new ApuSeccionResponse(s.tipo, s.orden, s.subtotal, respDetalles));
        }

        return ApuMapper.toResponse(apu, ciEfectivo, respSecciones);
    }

    private void crearSecciones(Apu apu) {
        short orden = 1;
        for (SeccionTipo tipo :
                List.of(SeccionTipo.EQUIPO, SeccionTipo.MANO_OBRA, SeccionTipo.MATERIAL, SeccionTipo.TRANSPORTE)) {
            ApuSeccion s = new ApuSeccion();
            s.apuId = apu.id;
            s.tipo = tipo;
            s.orden = orden++;
            seccionRepository.persist(s);
        }
        // fila HM, primera del bloque M (decisión §17 #9)
        ApuSeccion equipo =
                seccionRepository.findByApuYTipo(apu.id, SeccionTipo.EQUIPO).orElseThrow();
        ApuDetalle hm = new ApuDetalle();
        hm.seccionId = equipo.id;
        hm.descripcion = "Herramienta Menor 5%MO";
        hm.orden = 1;
        hm.esHerramientaMenor = true;
        detalleRepository.persist(hm);
    }

    private ApuDetalle resolverDetalle(Long apuId, Long detalleId) {
        return detalleRepository
                .findByIdYSeccionDeApu(detalleId, apuId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Fila no encontrada en este APU"));
    }

    private SeccionTipo tipoDeDetalle(Long apuId, Long seccionId) {
        return seccionRepository.findById(seccionId).tipo;
    }

    private String siguienteCodigo(Long presupuestoId) {
        long n = apuRepository.contarDePresupuesto(presupuestoId, null) + 1;
        return String.format("APU-%03d", n);
    }

    static BigDecimal rendimientoSegunSeccion(SeccionTipo tipo, BigDecimal rendimiento) {
        if (tipo == SeccionTipo.EQUIPO || tipo == SeccionTipo.MANO_OBRA) {
            if (rendimiento == null) {
                throw ProblemaException.validacion("rendimiento es obligatorio en EQUIPO/MANO_OBRA");
            }
            return rendimiento;
        }
        return null;
    }

    static BigDecimal overrideDe(ApuDetalle d, SeccionTipo tipo) {
        return switch (tipo) {
            case EQUIPO, MANO_OBRA -> d.tarifaJornal;
            case MATERIAL, TRANSPORTE -> d.precioUnitarioTarifa;
        };
    }

    static BigDecimal escribirOverride(ApuDetalle d, SeccionTipo tipo, BigDecimal valor) {
        switch (tipo) {
            case EQUIPO, MANO_OBRA -> d.tarifaJornal = valor;
            case MATERIAL, TRANSPORTE -> d.precioUnitarioTarifa = valor;
        }
        return valor;
    }

    private static void validarSeccionParaInsumo(SeccionTipo seccion, TipoInsumo tipoInsumo) {
        boolean ok =
                switch (seccion) {
                    case EQUIPO -> tipoInsumo == TipoInsumo.EQUIPO;
                    case MANO_OBRA -> tipoInsumo == TipoInsumo.MANO_OBRA;
                    case MATERIAL -> tipoInsumo == TipoInsumo.MATERIAL;
                    case TRANSPORTE -> tipoInsumo == TipoInsumo.TRANSPORTE;
                };
        if (!ok) {
            throw ProblemaException.validacion("El insumo no es compatible con la sección " + seccion);
        }
    }
}
