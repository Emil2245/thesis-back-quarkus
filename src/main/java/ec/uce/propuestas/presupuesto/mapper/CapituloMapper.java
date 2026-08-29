package ec.uce.propuestas.presupuesto.mapper;

import ec.uce.propuestas.presupuesto.dto.CapituloResponse;
import ec.uce.propuestas.presupuesto.dto.RubroResponse;
import ec.uce.propuestas.presupuesto.entity.Capitulo;
import java.util.List;

public final class CapituloMapper {

    private CapituloMapper() {}

    public static CapituloResponse toResponse(
            Capitulo c, List<CapituloResponse> subcapitulos, List<RubroResponse> rubros) {
        return new CapituloResponse(c.id, c.item, c.descripcion, c.orden, c.total, subcapitulos, rubros);
    }
}
