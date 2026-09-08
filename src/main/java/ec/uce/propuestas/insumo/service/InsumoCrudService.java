package ec.uce.propuestas.insumo.service;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.insumo.dto.InsumoCrearRequest;
import ec.uce.propuestas.insumo.dto.InsumoEditarRequest;
import ec.uce.propuestas.insumo.dto.InsumoResponse;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import ec.uce.propuestas.insumo.mapper.InsumoMapper;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import ec.uce.propuestas.recalculo.Alcance;
import ec.uce.propuestas.recalculo.RecalculoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.UUID;

/**
 * CRUD de insumo con reglas D-06 (unidad fija 'h' para MO/Equipo y restricciones
 * de edición de unidad), D-08 (no eliminar si está referenciado) y unicidad de
 * (base, codigo). Fallos -> {@link ProblemaException}.
 *
 * <p>Plan 07 — el {@code insumoId} de los métodos públicos es el {@code publicId}
 * UUIDv7 (identidad externa inmutable). El seam resuelve UUID → BIGINT interno
 * vía {@link InsumoRepository#findByPublicIdAndBase} dentro de la base indicada.
 * El resto del flujo opera con BIGINTs internos.</p>
 */
@ApplicationScoped
public class InsumoCrudService {

    @Inject
    InsumoRepository insumoRepository;

    @Inject
    RecalculoService recalculoService;

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

    /**
     * Plan 07 — el caller pasa el {@code publicId} UUIDv7 del insumo y la base
     * (ya validada por owner en la capa de resource). El seam resuelve UUID →
     * BIGINT interno antes de mutar.
     */
    @Transactional
    public InsumoResponse actualizar(Long baseId, UUID insumoPublicId, InsumoEditarRequest req) {
        Insumo e = validarExistencia(baseId, insumoPublicId);
        e.descripcion = req.descripcion();
        aplicarUnidad(e, e.tipo, req.unidad());
        e.precioUnitario = req.precioUnitario();
        insumoRepository.persist(e);
        insumoRepository.flush();
        recalculoService.recalcular(new Alcance.Insumo(e.id));
        return InsumoMapper.toResponse(e);
    }

    /** D-08: no permite borrar si el insumo está vinculado a un APU. */
    @Transactional
    public void eliminar(Long baseId, UUID insumoPublicId) {
        Insumo e = validarExistencia(baseId, insumoPublicId);
        long usos = insumoRepository.contarUsosEnApuDetalle(e.id);
        if (usos > 0) {
            throw ProblemaException.conflicto(
                    "insumo-en-uso",
                    "No se puede eliminar el insumo: está referenciado en " + usos + " parte(s) de APU");
        }
        insumoRepository.delete(e);
    }

    private Insumo validarExistencia(Long baseId, UUID insumoPublicId) {
        return insumoRepository
                .findByPublicIdAndBase(insumoPublicId, baseId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Insumo no encontrado en esta base"));
    }

    private void aplicarUnidad(Insumo e, TipoInsumo tipo, String unidad) {
        // D-06: unidad es no editable para MO/Equipo (siempre 'h').
        e.unidad = TipoInsumo.esUnidadFijaH(tipo) ? "h" : (unidad == null ? "" : unidad);
    }
}
