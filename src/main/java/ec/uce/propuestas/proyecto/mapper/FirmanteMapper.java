package ec.uce.propuestas.proyecto.mapper;

import ec.uce.propuestas.proyecto.dto.FirmanteResponse;
import ec.uce.propuestas.proyecto.entity.Firmante;

public final class FirmanteMapper {

    private FirmanteMapper() {}

    public static FirmanteResponse toResponse(Firmante f) {
        return new FirmanteResponse(f.id, f.nombre, f.cargo, f.rol, f.orden);
    }
}
