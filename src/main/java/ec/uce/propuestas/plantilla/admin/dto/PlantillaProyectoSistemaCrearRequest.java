package ec.uce.propuestas.plantilla.admin.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import ec.uce.propuestas.common.ProblemaException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Plan 044 — body para crear una plantilla de proyecto SISTEMA desde un
 * proyecto existente. Mismo molde que {@link PlantillaSistemaCrearRequest}: el
 * snapshot lo construye el backend, el cliente nunca lo envía.
 */
public record PlantillaProyectoSistemaCrearRequest(
        @NotNull UUID desdeProyectoId,
        @NotBlank @Size(max = 200) String nombre,
        @Size(max = 2000) String descripcion) {

    @JsonAnySetter
    public void rechazarCampoDesconocido(String campo, Object valor) {
        throw ProblemaException.validacion("Campo no permitido: " + campo);
    }
}
