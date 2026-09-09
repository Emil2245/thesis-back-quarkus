package ec.uce.propuestas.proyecto.mapper;

import ec.uce.propuestas.proyecto.dto.FirmanteResponse;
import ec.uce.propuestas.proyecto.entity.Firmante;

public final class FirmanteMapper {

    private FirmanteMapper() {}

    /**
     * Plan 07 — el {@code id} público es el {@code publicId} UUIDv7 de la fila;
     * el {@code BIGINT} interno nunca aparece en el JSON.
     */
    public static FirmanteResponse toResponse(Firmante f) {
        return new FirmanteResponse(f.publicId, f.nombre, f.cargo, f.rol, f.orden);
    }
}
