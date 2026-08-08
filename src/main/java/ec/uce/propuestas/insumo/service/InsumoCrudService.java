package ec.uce.propuestas.insumo.service;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.insumo.dto.InsumoCrearRequest;
import ec.uce.propuestas.insumo.dto.InsumoEditarRequest;
import ec.uce.propuestas.insumo.dto.InsumoResponse;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import ec.uce.propuestas.insumo.mapper.InsumoMapper;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * CRUD de insumo con reglas D-06 (unidad fija 'h' para MO/Equipo y restricciones
 * de edición de unidad), D-08 (no eliminar si está referenciado) y unicidad de
 * (base, codigo). Fallos -> {@link ProblemaException}.
 */
@ApplicationScoped
public class InsumoCrudService {

    @Inject
    InsumoRepository insumoRepository;

    @Transactional
    public InsumoResponse crear(Long baseId, InsumoCrearRequest req) {
        if (insumoRepository.findByBaseYcodigo(baseId, req.codigo()).isPresent()) {
            throw ProblemaException.validacion("Código duplicado en esta base");
        }
        Insumo e = new Insumo();
        e.baseId = baseId;
        e.codigo = req.codigo();
        e.tipo = req.tipo();
        e.descripcion = req.descripcion();
        aplicarUnidad(e, req.tipo(), req.unidad());
        e.precioUnitario = req.precioUnitario();
        insumoRepository.persist(e);
        return InsumoMapper.toResponse(e);
    }

    @Transactional
    public InsumoResponse actualizar(Long baseId, Long id, InsumoEditarRequest req) {
        Insumo e = validarExistencia(baseId, id);
        e.descripcion = req.descripcion();
        aplicarUnidad(e, e.tipo, req.unidad());
        e.precioUnitario = req.precioUnitario();
        insumoRepository.persist(e);
        return InsumoMapper.toResponse(e);
    }

    /** D-08: no permite borrar si el insumo está vinculado a un APU. */
    @Transactional
    public void eliminar(Long baseId, Long id) {
        Insumo e = validarExistencia(baseId, id);
        // TODO(P-18): la verificación de uso en APUs se resuelve con el módulo APU
        // (RESTRICT real, D-08). Mientras no exista tabla apu_insumo, se permite.
        long usos = conteoUsosApu(e.id);
        if (usos > 0) {
            throw ProblemaException.validacion(
                    "No se puede eliminar el insumo: está referenciado en " + usos + " parte(s) de APU");
        }
        insumoRepository.delete(e);
    }

    /** stub: 0 hasta el módulo APU (P-18). */
    private long conteoUsosApu(Long insumoId) {
        return 0L;
    }

    private Insumo validarExistencia(Long baseId, Long id) {
        return insumoRepository.findByIdYBase(id, baseId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Insumo no encontrado en esta base"));
    }

    private void aplicarUnidad(Insumo e, TipoInsumo tipo, String unidad) {
        // D-06: unidad es no editable para MO/Equipo (siempre 'h').
        e.unidad = TipoInsumo.esUnidadFijaH(tipo) ? "h" : (unidad == null ? "" : unidad);
    }
}