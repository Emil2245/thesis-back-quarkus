package ec.uce.propuestas.proyecto.service;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.proyecto.dto.ProyectoCrearRequest;
import ec.uce.propuestas.proyecto.dto.ProyectoEditarRequest;
import ec.uce.propuestas.proyecto.dto.ProyectoResponse;
import ec.uce.propuestas.proyecto.entity.EstadoProyecto;
import ec.uce.propuestas.proyecto.entity.PlazoUnidad;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.mapper.ProyectoMapper;
import ec.uce.propuestas.proyecto.repository.ProyectoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ProyectoService {

    @Inject
    ProyectoRepository proyectoRepository;

    /** Lista los proyectos del usuario autenticado (propietario), paginado. */
    public Page<ProyectoResponse> listarDeUsuario(
            Long usuarioId, String q, EstadoProyecto estado, int pageIndex, int pageSize) {
        List<Proyecto> items;
        long total;
        if (q != null && !q.isBlank() || estado != null) {
            items = proyectoRepository.buscarDePropietario(usuarioId, q, estado, pageIndex, pageSize);
            total = proyectoRepository.contarDePropietario(usuarioId);
        } else {
            items = proyectoRepository.listarDePropietario(usuarioId, pageIndex, pageSize);
            total = proyectoRepository.contarDePropietario(usuarioId);
        }
        return Page.of(items.stream().map(ProyectoMapper::toResponse).toList(), total, pageIndex, pageSize);
    }

    @Transactional
    public ProyectoResponse crear(Long usuarioId, ProyectoCrearRequest req) {
        Proyecto p = new Proyecto();
        p.usuarioId = usuarioId;
        p.nombreProyecto = req.nombreProyecto();
        p.codigo = req.codigo();
        p.descripcion = req.descripcion();
        p.anio = req.anio();
        p.fechaInicio = req.fechaInicio();
        p.plazoEjecucion = req.plazoEjecucion();
        p.plazoUnidad = req.plazoUnidad() == null
                ? null
                : PlazoUnidad.valueOf(req.plazoUnidad().toUpperCase());
        p.direccionInstitucional = req.direccionInstitucional();
        p.subdireccionInstitucional = req.subdireccionInstitucional();
        p.estado = EstadoProyecto.BORRADOR;
        proyectoRepository.persist(p);
        return ProyectoMapper.toResponse(p);
    }

    /**
     * Plan 07 — edición vía identidad externa (UUIDv7). Resuelve por
     * {@code publicId + owner} a BIGINT interno antes de mutar. Una entrada
     * ajena o inexistente lanza 404 (RNF-05).
     */
    @Transactional
    public ProyectoResponse actualizar(Long usuarioId, UUID publicId, ProyectoEditarRequest req) {
        Proyecto p = validarPropietario(usuarioId, publicId);
        p.nombreProyecto = req.nombreProyecto();
        p.codigo = req.codigo();
        p.descripcion = req.descripcion();
        if (req.anio() != null) p.anio = req.anio();
        if (req.fechaInicio() != null) p.fechaInicio = req.fechaInicio();
        if (req.plazoEjecucion() != null) p.plazoEjecucion = req.plazoEjecucion();
        if (req.plazoUnidad() != null && !req.plazoUnidad().isBlank()) {
            p.plazoUnidad = PlazoUnidad.valueOf(req.plazoUnidad().toUpperCase());
        }
        p.direccionInstitucional = req.direccionInstitucional();
        p.subdireccionInstitucional = req.subdireccionInstitucional();
        proyectoRepository.persist(p);
        return ProyectoMapper.toResponse(p);
    }

    @Transactional
    public void eliminar(Long usuarioId, UUID publicId) {
        Proyecto p = validarPropietario(usuarioId, publicId);
        if (p.estado == EstadoProyecto.FINALIZADO) {
            throw ProblemaException.validacion("No se puede eliminar un proyecto FINALIZADO");
        }
        proyectoRepository.delete(p);
    }

    @Transactional
    public void guardarLogo(Long usuarioId, Long id, byte[] data) {
        Proyecto p = validarPropietario(usuarioId, id);
        p.logo = data;
        proyectoRepository.persist(p);
    }

    public Proyecto validarPropietario(Long usuarioId, UUID publicId) {
        return proyectoRepository
                .findByPublicIdAndOwnerScope(publicId, usuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Proyecto no encontrado"));
    }

    public Proyecto validarPropietario(Long usuarioId, Long id) {
        return proyectoRepository
                .findByIdYPropietario(id, usuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Proyecto no encontrado"));
    }
}
