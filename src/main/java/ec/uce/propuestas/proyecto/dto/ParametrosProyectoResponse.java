package ec.uce.propuestas.proyecto.dto;

import ec.uce.propuestas.proyecto.entity.ModoCodigoRubro;

import java.math.BigDecimal;

/** Parámetros de cálculo de un proyecto (07-api-contract.md §3). */
public record ParametrosProyectoResponse(
        Long proyectoId,
        BigDecimal porcentajeHerramientaMenor,
        BigDecimal porcentajeIndirecto,
        BigDecimal iva,
        String moneda,
        boolean mostrarSeccionesVacias,
        boolean sufijosSeccionActivos,
        boolean mostrarSubtotalesSeccion,
        boolean mostrarSubtotalesPie,
        boolean mostrarNombreProyectoHeader,
        boolean enumerarApus,
        String mensajeFooter,
        ModoCodigoRubro modoCodigoRubro
) {}