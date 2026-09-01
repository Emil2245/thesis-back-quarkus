package ec.uce.propuestas.recalculo.internal;

import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.entity.ApuDetalle;
import ec.uce.propuestas.apu.entity.ApuSeccion;
import ec.uce.propuestas.apu.repository.ApuDetalleRepository;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.repository.ApuSeccionRepository;
import ec.uce.propuestas.apu.service.ApuCalculoService;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import ec.uce.propuestas.motor.ApuSnapshot;
import ec.uce.propuestas.motor.CapituloSnapshot;
import ec.uce.propuestas.motor.FilaSnapshot;
import ec.uce.propuestas.motor.ParametrosCalculo;
import ec.uce.propuestas.motor.RubroSnapshot;
import ec.uce.propuestas.motor.VersionSnapshot;
import ec.uce.propuestas.presupuesto.entity.Capitulo;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.repository.CapituloRepository;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import ec.uce.propuestas.proyecto.entity.ParametrosProyecto;
import ec.uce.propuestas.proyecto.service.ParametrosProyectoService;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Helper package-localizado (visible al paquete {@code ec.uce.propuestas.recalculo}
 * porque Java exige visibilidad pública para cruzar límites de subpaquete).
 * Construye el {@link VersionSnapshot} que consume el motor, leyendo desde la
 * base de datos el árbol completo de capítulos, rubros, APUs y sus filas.
 *
 * <p>El motor no se reabre; este helper se limita a mapear entidades JPA →
 * snapshots del motor. La frontera APU→Rubro se aplica únicamente dentro del
 * motor, en {@code internal/Consolidador}.
 *
 * <p>Regla de disciplina: este builder no es un seam adicional; los tests del
 * módulo profundo verifican la semántica vía {@link ec.uce.propuestas.recalculo.RecalculoService}
 * por su API pública, no tocando el builder directamente.
 */
@ApplicationScoped
public class VersionSnapshotBuilder {

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
    InsumoRepository insumoRepository;

    @Inject
    ParametrosProyectoService parametrosService;

    /**
     * Construye el {@link VersionSnapshot} de la versión indicada. Carga el árbol
     * completo de capítulos (raíz → subcapítulos), los rubros de cada capítulo,
     * el APU vinculado a cada rubro y las filas de cada APU. El cronograma es
     * nullable (I-08 todavía no existe).
     */
    public VersionSnapshot build(Long presupuestoId) {
        Presupuesto presupuesto = presupuestoRepository.findById(presupuestoId);
        if (presupuesto == null) {
            throw ProblemaException.noEncontrado("Presupuesto no encontrado");
        }
        ParametrosProyecto params = parametrosService.obtenerOCrear(presupuesto.proyectoId);
        ParametrosCalculo motorParams =
                new ParametrosCalculo(params.porcentajeHerramientaMenor, params.porcentajeIndirecto);

        List<CapituloSnapshot> raices = new ArrayList<>();
        for (Capitulo raiz : capitulosRaicesDe(presupuestoId)) {
            raices.add(construirCapitulo(raiz, presupuestoId, motorParams));
        }
        return new VersionSnapshot(motorParams, raices, null);
    }

    private List<Capitulo> capitulosRaicesDe(Long presupuestoId) {
        return capituloRepository
                .find(
                        "presupuestoId = :pid and parentId is null order by orden",
                        new Parameters().and("pid", presupuestoId))
                .list();
    }

    private CapituloSnapshot construirCapitulo(Capitulo cap, Long presupuestoId, ParametrosCalculo motorParams) {
        // Subcapítulos hijos (un nivel recursivo vía parent_id; el caso real IESS
        // tiene subcapítulos anidados pero el modelo SQL es plano con self-FK,
        // por lo que esta implementación procesa exactamente 1 nivel. Plan 022
        // ampliará a recursivo si los fixtures I-07 lo exigen).
        List<CapituloSnapshot> subs = new ArrayList<>();
        List<Capitulo> hijos = capituloRepository
                .find(
                        "presupuestoId = :pid and parentId = :parentId order by orden",
                        Parameters.with("pid", presupuestoId).and("parentId", cap.id))
                .list();
        for (Capitulo hijo : hijos) {
            subs.add(construirCapitulo(hijo, presupuestoId, motorParams));
        }

        // Rubros directos del capítulo
        List<RubroSnapshot> rubros = new ArrayList<>();
        List<ec.uce.propuestas.presupuesto.entity.Rubro> rubrosEnt = rubroRepository
                .find("capituloId = :cid order by item", Parameters.with("cid", cap.id))
                .list();
        for (ec.uce.propuestas.presupuesto.entity.Rubro r : rubrosEnt) {
            rubros.add(construirRubro(r, motorParams));
        }
        return new CapituloSnapshot(cap.item, cap.descripcion, depthDe(cap), subs, rubros);
    }

    private int depthDe(Capitulo cap) {
        int depth = 1;
        Long parent = cap.parentId;
        while (parent != null) {
            depth++;
            Capitulo parentCap = capituloRepository.findById(parent);
            if (parentCap == null) break;
            parent = parentCap.parentId;
        }
        return depth;
    }

    private RubroSnapshot construirRubro(ec.uce.propuestas.presupuesto.entity.Rubro r, ParametrosCalculo motorParams) {
        Apu apu = apuRepository.findById(r.apuId);
        if (apu == null) {
            // D-09 garantiza 1:1 rubro↔apu; si falta, snapshot consistente con
            // la realidad — registramos un APU vacío para que Motor.calcularApu
            // no lance NPE y el total del rubro se quede en 0.
            ApuSnapshot vacio = new ApuSnapshot(r.codigo, null, List.of());
            return new RubroSnapshot(r.codigo, r.cantidad, vacio);
        }
        ApuSnapshot apuSnap = construirApuSnapshot(apu, motorParams);
        return new RubroSnapshot(r.codigo, r.cantidad, apuSnap);
    }

    /**
     * Construye el snapshot de un APU. Se reutiliza el helper de
     * {@link ApuCalculoService#snapshotDeDetalle} para mantener la semántica
     * del write-through coherente (HM → cantidad = %HM × 100; insumo NULL →
     * override 0). Plan 015: se omite el campo {@code porcentajeDescuento} del
     * motor (ya retirado).
     */
    private ApuSnapshot construirApuSnapshot(Apu apu, ParametrosCalculo motorParams) {
        // Resolver ParametrosProyecto para resolver la fila HM con el %HM vigente
        // (motorParams ya trae el %HM; se obtiene el proyecto del apu para la
        // descripción HM, pero el helper snapshotDeDetalle sólo usa el %HM).
        Long proyectoId = apuRepository
                .proyectoDePresupuesto(apu.presupuestoId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));
        ParametrosProyecto params = parametrosService.obtenerOCrear(proyectoId);

        List<ApuSeccion> secciones = seccionRepository.listarDeApu(apu.id);
        secciones.sort(Comparator.comparingInt(s -> s.tipo.ordinal()));

        List<FilaSnapshot> filas = new ArrayList<>();
        for (ApuSeccion seccion : secciones) {
            List<ApuDetalle> detalles = detalleRepository.listarDeSeccion(seccion.id);
            for (ApuDetalle d : detalles) {
                Insumo insumo = d.insumoId == null ? null : insumoRepository.findById(d.insumoId);
                filas.add(ApuCalculoService.snapshotDeDetalle(
                        d, seccion.tipo, insumo, params.porcentajeHerramientaMenor));
            }
        }
        return new ApuSnapshot(apu.codigo, apu.porcentajeIndirecto, filas);
    }
}
