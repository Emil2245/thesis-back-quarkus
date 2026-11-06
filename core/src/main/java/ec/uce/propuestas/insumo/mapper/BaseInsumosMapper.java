package ec.uce.propuestas.insumo.mapper;

import ec.uce.propuestas.insumo.dto.BaseInsumosResponse;
import ec.uce.propuestas.insumo.entity.BaseInsumos;

public final class BaseInsumosMapper {

    private BaseInsumosMapper() {}

    /**
     * Plan 07 — el {@code id} público es el {@code publicId} UUIDv7; nunca el
     * {@code BIGINT} interno.
     */
    public static BaseInsumosResponse toResponse(BaseInsumos e, long totalInsumos) {
        return new BaseInsumosResponse(e.publicId, e.nombre, e.tipo.name(), e.archivada, totalInsumos);
    }
}
