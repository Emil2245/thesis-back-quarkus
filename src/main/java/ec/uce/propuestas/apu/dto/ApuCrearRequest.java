package ec.uce.propuestas.apu.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Plan 04 (P-26) — Crea un APU desde cero o desde una plantilla:
 * <ul>
 *   <li>Sin {@code plantillaId} (o null): creación normal (camino feliz 201).</li>
 *   <li>Con {@code plantillaId}: precarga el snapshot, resuelve cada
 *       {@code insumoCodigo} contra la base del proyecto con fallback
 *       (DM §12, dossier 07 §B.4). Si hay códigos no resueltos el endpoint
 *       responde HTTP 200 con {@code advertencias[]} poblado (N04 §B.4).
 *       Si todo se resuelve, sigue siendo HTTP 201.</li>
 * </ul>
 * {@link JsonInclude.Include#NON_NULL} omite {@code plantillaId} cuando no
 * viene para preservar la forma del contrato existente en el caso normal.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApuCrearRequest(
        @Size(max = 20) String codigo,
        @NotBlank String descripcion,
        @NotBlank @Size(max = 10) String unidad,
        UUID plantillaId) {

    public boolean tienePlantilla() {
        return plantillaId != null;
    }

    /** Constructor de compatibilidad para los tests existentes (sin plantilla). */
    public ApuCrearRequest(String codigo, String descripcion, String unidad) {
        this(codigo, descripcion, unidad, null);
    }
}
