package ec.uce.propuestas.presupuesto.dto;

import java.math.BigDecimal;
import java.util.List;

public record ComparacionVersionesResponse(List<PresupuestoComparacionItem> versiones) {

    public record PresupuestoComparacionItem(
            Long presupuestoId,
            Short version,
            BigDecimal totalGeneral,
            List<CapituloComparacionItem> porCapituloRaiz) {}

    public record CapituloComparacionItem(String item, String descripcion, BigDecimal total) {}
}
