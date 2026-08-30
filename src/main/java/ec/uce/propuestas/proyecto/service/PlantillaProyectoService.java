package ec.uce.propuestas.proyecto.service;

import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.entity.ApuDetalle;
import ec.uce.propuestas.apu.entity.ApuSeccion;
import ec.uce.propuestas.apu.repository.ApuDetalleRepository;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.repository.ApuSeccionRepository;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.presupuesto.entity.Capitulo;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.repository.CapituloRepository;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import ec.uce.propuestas.proyecto.dto.PlantillaProyectoCrearRequest;
import ec.uce.propuestas.proyecto.dto.PlantillaProyectoResponse;
import ec.uce.propuestas.proyecto.entity.PlantillaProyecto;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.repository.PlantillaProyectoRepository;
import ec.uce.propuestas.proyecto.repository.ProyectoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.*;

@ApplicationScoped
public class PlantillaProyectoService {

    @Inject
    PlantillaProyectoRepository plantillaRepository;

    @Inject
    ProyectoRepository proyectoRepository;

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

    public List<PlantillaProyectoResponse> listar(Long usuarioId) {
        return plantillaRepository.listByUsuario(usuarioId).stream()
                .map(p -> new PlantillaProyectoResponse(p.id, p.nombre, p.descripcion, p.createdAt))
                .toList();
    }

    @Transactional
    public PlantillaProyectoResponse crear(Long usuarioId, PlantillaProyectoCrearRequest req) {
        Proyecto proy = proyectoRepository
                .findByIdOptional(req.proyectoId())
                .orElseThrow(() -> ProblemaException.noEncontrado("Proyecto no encontrado"));

        if (!proy.usuarioId.equals(usuarioId)) {
            throw ProblemaException.noEncontrado("Proyecto no encontrado");
        }

        Presupuesto vigente = presupuestoRepository
                .findVigente(proy.id)
                .orElseThrow(() -> ProblemaException.validacion("El proyecto no tiene presupuesto vigente"));

        Map<String, Object> snapshot = buildSnapshot(vigente);

        PlantillaProyecto plantilla = new PlantillaProyecto();
        plantilla.usuarioId = usuarioId;
        plantilla.nombre = req.nombre();
        plantilla.descripcion = req.descripcion();
        plantilla.snapshot = snapshot;
        plantillaRepository.persist(plantilla);

        return new PlantillaProyectoResponse(
                plantilla.id, plantilla.nombre, plantilla.descripcion, plantilla.createdAt);
    }

    @Transactional
    public void eliminar(Long usuarioId, Long plantillaId) {
        PlantillaProyecto p = plantillaRepository
                .findByIdOptional(plantillaId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Plantilla no encontrada"));
        if (!p.usuarioId.equals(usuarioId)) {
            throw ProblemaException.noEncontrado("Plantilla no encontrada");
        }
        plantillaRepository.delete(p);
    }

    private Map<String, Object> buildSnapshot(Presupuesto presupuesto) {
        Map<String, Object> snap = new LinkedHashMap<>();

        List<Capitulo> caps = capituloRepository.listByPresupuesto(presupuesto.id);
        List<Map<String, Object>> capSnaps = new ArrayList<>();
        for (Capitulo c : caps) {
            Map<String, Object> cs = new LinkedHashMap<>();
            cs.put("item", c.item);
            cs.put("descripcion", c.descripcion);
            cs.put("parentItem", findParentItem(c.parentId, caps));
            cs.put("orden", c.orden);

            List<Rubro> rubros = rubroRepository.listByCapitulo(c.id);
            List<Map<String, Object>> rubroSnaps = new ArrayList<>();
            for (Rubro r : rubros) {
                Map<String, Object> rs = new LinkedHashMap<>();
                rs.put("item", r.item);
                rs.put("codigo", r.codigo);
                rs.put("descripcion", r.descripcion);
                rs.put("unidad", r.unidad);
                rs.put("apu", buildApuSnapshot(r.apuId));
                rubroSnaps.add(rs);
            }
            cs.put("rubros", rubroSnaps);
            capSnaps.add(cs);
        }
        snap.put("capitulos", capSnaps);
        return snap;
    }

    private Map<String, Object> buildApuSnapshot(Long apuId) {
        Apu apu = apuRepository.findById(apuId);
        if (apu == null) return Map.of();

        Map<String, Object> apuSnap = new LinkedHashMap<>();
        apuSnap.put("codigo", apu.codigo);
        apuSnap.put("descripcion", apu.descripcion);
        apuSnap.put("unidad", apu.unidad);

        List<ApuSeccion> secciones = seccionRepository.listarDeApu(apuId);
        List<Map<String, Object>> secSnaps = new ArrayList<>();
        for (ApuSeccion s : secciones) {
            Map<String, Object> ss = new LinkedHashMap<>();
            ss.put("tipo", s.tipo.name());
            ss.put("orden", s.orden);

            List<ApuDetalle> detalles = detalleRepository.listarDeSeccion(s.id);
            List<Map<String, Object>> detSnaps = new ArrayList<>();
            for (ApuDetalle d : detalles) {
                Map<String, Object> ds = new LinkedHashMap<>();
                ds.put("insumoId", d.insumoId);
                ds.put("descripcion", d.descripcion);
                ds.put("esHerramientaMenor", d.esHerramientaMenor);
                ds.put("unidad", d.unidad);
                ds.put("rendimiento", d.rendimiento);
                detSnaps.add(ds);
            }
            ss.put("detalles", detSnaps);
            secSnaps.add(ss);
        }
        apuSnap.put("secciones", secSnaps);
        return apuSnap;
    }

    private String findParentItem(Long parentId, List<Capitulo> all) {
        if (parentId == null) return null;
        for (Capitulo c : all) {
            if (c.id.equals(parentId)) return c.item;
        }
        return null;
    }
}
