package ec.uce.propuestas.apu.mapper;

import ec.uce.propuestas.apu.dto.ApuResponse;
import ec.uce.propuestas.apu.dto.ApuSeccionResponse;
import ec.uce.propuestas.apu.entity.Apu;
import java.math.BigDecimal;
import java.util.List;

public final class ApuMapper {

    private ApuMapper() {}

    /**
     * @param porcentajeIndirectoEfectivo COALESCE(apu.porcentajeIndirecto, proyecto) — DM §17 #17
     */
    public static ApuResponse toResponse(
            Apu e, BigDecimal porcentajeIndirectoEfectivo, List<ApuSeccionResponse> secciones) {
        return new ApuResponse(
                e.publicId,
                e.codigo,
                e.descripcion,
                e.unidad,
                e.costoDirecto,
                e.costoIndirecto,
                e.costoTotal,
                e.porcentajeIndirecto,
                porcentajeIndirectoEfectivo,
                e.porcentajeDescuento,
                secciones,
                null);
    }
}
