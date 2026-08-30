package ec.uce.propuestas.apu.mapper;

import ec.uce.propuestas.apu.dto.ApuDetalleResponse;
import ec.uce.propuestas.apu.entity.ApuDetalle;
import ec.uce.propuestas.insumo.entity.Insumo;
import java.math.BigDecimal;

public final class ApuDetalleMapper {

    private ApuDetalleMapper() {}

    /**
     * Plan 07 — el {@code insumoId} público es el {@code publicId} UUIDv7 del
     * insumo. Si la fila no tiene insumo (HM o pendiente), se omite el campo
     * pasando {@code null}. El {@code BIGINT} interno nunca aparece en el JSON.
     *
     * @param precioEfectivo precio resuelto: COALESCE(override, Insumo.precioUnitario)
     * @param precioHeredado true si no hay override manual (null-means-inherit, DM §8)
     */
    public static ApuDetalleResponse toResponse(
            ApuDetalle d, Insumo insumo, BigDecimal precioEfectivo, boolean precioHeredado) {
        java.util.UUID insumoPublicId = insumo == null ? null : insumo.publicId;
        return new ApuDetalleResponse(
                d.publicId,
                d.orden,
                d.descripcion,
                d.esHerramientaMenor,
                insumoPublicId,
                d.cantidad,
                d.rendimiento,
                d.unidad,
                precioEfectivo,
                precioHeredado,
                d.costoHora,
                d.costo);
    }

    /** Variante de compatibilidad: el caller ya conoce el insumo resuelto. */
    public static ApuDetalleResponse toResponse(ApuDetalle d, BigDecimal precioEfectivo, boolean precioHeredado) {
        return new ApuDetalleResponse(
                d.publicId,
                d.orden,
                d.descripcion,
                d.esHerramientaMenor,
                null,
                d.cantidad,
                d.rendimiento,
                d.unidad,
                precioEfectivo,
                precioHeredado,
                d.costoHora,
                d.costo);
    }
}
