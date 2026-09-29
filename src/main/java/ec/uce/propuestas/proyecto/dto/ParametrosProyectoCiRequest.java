package ec.uce.propuestas.proyecto.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** Request for the project CI rate and the individual-APU opt-in. */
public record ParametrosProyectoCiRequest(
        @Digits(integer = 1, fraction = 4) BigDecimal porcentajeIndirecto,
        @NotNull Boolean ciIndividualHabilitado,
        PoliticaOverrides politicaOverrides) {

    public enum PoliticaOverrides {
        PRESERVAR,
        RESTABLECER
    }
}
