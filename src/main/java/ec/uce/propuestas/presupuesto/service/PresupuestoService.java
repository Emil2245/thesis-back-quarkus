package ec.uce.propuestas.presupuesto.service;

import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.entity.ApuDetalle;
import ec.uce.propuestas.apu.entity.ApuSeccion;
import ec.uce.propuestas.apu.repository.ApuDetalleRepository;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.repository.ApuSeccionRepository;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.presupuesto.dto.*;
import ec.uce.propuestas.presupuesto.entity.Capitulo;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.mapper.CapituloMapper;
import ec.uce.propuestas.presupuesto.mapper.PresupuestoMapper;
import ec.uce.propuestas.presupuesto.mapper.RubroMapper;
import ec.uce.propuestas.presupuesto.repository.CapituloRepository;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
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
public class PresupuestoService {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    CapituloRepository capituloRepository;

    @Inject
    RubroRepository rubroRepository;

    @Inject
    ApuRepository apuRepository;

    @Inject
    ApuSeccionRepository seccionRepository;

    @Inject
    ApuDetalleRepository detalleRepository;

    @Inject
    RecalculoService recalculoService;

    @Inject
    ParametrosProyectoService parametrosService;

    public List<PresupuestoVersionResponse> listarVersiones(Long proyectoId) {
        return presupuestoRepository.listByProyecto(proyectoId).stream()
                .map(PresupuestoMapper::toVersionResponse)
                .toList();
    }

