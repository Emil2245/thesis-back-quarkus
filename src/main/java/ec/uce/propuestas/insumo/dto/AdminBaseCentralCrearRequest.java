package ec.uce.propuestas.insumo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Plan 05 — Solicitud para crear una base CENTRAL (P-39). El cliente solo
 * elige el nombre: tipo, dueño y proyecto padre los fija el servicio a
 * {@code CENTRAL / null / null} respectivamente.
 */
public record AdminBaseCentralCrearRequest(
        @NotBlank @Size(max = 200) String nombre) {}
