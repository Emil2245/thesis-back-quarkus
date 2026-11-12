package ec.uce.propuestas.presupuesto.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import ec.uce.propuestas.apu.dto.ApuDetalleCrearRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Request for creating an APU header, its editable rows, and its budget item atomically. */
@JsonDeserialize(using = ApuManualCompletoRequestDeserializer.class)
public record ApuManualCompletoRequest(
        @Size(max = 20) String codigo,
        @NotBlank @Size(max = 255) String descripcion,
        @NotBlank @Size(max = 10) String unidad,
        @DecimalMin("0.0000") @DecimalMax("1.0000") BigDecimal porcentajeIndirecto,
        UUID capituloId,
        @NotNull @Size(min = 1, max = 200) List<@Valid ApuDetalleCrearRequest> detalles) {}
