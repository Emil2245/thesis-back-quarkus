package ec.uce.propuestas.proyecto.service;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.proyecto.dto.FirmanteCrearRequest;
import ec.uce.propuestas.proyecto.dto.FirmanteResponse;
import ec.uce.propuestas.proyecto.entity.Firmante;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.mapper.FirmanteMapper;
import ec.uce.propuestas.proyecto.repository.FirmanteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class FirmanteService {

    @Inject
    FirmanteRepository firmanteRepository;

    @Inject
    ProyectoService proyectoService;

    /** Plan 07 — el resource llega con el {@code publicId} UUIDv7 del proyecto. */
    public List<FirmanteResponse> listarDeProyecto(Long usuarioId, UUID proyectoPublicId) {
        Proyecto proyecto = proyectoService.validarPropietario(usuarioId, proyectoPublicId);
        return firmanteRepository.listarDeProyecto(proyecto.id).stream()
                .map(FirmanteMapper::toResponse)
                .toList();
    }

    @Transactional
    public FirmanteResponse crear(Long usuarioId, UUID proyectoPublicId, FirmanteCrearRequest req) {
        Proyecto proyecto = proyectoService.validarPropietario(usuarioId, proyectoPublicId);
        if (firmanteRepository.existeRolOrden(proyecto.id, req.rol(), req.orden())) {
            throw ProblemaException.validacion("Ya existe un firmante con ese rol y orden");
        }
        Firmante f = new Firmante();
        f.proyectoId = proyecto.id;
        f.nombre = req.nombre();
        f.cargo = req.cargo();
        f.rol = req.rol();
        f.orden = req.orden();
        firmanteRepository.persist(f);
        return FirmanteMapper.toResponse(f);
    }

    /**
     * Plan 07 — ambos identificadores del path son UUIDv7. La resolución de
     * ownership del proyecto se hace en {@code ProyectoService}; la fila del
     * firmante se valida por su {@code publicId} con scope al proyecto dueño.
     */
    @Transactional
    public FirmanteResponse actualizar(
            Long usuarioId, UUID proyectoPublicId, UUID firmantePublicId, FirmanteCrearRequest req) {
        Proyecto proyecto = proyectoService.validarPropietario(usuarioId, proyectoPublicId);
        Firmante f = firmanteRepository
                .findByPublicIdAndProyecto(firmantePublicId, proyecto.id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Firmante no encontrado"));
        f.nombre = req.nombre();
        f.cargo = req.cargo();
        f.rol = req.rol();
        f.orden = req.orden();
        firmanteRepository.persist(f);
        return FirmanteMapper.toResponse(f);
    }

    @Transactional
    public void eliminar(Long usuarioId, UUID proyectoPublicId, UUID firmantePublicId) {
        Proyecto proyecto = proyectoService.validarPropietario(usuarioId, proyectoPublicId);
        Firmante f = firmanteRepository
                .findByPublicIdAndProyecto(firmantePublicId, proyecto.id)
                .orElseThrow(() -> ProblemaException.noEncontrado("Firmante no encontrado"));
        firmanteRepository.delete(f);
    }
}
