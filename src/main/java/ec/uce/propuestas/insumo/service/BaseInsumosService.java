package ec.uce.propuestas.insumo.service;

import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.insumo.dto.BaseInsumosResponse;
import ec.uce.propuestas.insumo.dto.InsumoResponse;
import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.TipoBase;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import ec.uce.propuestas.insumo.mapper.BaseInsumosMapper;
import ec.uce.propuestas.insumo.mapper.InsumoMapper;
import ec.uce.propuestas.insumo.repository.BaseInsumosRepository;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class BaseInsumosService {

    public static final int PAGE_SIZE_DEFAULT = 25;
    public static final long DESACTUALIZADO_DAYS = 90;

    @Inject
    BaseInsumosRepository baseInsumosRepository;

    @Inject
    InsumoRepository insumoRepository;

    /** Base PROYECTO del proyecto; la crea si no existe (cada proyecto tiene una). */
    @Transactional
    public BaseInsumos asegurarBaseProyecto(Long proyectoId) {
        return baseInsumosRepository.findByProyecto(proyectoId).orElseGet(() -> {
            BaseInsumos base = new BaseInsumos();
            base.nombre = "Insumos del proyecto #" + proyectoId;
            base.tipo = TipoBase.PROYECTO;
            base.proyectoId = proyectoId;
            base.archivada = false;
            baseInsumosRepository.persist(base);
            return base;
        });
    }

    public BaseInsumos obtenerBaseProyecto(Long proyectoId) {
        return baseInsumosRepository
                .findByProyecto(proyectoId)
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException("El proyecto no tiene base de insumos"));
    }

    /** Bases centrales (P-13). Las archivadas no se exponen (D-12). */
    public List<BaseInsumosResponse> listarCentrales() {
        return baseInsumosRepository.listarCentralesActivas().stream()
                .map(b -> BaseInsumosMapper.toResponse(b, insumoRepository.contarDeBase(b.id)))
                .toList();
    }

    /** Lista insumos de una base con filtros y paginación (P-13/P-16). */
    public Page<InsumoResponse> listarInsumosBase(
            Long baseId, TipoInsumo tipo, String q, boolean desactualizadosOnly, int pageIndex, int pageSize) {
        long corte = System.currentTimeMillis() - DESACTUALIZADO_DAYS * 86400L * 1000;
        var items =
                insumoRepository
                        .listarDeBaseConFiltros(baseId, tipo, q, desactualizadosOnly, corte, pageIndex, pageSize)
                        .stream()
                        .map(InsumoMapper::toResponse)
                        .toList();
        long total = insumoRepository.contarDeBaseConFiltros(baseId, tipo, q, desactualizadosOnly, corte);
        return Page.of(items, total, pageIndex, pageSize);
    }
}
