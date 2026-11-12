package ec.uce.propuestas.presupuesto.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record PlantillaLoteRequest(
        UUID capituloId, @NotEmpty @Size(min = 1, max = 20) List<UUID> plantillaIds) {}
