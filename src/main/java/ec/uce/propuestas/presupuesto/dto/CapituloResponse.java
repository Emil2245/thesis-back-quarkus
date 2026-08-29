package ec.uce.propuestas.presupuesto.dto;

import java.math.BigDecimal;
import java.util.List;

public record CapituloResponse(
        Long id,
        String item,
        String descripcion,
        Short orden,
        BigDecimal total,
        List<CapituloResponse> subcapitulos,
        List<RubroResponse> rubros) {}
