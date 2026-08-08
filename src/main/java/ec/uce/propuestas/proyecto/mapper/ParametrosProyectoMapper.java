package ec.uce.propuestas.proyecto.mapper;

import ec.uce.propuestas.proyecto.dto.ParametrosProyectoResponse;
import ec.uce.propuestas.proyecto.entity.ParametrosProyecto;

public final class ParametrosProyectoMapper {

    private ParametrosProyectoMapper() {
    }

    public static ParametrosProyectoResponse toResponse(ParametrosProyecto p) {
        return new ParametrosProyectoResponse(
                p.proyectoId, p.porcentajeHerramientaMenor, p.porcentajeIndirecto, p.iva, p.moneda,
                p.mostrarSeccionesVacias, p.sufijosSeccionActivos, p.mostrarSubtotalesSeccion,
                p.mostrarSubtotalesPie, p.mostrarNombreProyectoHeader, p.enumerarApus,
                p.mensajeFooter, p.modoCodigoRubro);
    }
}