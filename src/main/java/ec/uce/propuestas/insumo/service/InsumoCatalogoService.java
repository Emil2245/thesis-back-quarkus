package ec.uce.propuestas.insumo.service;

import ec.uce.propuestas.common.dto.Page;
import ec.uce.propuestas.insumo.dto.InsumoBusquedaResponse;
import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.entity.TipoBase;
import ec.uce.propuestas.insumo.repository.BaseInsumosRepository;
import ec.uce.propuestas.insumo.repository.InsumoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Selector multi-fuente de insumos (P-16/P-21): une la base CENTRAL con la base
 * PROYECTO del proyecto en una sola lista paginada y filtrable.
 */
@ApplicationScoped
public class InsumoCatalogoService {

    @Inject
    BaseInsumosRepository baseInsumosRepository;
    @Inject
    InsumoRepository insumoRepository;
    @Inject
    BaseInsumosService baseInsumosService;

    public Page<InsumoBusquedaResponse> buscar(Long proyectoId, String q, boolean soloCentrales,
                                               int pageIndex, int pageSize) {
        List<BaseInsumos> bases = new ArrayList<>(baseInsumosRepository.listarCentralesActivas());
        if (!soloCentrales && proyectoId != null) {
            bases.add(baseInsumosService.asegurarBaseProyecto(proyectoId));
        }
        if (bases.isEmpty()) {
            return Page.of(List.of(), 0, pageIndex, pageSize);
        }
        List<Long> baseIds = bases.stream().map(b -> b.id).toList();

        long corte = System.currentTimeMillis() - BaseInsumosService.DESACTUALIZADO_DAYS * 86400L * 1000;
        List<Insumo> pageItems = insumoRepository.listarDeBases(baseIds, q, pageIndex, pageSize);
        long total = insumoRepository.contarDeBases(baseIds, q);

        Map<Long, String> baseNombre = new HashMap<>();
        Map<Long, TipoBase> baseTipo = new HashMap<>();
        bases.forEach(b -> {
            baseNombre.put(b.id, b.nombre);
            baseTipo.put(b.id, b.tipo);
        });

        List<InsumoBusquedaResponse> items = pageItems.stream().map(i -> {
            boolean esCentral = baseTipo.get(i.baseId) == TipoBase.CENTRAL;
            return new InsumoBusquedaResponse(i.id, i.codigo, i.tipo, i.descripcion, i.unidad,
                    i.precioUnitario, i.updatedAt, desactualizado(i, corte),
                    esCentral ? "CENTRAL" : "PROYECTO", baseNombre.get(i.baseId));
        }).toList();

        return Page.of(items, total, pageIndex, pageSize);
    }

    private boolean desactualizado(Insumo i, long corte) {
        return i.updatedAt == null || i.updatedAt.toEpochMilli() < corte;
    }
}