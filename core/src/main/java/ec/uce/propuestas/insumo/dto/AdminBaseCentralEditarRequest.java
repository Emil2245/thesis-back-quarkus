package ec.uce.propuestas.insumo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Plan 05 — Solicitud para renombrar una base CENTRAL (P-39, D-12). Solo se
 * admite el nombre: tipo, archivada y métricas los mantiene el servidor.
 */
public record AdminBaseCentralEditarRequest(
        @NotBlank @Size(max = 200) String nombre) {}
