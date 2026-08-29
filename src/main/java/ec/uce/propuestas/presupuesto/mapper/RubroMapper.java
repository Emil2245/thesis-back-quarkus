package ec.uce.propuestas.presupuesto.mapper;

import ec.uce.propuestas.presupuesto.dto.RubroResponse;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class RubroMapper {

    private RubroMapper() {}

    public static RubroResponse toResponse(Rubro r) {
        List<String> alertas = new ArrayList<>();
        if (r.precioUnitario == null || r.precioUnitario.compareTo(BigDecimal.ZERO) == 0) {
            alertas.add("PU_CERO");
        }
        if (r.cantidad == null || r.cantidad.compareTo(BigDecimal.ZERO) == 0) {
            alertas.add("CANTIDAD_CERO");
        }
        return new RubroResponse(
                r.id,
                r.item,
                r.codigo,
                r.descripcion,
                r.unidad,
                r.cantidad,
                r.precioUnitario,
                r.precioTotal,
                r.apuId,
                alertas);
    }
}
