package ec.uce.propuestas.plantilla.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Plan 04 (P-26) — Advertencia reportada al cargar una plantilla. Forma:
 * <pre>
 * { "insumoCodigo": "MO-099", "motivo": "no-existe-en-base-proyecto", "mensaje": "..." }
 * </pre>
 * El {@code insumoCodigo} viene del snapshot JSONB; {@code motivo} es el
 * código de máquina del catálogo; {@code mensaje} es una guía accionable
 * para el usuario (Plan 04 §9 — "completar el precio o agregar el insumo").
 *
 * <p>{@link JsonInclude#NON_NULL} omite campos nulos para que las advertencias
 * tengan una forma estable en todas las rutas donde aparecen (lista, detalle,
 * creación desde plantilla).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdvertenciaPlantillaResponse(String insumoCodigo, String motivo, String mensaje) {

    public static final String MOTIVO_NO_EXISTE = "no-existe-en-base-proyecto";

    public static final String MENSAJE_NO_EXISTE =
            "Completar el precio o agregar el insumo correspondiente en la base del proyecto";

    public static AdvertenciaPlantillaResponse noExiste(String insumoCodigo) {
        return new AdvertenciaPlantillaResponse(insumoCodigo, MOTIVO_NO_EXISTE, MENSAJE_NO_EXISTE);
    }
}
