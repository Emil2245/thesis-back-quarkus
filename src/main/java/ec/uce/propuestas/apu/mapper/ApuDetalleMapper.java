package ec.uce.propuestas.apu.mapper;

import ec.uce.propuestas.apu.dto.ApuDetalleResponse;
import ec.uce.propuestas.apu.entity.ApuDetalle;
import java.math.BigDecimal;

public final class ApuDetalleMapper {

    private ApuDetalleMapper() {}

    /**
     * @param precioEfectivo precio resuelto: COALESCE(override, Insumo.precioUnitario)
     * @param precioHeredado true si no hay override manual (null-means-inherit, DM §8)
     */
    public static ApuDetalleResponse toResponse(ApuDetalle d, BigDecimal precioEfectivo, boolean precioHeredado) {
        return new ApuDetalleResponse(
                d.id,
                d.orden,
                d.descripcion,
                d.esHerramientaMenor,
                d.insumoId,
                d.apuAuxiliarId,
                d.cantidad,
                d.rendimiento,
                d.unidad,
                precioEfectivo,
                precioHeredado,
                d.costoHora,
                d.costo);
    }
}
