package ec.uce.propuestas.documento.exportacion;

import ec.uce.propuestas.apu.repository.ApuDetalleRepository;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.repository.ApuSeccionRepository;
import ec.uce.propuestas.apu.service.ApuCalculoService;
import ec.uce.propuestas.common.ItemJerarquico;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.config.DisplayConfig;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.entity.TipoBase;
import ec.uce.propuestas.insumo.repository.BaseInsumosRepository;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import ec.uce.propuestas.presupuesto.entity.Capitulo;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.repository.CapituloRepository;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import ec.uce.propuestas.proyecto.repository.FirmanteRepository;
import ec.uce.propuestas.proyecto.repository.ProyectoRepository;
import ec.uce.propuestas.proyecto.service.ParametrosProyectoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Solo se ejecuta dentro de la frontera transaccional de captura. */
@ApplicationScoped
public class ProyeccionDocumentoService {
    @Inject
    ProyectoRepository proyectos;

    @Inject
    ParametrosProyectoService parametros;

    @Inject
    FirmanteRepository firmantes;

    @Inject
    CapituloRepository capitulos;

    @Inject
    RubroRepository rubros;

    @Inject
    ApuRepository apus;

    @Inject
    ApuSeccionRepository secciones;

    @Inject
    ApuDetalleRepository detalles;

    @Inject
    BaseInsumosRepository bases;

    @Inject
    InsumoRepository insumos;

    @Inject
    ApuCalculoService calculo;

    @Inject
    DisplayConfig display;