    public Presupuesto validar(Long presupuestoId) {
        return presupuestoRepository
                .findByIdOptional(presupuestoId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));
    }

    @Transactional
    public PresupuestoVersionResponse crearVersion(Long proyectoId, PresupuestoVersionCrearRequest req) {
        Presupuesto origen = presupuestoRepository
                .findByProyectoYId(proyectoId, req.origenId())
                .orElseThrow(() -> ProblemaException.noEncontrado("Versión de presupuesto origen no encontrada"));

        short nextVer = presupuestoRepository.nextVersion(proyectoId);

        Presupuesto nuevo = new Presupuesto();
        nuevo.proyectoId = proyectoId;
        nuevo.version = nextVer;
        nuevo.esVigente = false;
        nuevo.origenId = origen.id;
        nuevo.notas = req.notas();
        nuevo.porcentajeIndirecto = origen.porcentajeIndirecto;
        nuevo.total = BigDecimal.ZERO;
        presupuestoRepository.persist(nuevo);

        // 1. Clonar APUs
        Map<Long, Long> apuIdMap = new HashMap<>(); // old -> new
        List<Apu> apusOrigen = apuRepository.list("presupuestoId", origen.id);
        for (Apu oldApu : apusOrigen) {
            Apu newApu = new Apu();
            newApu.presupuestoId = nuevo.id;
            newApu.codigo = oldApu.codigo;
            newApu.descripcion = oldApu.descripcion;
            newApu.unidad = oldApu.unidad;
            newApu.porcentajeIndirecto = oldApu.porcentajeIndirecto;
            newApu.porcentajeDescuento = oldApu.porcentajeDescuento;
            newApu.costoDirecto = oldApu.costoDirecto;
            newApu.costoIndirecto = oldApu.costoIndirecto;
            newApu.costoTotal = oldApu.costoTotal;
            apuRepository.persist(newApu);
            apuIdMap.put(oldApu.id, newApu.id);

            List<ApuSeccion> secciones = seccionRepository.listarDeApu(oldApu.id);
            for (ApuSeccion oldSec : secciones) {
                ApuSeccion newSec = new ApuSeccion();
                newSec.apuId = newApu.id;
                newSec.tipo = oldSec.tipo;
                newSec.subtotal = oldSec.subtotal;
                newSec.orden = oldSec.orden;
                seccionRepository.persist(newSec);

                List<ApuDetalle> detalles = detalleRepository.listarDeSeccion(oldSec.id);
                for (ApuDetalle oldDet : detalles) {
                    ApuDetalle newDet = new ApuDetalle();
                    newDet.seccionId = newSec.id;
                    newDet.insumoId = oldDet.insumoId;
                    newDet.descripcion = oldDet.descripcion;
                    newDet.orden = oldDet.orden;
                    newDet.esHerramientaMenor = oldDet.esHerramientaMenor;
                    newDet.cantidad = oldDet.cantidad;
                    newDet.tarifaJornal = oldDet.tarifaJornal;
                    newDet.costoHora = oldDet.costoHora;
                    newDet.rendimiento = oldDet.rendimiento;
                    newDet.unidad = oldDet.unidad;
                    newDet.precioUnitarioTarifa = oldDet.precioUnitarioTarifa;
                    newDet.costo = oldDet.costo;
                    detalleRepository.persist(newDet);
                }
            }
        }

        // 2. Clonar Capítulos (respetando jerarquía)
        Map<Long, Long> capIdMap = new HashMap<>(); // old -> new
        List<Capitulo> capsOrigen = capituloRepository.listByPresupuesto(origen.id);
        clonarSubarbolCapitulos(null, null, capsOrigen, nuevo.id, capIdMap);

        // 3. Clonar Rubros
        for (Capitulo oldCap : capsOrigen) {
            Long newCapId = capIdMap.get(oldCap.id);
            List<Rubro> rubrosOld = rubroRepository.listByCapitulo(oldCap.id);
            for (Rubro oldRubro : rubrosOld) {
                Rubro newRubro = new Rubro();
                newRubro.capituloId = newCapId;
                newRubro.apuId = apuIdMap.get(oldRubro.apuId);
                newRubro.item = oldRubro.item;
                newRubro.codigo = oldRubro.codigo;
                newRubro.descripcion = oldRubro.descripcion;
                newRubro.unidad = oldRubro.unidad;
                newRubro.cantidad = oldRubro.cantidad;
                newRubro.precioUnitario = oldRubro.precioUnitario;
                newRubro.precioTotal = oldRubro.precioTotal;
                rubroRepository.persist(newRubro);
            }
        }

        recalculoService.recalcular(nuevo.id);
        return PresupuestoMapper.toVersionResponse(nuevo);
    }

    private void clonarSubarbolCapitulos(
            Long oldParentId,
            Long newParentId,
            List<Capitulo> todosCaps,
            Long newPresupuestoId,
            Map<Long, Long> capIdMap) {
        for (Capitulo oldCap : todosCaps) {
            boolean isChild = (oldParentId == null && oldCap.parentId == null)
                    || (oldParentId != null && oldParentId.equals(oldCap.parentId));
            if (isChild) {
                Capitulo newCap = new Capitulo();
                newCap.presupuestoId = newPresupuestoId;
                newCap.parentId = newParentId;
                newCap.item = oldCap.item;
                newCap.descripcion = oldCap.descripcion;
                newCap.orden = oldCap.orden;
                newCap.total = oldCap.total;
                capituloRepository.persist(newCap);
                capIdMap.put(oldCap.id, newCap.id);

                clonarSubarbolCapitulos(oldCap.id, newCap.id, todosCaps, newPresupuestoId, capIdMap);
            }
        }
    }

    @Transactional
    public PresupuestoVersionResponse marcarVigente(Long presupuestoId) {
        Presupuesto p = validar(presupuestoId);
        List<Presupuesto> versiones = presupuestoRepository.listByProyecto(p.proyectoId);
        for (Presupuesto v : versiones) {
            if (v.esVigente && !v.id.equals(p.id)) {
                v.esVigente = false;
                presupuestoRepository.persist(v);
            }
        }
        p.esVigente = true;
        presupuestoRepository.persist(p);
        return PresupuestoMapper.toVersionResponse(p);
    }

    @Transactional
    public void eliminar(Long presupuestoId) {
        Presupuesto p = validar(presupuestoId);
        if (p.esVigente) {
            throw ProblemaException.versionVigenteProtegida(
                    "No se puede eliminar la versión vigente de un presupuesto");
        }
        presupuestoRepository.delete(p);
    }

    public PresupuestoResponse obtenerArbol(Long presupuestoId) {
        Presupuesto p = validar(presupuestoId);
        List<Capitulo> todosCaps = capituloRepository.listByPresupuesto(presupuestoId);
        Map<Long, List<Capitulo>> hijosPorPadre = new HashMap<>();
        for (Capitulo c : todosCaps) {
            hijosPorPadre.computeIfAbsent(c.parentId, k -> new ArrayList<>()).add(c);
        }
        for (List<Capitulo> list : hijosPorPadre.values()) {
            list.sort(Comparator.comparingInt(c -> c.orden == null ? 0 : c.orden));
        }

        List<CapituloResponse> arbol = new ArrayList<>();
        List<Capitulo> raices = hijosPorPadre.getOrDefault(null, Collections.emptyList());
        for (Capitulo raiz : raices) {
            arbol.add(construirCapituloResponse(raiz, hijosPorPadre));
        }

        return PresupuestoMapper.toTreeResponse(p, arbol);
    }

    private CapituloResponse construirCapituloResponse(Capitulo cap, Map<Long, List<Capitulo>> hijosPorPadre) {
        List<CapituloResponse> subcaps = new ArrayList<>();
        List<Capitulo> hijos = hijosPorPadre.getOrDefault(cap.id, Collections.emptyList());
        for (Capitulo hijo : hijos) {
            subcaps.add(construirCapituloResponse(hijo, hijosPorPadre));
        }

        List<RubroResponse> rubros = rubroRepository.listByCapitulo(cap.id).stream()
                .map(RubroMapper::toResponse)
                .toList();

        return CapituloMapper.toResponse(cap, subcaps, rubros);
    }

    public ResumenComponentesResponse obtenerResumenComponentes(Long presupuestoId) {
        Presupuesto p = validar(presupuestoId);
        ParametrosProyecto params = parametrosService.obtenerOCrear(p.proyectoId);

        BigDecimal subtotalEquipo = BigDecimal.ZERO;
        BigDecimal subtotalManoObra = BigDecimal.ZERO;
        BigDecimal subtotalMaterial = BigDecimal.ZERO;
        BigDecimal subtotalTransporte = BigDecimal.ZERO;

        List<Rubro> rubros = rubroRepository.listByPresupuesto(presupuestoId);
        for (Rubro r : rubros) {
            List<ApuSeccion> secciones = seccionRepository.listarDeApu(r.apuId);
            for (ApuSeccion s : secciones) {
                BigDecimal parte = s.subtotal.multiply(r.cantidad, MC);
                switch (s.tipo) {
                    case EQUIPO -> subtotalEquipo = subtotalEquipo.add(parte);
                    case MANO_OBRA -> subtotalManoObra = subtotalManoObra.add(parte);
                    case MATERIAL -> subtotalMaterial = subtotalMaterial.add(parte);
                    case TRANSPORTE -> subtotalTransporte = subtotalTransporte.add(parte);
                }
            }
        }

        Map<String, BigDecimal> porComponente = new LinkedHashMap<>();
        porComponente.put("EQUIPO", subtotalEquipo.setScale(6, RoundingMode.HALF_UP));
        porComponente.put("MANO_OBRA", subtotalManoObra.setScale(6, RoundingMode.HALF_UP));
        porComponente.put("MATERIAL", subtotalMaterial.setScale(6, RoundingMode.HALF_UP));
        porComponente.put("TRANSPORTE", subtotalTransporte.setScale(6, RoundingMode.HALF_UP));

        BigDecimal totalGeneral = p.total;
        BigDecimal ivaPct = params.iva != null ? params.iva : BigDecimal.ZERO;
        BigDecimal ivaReferencial = totalGeneral.multiply(ivaPct, MC).setScale(6, RoundingMode.HALF_UP);
        BigDecimal totalConIva = totalGeneral.add(ivaReferencial).setScale(6, RoundingMode.HALF_UP);

        return new ResumenComponentesResponse(porComponente, totalGeneral, ivaReferencial, totalConIva);
    }

    public ComparacionVersionesResponse compararVersiones(Long presupuestoId1, Long presupuestoId2) {
        Presupuesto p1 = validar(presupuestoId1);
        Presupuesto p2 = validar(presupuestoId2);

        if (!p1.proyectoId.equals(p2.proyectoId)) {
            throw ProblemaException.validacion("Los presupuestos a comparar deben pertenecer al mismo proyecto");
        }

        return new ComparacionVersionesResponse(List.of(armarItemComparacion(p1), armarItemComparacion(p2)));
    }

    private ComparacionVersionesResponse.PresupuestoComparacionItem armarItemComparacion(Presupuesto p) {
        List<Capitulo> raices = capituloRepository.listByPresupuestoAndParent(p.id, null);
        List<ComparacionVersionesResponse.CapituloComparacionItem> caps = raices.stream()
                .map(c -> new ComparacionVersionesResponse.CapituloComparacionItem(c.item, c.descripcion, c.total))
                .toList();
        return new ComparacionVersionesResponse.PresupuestoComparacionItem(p.id, p.version, p.total, caps);
    }

    public ValidacionPresupuestoResponse validarIntegridad(Long presupuestoId) {
        validar(presupuestoId);
        List<Rubro> rubros = rubroRepository.listByPresupuesto(presupuestoId);

        List<RubroRefResponse> puCero = new ArrayList<>();
        List<RubroRefResponse> cantCero = new ArrayList<>();
        List<RubroRefResponse> sinActividad = new ArrayList<>();

        for (Rubro r : rubros) {
            RubroRefResponse ref = new RubroRefResponse(r.id, r.item, r.codigo, r.descripcion);
            if (r.precioUnitario == null || r.precioUnitario.compareTo(BigDecimal.ZERO) == 0) {
                puCero.add(ref);
            }
            if (r.cantidad == null || r.cantidad.compareTo(BigDecimal.ZERO) == 0) {
                cantCero.add(ref);
            }
        }

        boolean exportable = puCero.isEmpty() && cantCero.isEmpty() && sinActividad.isEmpty();
        return new ValidacionPresupuestoResponse(exportable, puCero, cantCero, sinActividad);
    }
}
