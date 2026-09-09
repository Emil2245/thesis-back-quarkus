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
 *
 * <p>Plan 015 (P-24/S-24 withdrawn): el campo {@code porcentajeDescuento} se
 * retira del contrato JSON. La columna BD {@code apu.porcentaje_descuento}
 * queda como compatibility seam inert (V001 sin cambios; JPA la ignora).
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
        List<ApuSeccionResponse> secciones,
        List<AdvertenciaPlantillaResponse> advertencias) {}
