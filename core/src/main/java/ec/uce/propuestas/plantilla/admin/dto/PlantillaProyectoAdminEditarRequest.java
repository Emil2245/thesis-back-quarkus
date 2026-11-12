package ec.uce.propuestas.plantilla.admin.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import ec.uce.propuestas.common.ProblemaException;
import org.openapitools.jackson.nullable.JsonNullable;

/**
 * Plan 044 — edición administrativa limitada a metadatos de una plantilla de
 * proyecto SISTEMA. Semántica de presencia, como {@link PlantillaApuAdminEditarRequest}.
 */
public record PlantillaProyectoAdminEditarRequest(JsonNullable<String> nombre, JsonNullable<String> descripcion) {

    public boolean nombrePresente() {
        return nombre != null && nombre.isPresent();
    }

    public String nombreOrNull() {
        return nombrePresente() ? nombre.get() : null;
    }

    public boolean descripcionPresente() {
        return descripcion != null && descripcion.isPresent();
    }

    public String descripcionOrNull() {
        return descripcionPresente() ? descripcion.get() : null;
    }

    @JsonAnySetter
    public void rechazarCampoDesconocido(String campo, Object valor) {
        throw ProblemaException.validacion("Campo no permitido: " + campo);
    }
}