    SnapshotDocumento proyectar(Presupuesto presupuesto, Long caller, PreflightDocumento preflight) {
        var proyecto = proyectos.findById(presupuesto.proyectoId);
        exigir(proyecto != null && Objects.equals(proyecto.usuarioId, caller), "Proyecto ilegible");
        var p = parametros.obtenerEfectivosSinCrear(proyecto.id);
        var params = new SnapshotDocumento.Parametros(
                p.porcentajeHerramientaMenor,
                p.porcentajeIndirecto,
                p.ciIndividualHabilitado,
                p.iva,
                p.moneda,
                p.mostrarSeccionesVacias,
                p.sufijosSeccionActivos,
                p.mostrarSubtotalesSeccion,
                p.mostrarSubtotalesPie,
                p.mostrarNombreProyectoHeader,
                p.enumerarApus,
                p.mensajeFooter,
                p.modoCodigoRubro.name());
        var responsables = firmantes.listarDeProyecto(proyecto.id).stream()
                .map(f -> {
                    exigir(Objects.equals(f.proyectoId, proyecto.id) && f.publicId != null, "Firmante ilegible");
                    return new SnapshotDocumento.Firmante(f.publicId, f.nombre, f.cargo, f.rol.name(), f.orden);
                })
                .toList();
        List<Capitulo> cs = capitulos.listarPorPresupuestoOrdenado(presupuesto.id);
        Map<Long, Capitulo> porId = new HashMap<>();
        for (var c : cs) {
            exigir(c.publicId != null && Objects.equals(c.presupuestoId, presupuesto.id), "Capítulo ilegible");
            porId.put(c.id, c);
        }
        for (var c : cs) {
            Set<Long> vistos = new HashSet<>();
            var cursor = c;
            while (cursor != null) {
                exigir(vistos.add(cursor.id), "Árbol ilegible");
                if (cursor.parentId == null) break;
                cursor = porId.get(cursor.parentId);
                exigir(cursor != null, "Árbol ilegible");
            }
        }
        List<Rubro> rs = new ArrayList<>(rubros.listarPorPresupuesto(presupuesto.id));
        rs.sort(Comparator.comparing((Rubro r) -> r.item, ItemJerarquico.ORDEN));
        for (var r : rs) {
            exigir(porId.containsKey(r.capituloId) && r.publicId != null, "Rubro ilegible");
            exigir(
                    noNegativo(r.cantidad) && noNegativo(r.precioUnitario) && noNegativo(r.precioTotal),
                    "Importe ilegible");
        }
        var base = bases.findByProyecto(proyecto.id).orElse(null);
        if (base != null)
            exigir(base.tipo == TipoBase.PROYECTO && Objects.equals(base.proyectoId, proyecto.id), "Base ilegible");
        // Precarga solo PROYECTO: no resuelve fallback CENTRAL/PERSONAL ni datos ajenos.
        Map<Long, Insumo> inputs = new HashMap<>();
        if (base != null) for (var i : insumos.listarDeBase(base.id)) inputs.put(i.id, i);
        List<SnapshotDocumento.Apu> analyses = new ArrayList<>();
        Map<Long, java.util.UUID> apuIds = new HashMap<>();
        var identities = PreflightDocumentoService.apuIdentidades(rs);
        var selectedApus = identities.isEmpty()
                ? List.<ec.uce.propuestas.apu.entity.Apu>of()
                : apus.find("presupuestoId = ?1 and id in ?2", presupuesto.id, identities)
                        .list();
        exigir(selectedApus.size() == identities.size(), "Vínculo APU ilegible");
        Map<Long, ec.uce.propuestas.apu.entity.Apu> scopedApus = new HashMap<>();
        for (var a : selectedApus) {
            exigir(a.publicId != null, "Identidad APU ilegible");
            scopedApus.put(a.id, a);
        }
        var allSections = identities.isEmpty()
                ? List.<ec.uce.propuestas.apu.entity.ApuSeccion>of()
                : secciones.find("apuId in ?1 order by orden", identities).list();
        var sectionIds = allSections.stream().map(s -> s.id).toList();
        var allDetails = sectionIds.isEmpty()
                ? List.<ec.uce.propuestas.apu.entity.ApuDetalle>of()
                : detalles.find("seccionId in ?1 order by orden", sectionIds).list();
        Map<Long, List<ec.uce.propuestas.apu.entity.ApuDetalle>> detallesPorSeccion = new HashMap<>();
        for (var s : allSections) detallesPorSeccion.put(s.id, new ArrayList<>());
        for (var d : allDetails) detallesPorSeccion.get(d.seccionId).add(d);
        for (Long id : identities) {
            var a = scopedApus.get(id);
            apuIds.put(id, a.publicId);
            List<SnapshotDocumento.Seccion> blocks = new ArrayList<>();
            var ss = allSections.stream()
                    .filter(s -> Objects.equals(s.apuId, a.id))
                    .sorted(Comparator.comparingInt(s -> s.tipo.ordinal()))
                    .toList();
            for (var s : ss) {
                exigir(Objects.equals(s.apuId, a.id), "Sección ilegible");
                List<SnapshotDocumento.Detalle> rows = new ArrayList<>();
                for (var d : allDetails) {
                    if (!Objects.equals(d.seccionId, s.id)) continue;
                    exigir(Objects.equals(d.seccionId, s.id) && d.publicId != null, "Detalle ilegible");
                    var i = d.insumoId == null ? null : inputs.get(d.insumoId);
                    exigir(d.insumoId == null || i != null, "Insumo ilegible");
                    if (i != null) exigir(i.publicId != null && Objects.equals(i.baseId, base.id), "Insumo ilegible");
                    exigir(noNegativo(d.costo) && noNegativo(d.costoHora), "Importe ilegible");
                    if (!d.esHerramientaMenor) {
                        exigir(noNegativo(d.cantidad), "Detalle ilegible");
                        if (s.tipo == ec.uce.propuestas.motor.SeccionTipo.EQUIPO
                                || s.tipo == ec.uce.propuestas.motor.SeccionTipo.MANO_OBRA)
                            exigir(noNegativo(d.rendimiento), "Detalle ilegible");
                    }
                    var input = i == null
                            ? null
                            : new SnapshotDocumento.Insumo(
                                    i.publicId, i.codigo, i.tipo.name(), i.descripcion, i.unidad, i.precioUnitario);
                    rows.add(new SnapshotDocumento.Detalle(
                            d.publicId,
                            d.orden,
                            d.esHerramientaMenor,
                            d.descripcion,
                            d.unidad,
                            d.cantidad,
                            d.rendimiento,
                            d.tarifaJornal,
                            d.precioUnitarioTarifa,
                            calculo.precioEfectivo(d, s.tipo, i),
                            d.costoHora,
                            d.costo,
                            input));
                }
                blocks.add(new SnapshotDocumento.Seccion(s.tipo, s.orden, s.subtotal, rows));
            }
            // Todos los vínculos se validaron antes del calculador canónico read-safe.
            var result = calculo.calcular(a, ss, detallesPorSeccion, inputs, p);
            analyses.add(new SnapshotDocumento.Apu(
                    a.publicId,
                    a.codigo,
                    a.descripcion,
                    a.unidad,
                    a.especificacionTecnica,
                    a.porcentajeIndirecto,
                    a.costoDirecto,
                    a.costoIndirecto,
                    a.costoTotal,
                    result,
                    blocks));
        }
        Map<Long, List<SnapshotDocumento.Rubro>> porCapitulo = new HashMap<>();
        for (var r : rs)
            porCapitulo
                    .computeIfAbsent(r.capituloId, ignored -> new ArrayList<>())
                    .add(new SnapshotDocumento.Rubro(
                            r.publicId,
                            apuIds.get(r.apuId),
                            r.item,
                            r.codigo,
                            r.descripcion,
                            r.unidad,
                            r.cantidad,
                            r.precioUnitario,
                            r.precioTotal));
        var roots = cs.stream()
                .filter(c -> c.parentId == null)
                .map(c -> congelar(c, cs, porCapitulo))
                .toList();
        return new SnapshotDocumento(
                presupuesto.publicId,
                presupuesto.version,
                presupuesto.esVigente,
                presupuesto.notas,
                presupuesto.total,
                new SnapshotDocumento.Proyecto(
                        proyecto.publicId,
                        proyecto.nombreProyecto,
                        proyecto.codigo,
                        proyecto.descripcion,
                        proyecto.anio,
                        proyecto.fechaInicio,
                        proyecto.plazoEjecucion,
                        proyecto.plazoUnidad == null ? null : proyecto.plazoUnidad.name(),
                        proyecto.estado.name(),
                        proyecto.direccionInstitucional,
                        proyecto.subdireccionInstitucional),
                params,
                new SnapshotDocumento.Display(display.precision(), display.precisionPorcentaje()),
                responsables,
                roots,
                analyses,
                preflight);
    }

    private SnapshotDocumento.Capitulo congelar(
            Capitulo c, List<Capitulo> cs, Map<Long, List<SnapshotDocumento.Rubro>> rs) {
        return new SnapshotDocumento.Capitulo(
                c.publicId,
                c.item,
                c.descripcion,
                c.orden,
                c.total,
                cs.stream()
                        .filter(h -> Objects.equals(h.parentId, c.id))
                        .map(h -> congelar(h, cs, rs))
                        .toList(),
                rs.getOrDefault(c.id, List.of()));
    }

    private static boolean noNegativo(java.math.BigDecimal value) {
        return value != null && value.signum() >= 0;
    }

    static void exigir(boolean condicion, String mensaje) {
        if (!condicion) throw ilegible(mensaje);
    }

    static ProblemaException ilegible(String mensaje) {
        return ProblemaException.conflicto("export-inconsistente", mensaje);
    }
}
