package ec.uce.propuestas.admin.dto;

import java.math.BigDecimal;

public record ParametrosSistemaActualizarRequest(
        BigDecimal porcentajeHerramientaMenor,
        BigDecimal porcentajeIndirecto,
        BigDecimal iva,
        String moneda,
        Boolean mostrarSeccionesVacias,
        Boolean sufijosSeccionActivos,
        Boolean mostrarSubtotalesSeccion,
        Boolean mostrarSubtotalesPie,
        Boolean mostrarNombreProyectoHeader,
        Boolean enumerarApus,
        String mensajeFooter,
        String modoCodigoRubro,
        BigDecimal rangoHmMin,
        BigDecimal rangoHmMax,
        BigDecimal rangoCiMin,
        BigDecimal rangoCiMax,
        BigDecimal rangoDescuentoMin,
        BigDecimal rangoDescuentoMax,
        BigDecimal rangoIvaMin,
        BigDecimal rangoIvaMax) {}
