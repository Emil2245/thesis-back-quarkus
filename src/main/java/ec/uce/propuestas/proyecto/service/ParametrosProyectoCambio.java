package ec.uce.propuestas.proyecto.service;

import ec.uce.propuestas.proyecto.dto.ParametrosProyectoResponse;
import java.util.UUID;

/**
 * WU-06 — Seam neutro de cambio de parámetros de un proyecto.
 *
 * <p>Plan 07 — {@code proyectoId} es la identidad externa inmutable UUIDv7 del
 * proyecto. Transporta los flags de cambio numérico de %HM y %CI
 * (escala-insensibles y null-safe) y la respuesta ya materializada. Es
 * deliberadamente neutro: no importa tipos de recálculo ni dispara eventos del
 * framework. Será la entrada del WU-07 cuando se conecte el recálculo real.</p>
 */
public record ParametrosProyectoCambio(
        UUID proyectoId,
        boolean porcentajeIndirectoCambio,
        boolean porcentajeHerramientaMenorCambio,
        ParametrosProyectoResponse parametros) {}
