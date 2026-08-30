package ec.uce.propuestas.insumo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * WU-05 — Solicitud para crear una base PERSONAL del usuario autenticado.
 *
 * <p>El caller NO puede elegir el dueño, el tipo, el proyecto padre ni el
 * {@code publicId}: esos campos los fija el servicio a partir del JWT
 * (RNF-05 / A9). Solo se acepta el nombre, requerido para identificar la
 * base en el catálogo personal.</p>
 */
public record BasePersonalCrearRequest(
        @NotBlank @Size(max = 200) String nombre) {}
