package ec.uce.propuestas.proyecto.service;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.proyecto.dto.ParametrosProyectoEditarRequest;
import ec.uce.propuestas.proyecto.dto.ParametrosProyectoResponse;
import ec.uce.propuestas.proyecto.entity.ParametrosProyecto;
import ec.uce.propuestas.proyecto.entity.ParametrosSistema;
import ec.uce.propuestas.proyecto.mapper.ParametrosProyectoMapper;
import ec.uce.propuestas.proyecto.repository.ParametrosProyectoRepository;
import ec.uce.propuestas.proyecto.repository.ParametrosSistemaRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ParametrosProyectoService {

    @Inject
    ParametrosProyectoRepository parametrosRepository;

    @Inject
    ParametrosSistemaRepository sistemaRepository;

    @Inject
    ProyectoService proyectoService;

    @Transactional
    public ParametrosProyectoResponse leer(Long usuarioId, Long proyectoId) {
        proyectoService.validarPropietario(usuarioId, proyectoId);
        ParametrosProyecto p = obtenerOCrear(proyectoId);
        return ParametrosProyectoMapper.toResponse(p);
    }

    @Transactional
    public ParametrosProyectoResponse actualizar(Long usuarioId, Long proyectoId, ParametrosProyectoEditarRequest req) {
        proyectoService.validarPropietario(usuarioId, proyectoId);
        ParametrosProyecto p = obtenerOCrear(proyectoId);
        p.porcentajeHerramientaMenor = req.porcentajeHerramientaMenor();
        p.porcentajeIndirecto = req.porcentajeIndirecto();
        p.iva = req.iva();
        p.moneda = req.moneda();
        parametrosRepository.persist(p);
        return ParametrosProyectoMapper.toResponse(p);
    }

    /** Crea la fila de parámetros con defaults si aún no existe (tabla de uno-a-uno). */
    @Transactional
    public ParametrosProyecto obtenerOCrear(Long proyectoId) {
        ParametrosProyecto p = parametrosRepository.findById(proyectoId);
        if (p == null) {
            p = new ParametrosProyecto();
            p.proyectoId = proyectoId;
            parametrosRepository.persist(p);
        }
        return p;
    }

    /** Parámetros globales — solo lectura para la API pública. */
    public ParametrosSistema leerSistema() {
        ParametrosSistema s = sistemaRepository.findById((short) 1);
        if (s == null) throw ProblemaException.noEncontrado("Parámetros de sistema no inicializados");
        return s;
    }
}
