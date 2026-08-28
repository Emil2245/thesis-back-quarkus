package ec.uce.propuestas.proyecto.service;

import ec.uce.propuestas.proyecto.dto.ParametrosProyectoResponse;

/**
 * WU-06 — Seam neutro de cambio de parámetros de un proyecto.
 *
 * <p>Transporta el identificador interno del proyecto, los flags de cambio
 * numérico de %HM y %CI (escala-insensibles y null-safe) y la respuesta ya
 * materializada. Es deliberadamente neutro: no importa tipos de recálculo ni
 * dispara eventos del framework. Será la entrada del WU-07 cuando se conecte
 * el recálculo real.
 */
public record ParametrosProyectoCambio(
        Long proyectoId,
        boolean porcentajeIndirectoCambio,
        boolean porcentajeHerramientaMenorCambio,
        ParametrosProyectoResponse parametros) {}
