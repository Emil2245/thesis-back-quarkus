package ec.uce.propuestas.proyecto.mapper;

import ec.uce.propuestas.proyecto.dto.ParametrosProyectoResponse;
import ec.uce.propuestas.proyecto.entity.ParametrosProyecto;
import ec.uce.propuestas.proyecto.entity.Proyecto;

public final class ParametrosProyectoMapper {

    private ParametrosProyectoMapper() {}

    /**
     * Plan 07 — el {@code proyectoId} público es el {@code publicId} UUIDv7 del
     * proyecto padre (caller pasa la entidad padre ya validada por owner).
     * El {@code BIGINT} interno nunca aparece en el JSON.
     */
    public static ParametrosProyectoResponse toResponse(ParametrosProyecto p, Proyecto proyectoPadre) {
        return new ParametrosProyectoResponse(
                proyectoPadre.publicId,
                p.porcentajeHerramientaMenor,
                p.porcentajeIndirecto,
                p.iva,
                p.moneda,
                p.mostrarSeccionesVacias,
                p.sufijosSeccionActivos,
                p.mostrarSubtotalesSeccion,
                p.mostrarSubtotalesPie,
                p.mostrarNombreProyectoHeader,
                p.enumerarApus,
                p.mensajeFooter,
                p.modoCodigoRubro);
    }

    /** Variante de compatibilidad — el caller ya conoce el {@code publicId}. */
    public static ParametrosProyectoResponse toResponse(ParametrosProyecto p, java.util.UUID proyectoPublicId) {
        return new ParametrosProyectoResponse(
                proyectoPublicId,
                p.porcentajeHerramientaMenor,
                p.porcentajeIndirecto,
                p.iva,
                p.moneda,
                p.mostrarSeccionesVacias,
                p.sufijosSeccionActivos,
                p.mostrarSubtotalesSeccion,
                p.mostrarSubtotalesPie,
                p.mostrarNombreProyectoHeader,
                p.enumerarApus,
                p.mensajeFooter,
                p.modoCodigoRubro);
    }
}
