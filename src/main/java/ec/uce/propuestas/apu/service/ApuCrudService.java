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
import ec.uce.propuestas.motor.SeccionTipo;
import ec.uce.propuestas.proyecto.entity.ParametrosProyecto;
import ec.uce.propuestas.proyecto.service.ParametrosProyectoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
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
    ParametrosProyectoService parametrosService;

    @Inject
    ApuCalculoService calculoService;

    @Transactional
    public ApuResponse crear(Long presupuestoId, ApuCrearRequest req) {
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

        calculoService.recalcular(apu);
        return respuestaCompleta(apu);
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

        Insumo insumo = insumoRepository.findById(req.insumoId());
        if (insumo == null) {
            throw ProblemaException.noEncontrado("Insumo no encontrado");
        }
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
        if (d.esHerramientaMenor) {
            throw ProblemaException.filaProtegida("La fila de Herramienta Menor no es editable");
        }
        SeccionTipo tipo = tipoDeDetalle(apuId, d.seccionId);

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

    @Transactional
    public ApuResponse editarEspecificacion(Long apuId, String texto) {
        Apu apu = apuRepository
                .findByIdOptional(apuId)
                .orElseThrow(() -> ProblemaException.noEncontrado("APU no encontrado"));
        if (texto != null && texto.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536) {
            throw ProblemaException.validacion("La especificación técnica no puede superar 64 KB");
        }
        apu.especificacionTecnica = texto;
        apuRepository.persist(apu);
        return obtener(apuId);
    }
}
