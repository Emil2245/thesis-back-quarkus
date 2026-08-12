package ec.uce.propuestas.proyecto.service;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.proyecto.dto.FirmanteCrearRequest;
import ec.uce.propuestas.proyecto.dto.FirmanteResponse;
import ec.uce.propuestas.proyecto.entity.Firmante;
import ec.uce.propuestas.proyecto.mapper.FirmanteMapper;
import ec.uce.propuestas.proyecto.repository.FirmanteRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class FirmanteService {

    @Inject
    FirmanteRepository firmanteRepository;

    @Inject
    ProyectoService proyectoService;

    public List<FirmanteResponse> listarDeProyecto(Long usuarioId, Long proyectoId) {
        proyectoService.validarPropietario(usuarioId, proyectoId);
        return firmanteRepository.listarDeProyecto(proyectoId).stream()
                .map(FirmanteMapper::toResponse)
                .toList();
    }

    @Transactional
    public FirmanteResponse crear(Long usuarioId, Long proyectoId, FirmanteCrearRequest req) {
        proyectoService.validarPropietario(usuarioId, proyectoId);
        if (firmanteRepository.existeRolOrden(proyectoId, req.rol(), req.orden())) {
            throw ProblemaException.validacion("Ya existe un firmante con ese rol y orden");
        }
        Firmante f = new Firmante();
        f.proyectoId = proyectoId;
        f.nombre = req.nombre();
        f.cargo = req.cargo();
        f.rol = req.rol();
        f.orden = req.orden();
        firmanteRepository.persist(f);
        return FirmanteMapper.toResponse(f);
    }

    @Transactional
    public FirmanteResponse actualizar(Long usuarioId, Long proyectoId, Long firmanteId, FirmanteCrearRequest req) {
        proyectoService.validarPropietario(usuarioId, proyectoId);
        Firmante f = firmanteRepository
                .findByIdYProyecto(firmanteId, proyectoId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Firmante no encontrado"));
        f.nombre = req.nombre();
        f.cargo = req.cargo();
        f.rol = req.rol();
        f.orden = req.orden();
        firmanteRepository.persist(f);
        return FirmanteMapper.toResponse(f);
    }

    @Transactional
    public void eliminar(Long usuarioId, Long proyectoId, Long firmanteId) {
        proyectoService.validarPropietario(usuarioId, proyectoId);
        Firmante f = firmanteRepository
                .findByIdYProyecto(firmanteId, proyectoId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Firmante no encontrado"));
        firmanteRepository.delete(f);
    }
}
