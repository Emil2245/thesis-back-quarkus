package ec.uce.propuestas.apu.mapper;

import ec.uce.propuestas.apu.dto.ApuResumenResponse;
import ec.uce.propuestas.apu.entity.Apu;

public final class ApuResumenMapper {

    private ApuResumenMapper() {}

    public static ApuResumenResponse toResponse(Apu e, boolean vinculado) {
        return new ApuResumenResponse(
                e.publicId, e.codigo, e.descripcion, e.unidad, e.costoDirecto, e.costoTotal, vinculado);
    }
}
