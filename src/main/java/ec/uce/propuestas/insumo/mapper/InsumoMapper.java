package ec.uce.propuestas.insumo.mapper;

import ec.uce.propuestas.insumo.dto.InsumoResponse;
import ec.uce.propuestas.insumo.entity.Insumo;
import java.time.Instant;

public final class InsumoMapper {

    private static final long DESACTUALIZADO_DAYS = 90;

    private InsumoMapper() {}

    public static InsumoResponse toResponse(Insumo e) {
        boolean desactualizado =
                e.updatedAt == null || e.updatedAt.isBefore(Instant.now().minusSeconds(DESACTUALIZADO_DAYS * 86400L));
        return new InsumoResponse(
                e.id, e.codigo, e.tipo, e.descripcion, e.unidad, e.precioUnitario, e.updatedAt, desactualizado);
    }
}
