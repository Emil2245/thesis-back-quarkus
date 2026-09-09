package ec.uce.propuestas.plantilla.admin.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import ec.uce.propuestas.common.ProblemaException;
import org.openapitools.jackson.nullable.JsonNullable;

/** Edición administrativa limitada a metadatos de una plantilla SISTEMA. */
public record PlantillaApuAdminEditarRequest(JsonNullable<String> nombre, JsonNullable<String> descripcionRubro) {

    public boolean nombrePresente() {
        return nombre != null && nombre.isPresent();
    }

    public String nombreOrNull() {
        return nombrePresente() ? nombre.get() : null;
    }

    public boolean descripcionPresente() {
        return descripcionRubro != null && descripcionRubro.isPresent();
    }

    public String descripcionOrNull() {
        return descripcionPresente() ? descripcionRubro.get() : null;
    }

    @JsonAnySetter
    public void rechazarCampoDesconocido(String campo, Object valor) {
        throw ProblemaException.validacion("Campo no permitido: " + campo);
    }
}
