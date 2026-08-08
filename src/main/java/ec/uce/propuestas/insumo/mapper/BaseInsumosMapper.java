package ec.uce.propuestas.insumo.mapper;

import ec.uce.propuestas.insumo.dto.BaseInsumosResponse;
import ec.uce.propuestas.insumo.entity.BaseInsumos;

public final class BaseInsumosMapper {

    private BaseInsumosMapper() {
    }

    public static BaseInsumosResponse toResponse(BaseInsumos e, long totalInsumos) {
        return new BaseInsumosResponse(e.id, e.nombre, e.tipo.name(), e.archivada, totalInsumos);
    }
}