package ec.uce.propuestas.plantilla.dto;

import ec.uce.propuestas.common.ProblemaException;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * Plan 04 (P-26) — Body de {@code PUT /plantillas-apu/{id}}. Plan 04 §7 —
 * solo se editan metadatos (nombre, descripcionRubro). El snapshot, la
 * unidad, el tipo y el dueño son inmutables. {@code JsonNullable} permite
 * distinguir campo omitido (no tocar) vs presente (setear) en línea con la
 * política PATCH de {@code apu} (módulo apu, WU-03 + Plan 014).
 */
public record PlantillaApuEditarRequest(JsonNullable<String> nombre, JsonNullable<String> descripcionRubro) {

    public String nombreOrNull() {
        return nombre != null && nombre.isPresent() ? nombre.get() : null;
    }

    public String descripcionOrNull() {
        return descripcionRubro != null && descripcionRubro.isPresent() ? descripcionRubro.get() : null;
    }

    public boolean nombrePresente() {
        return nombre != null && nombre.isPresent();
    }

    public boolean descripcionPresente() {
        return descripcionRubro != null && descripcionRubro.isPresent();
    }

    /**
     * Valida el cuerpo siguiendo el contrato PUT (Plan 04 §7). Reglas:
     * <ul>
     *   <li>Cuerpo obligatorio.</li>
     *   <li>{@code nombre} presente (no omitido) → no-blanco y ≤ 200 chars.</li>
     *   <li>{@code descripcionRubro} presente → null o ≤ 500 chars.</li>
     * </ul>
     */
    public void validar() {
        if (nombre == null && descripcionRubro == null) {
            throw ProblemaException.validacion("Debe enviar al menos un campo a editar");
        }
        if (nombrePresente()) {
            String n = nombreOrNull();
            if (n == null || n.isBlank()) {
                throw ProblemaException.validacion("nombre no puede estar vacío");
            }
            if (n.length() > 200) {
                throw ProblemaException.validacion("nombre excede 200 caracteres");
            }
        }
        if (descripcionPresente()) {
            String d = descripcionOrNull();
            if (d != null && d.length() > 500) {
                throw ProblemaException.validacion("descripcionRubro excede 500 caracteres");
            }
        }
    }
}
