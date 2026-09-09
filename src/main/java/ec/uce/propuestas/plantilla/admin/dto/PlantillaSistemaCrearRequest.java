package ec.uce.propuestas.plantilla.admin.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import ec.uce.propuestas.common.ProblemaException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Body canónico para crear una plantilla SISTEMA desde un APU existente. */
public record PlantillaSistemaCrearRequest(
        @NotNull UUID desdeApuId, @NotBlank @Size(max = 200) String nombre, String descripcionRubro) {

    @JsonAnySetter
    public void rechazarCampoDesconocido(String campo, Object valor) {
        throw ProblemaException.validacion("Campo no permitido: " + campo);
    }
}
