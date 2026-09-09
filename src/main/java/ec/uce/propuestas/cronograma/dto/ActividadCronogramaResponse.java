package ec.uce.propuestas.cronograma.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Proyección de solo lectura de una actividad y su rubro. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ActividadCronogramaResponse(
        UUID id,
        UUID rubroId,
        String item,
        String codigo,
        String descripcion,
        String unidad,
        String cantidad,
        String precioUnitario,
        String precioTotal,
        String pesoPonderado,
        Map<String, String> avancePorPeriodo,
        List<SegmentoResponse> segmentos,
        String desviacion) {}
