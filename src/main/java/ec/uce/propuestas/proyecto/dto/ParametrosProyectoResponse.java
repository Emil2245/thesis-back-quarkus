package ec.uce.propuestas.proyecto.dto;

import ec.uce.propuestas.proyecto.entity.ModoCodigoRubro;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Parámetros de cálculo de un proyecto (07-api-contract.md §3).
 *
 * <p>Plan 07 — {@code proyectoId} es la identidad externa inmutable UUIDv7 del
 * proyecto (columna {@code public_id}); nunca el {@code id} interno BIGINT.</p>
 */
public record ParametrosProyectoResponse(
        UUID proyectoId,
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
        ModoCodigoRubro modoCodigoRubro) {}
