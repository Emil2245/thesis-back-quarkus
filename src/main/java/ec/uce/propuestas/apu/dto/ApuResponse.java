package ec.uce.propuestas.apu.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import ec.uce.propuestas.plantilla.dto.AdvertenciaPlantillaResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Detalle completo de un APU (07-api-contract.md Apéndice B). Plan 04 (P-26) —
 * {@code advertencias} sólo se rellena en la respuesta del endpoint
 * {@code POST /presupuestos/{id}/apus} cuando la creación carga una plantilla
 * con códigos no resueltos; el resto de endpoints la omiten (forma estable
 * para los consumidores existentes). {@code @JsonInclude(NON_NULL)} evita
 * que el campo aparezca cuando no aplica.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApuResponse(
        UUID id,
        String codigo,
        String descripcion,
        String unidad,
        BigDecimal costoDirecto,
        BigDecimal costoIndirecto,
        BigDecimal costoTotal,
        BigDecimal porcentajeIndirecto,
        BigDecimal porcentajeIndirectoEfectivo,
        BigDecimal porcentajeDescuento,
        List<ApuSeccionResponse> secciones,
        List<AdvertenciaPlantillaResponse> advertencias) {}