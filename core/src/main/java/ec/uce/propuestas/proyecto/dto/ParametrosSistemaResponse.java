package ec.uce.propuestas.proyecto.dto;

import ec.uce.propuestas.proyecto.entity.ModoCodigoRubro;
import ec.uce.propuestas.proyecto.entity.ParametrosSistema;
import java.math.BigDecimal;
import java.time.Instant;

/** Representación REST completa del singleton de parámetros del sistema. */
public record ParametrosSistemaResponse(
        Short id,
        BigDecimal porcentajeHerramientaMenor,
        BigDecimal porcentajeIndirecto,
        BigDecimal iva,
        BigDecimal rangoHmMin,
        BigDecimal rangoHmMax,
        BigDecimal rangoCiMin,
        BigDecimal rangoCiMax,
        BigDecimal rangoDescuentoMin,
        BigDecimal rangoDescuentoMax,
        BigDecimal rangoIvaMin,
        BigDecimal rangoIvaMax,
        String moneda,
        boolean mostrarSeccionesVacias,
        boolean sufijosSeccionActivos,
        boolean mostrarSubtotalesSeccion,
        boolean mostrarSubtotalesPie,
        boolean mostrarNombreProyectoHeader,
        boolean enumerarApus,
        String mensajeFooter,
        ModoCodigoRubro modoCodigoRubro,
        Instant updatedAt) {

    public static ParametrosSistemaResponse from(ParametrosSistema p) {
        return new ParametrosSistemaResponse(
                p.id,
                p.porcentajeHerramientaMenor,
                p.porcentajeIndirecto,
                p.iva,
                p.rangoHmMin,
                p.rangoHmMax,
                p.rangoCiMin,
                p.rangoCiMax,
                p.rangoDescuentoMin,
                p.rangoDescuentoMax,
                p.rangoIvaMin,
                p.rangoIvaMax,
                p.moneda,
                p.mostrarSeccionesVacias,
                p.sufijosSeccionActivos,
                p.mostrarSubtotalesSeccion,
                p.mostrarSubtotalesPie,
                p.mostrarNombreProyectoHeader,
                p.enumerarApus,
                p.mensajeFooter,
                p.modoCodigoRubro,
                p.updatedAt);
    }
}
