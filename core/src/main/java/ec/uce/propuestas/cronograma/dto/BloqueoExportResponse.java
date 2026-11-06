package ec.uce.propuestas.cronograma.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

/**
 * Plan 031 (P-37) — un bloqueo individual en la respuesta de preflight del
 * cronograma. Códigos canónicos:
 *
 * <ul>
 *   <li>{@code cronograma-borrador}: estado BORRADOR (desviación != 0 o avance != 100).</li>
 *   <li>{@code cronograma-desviacion}: alguna actividad con desviación != 0.0000.</li>
 *   <li>{@code cronograma-avance-final}: avance global final != 100.0000.</li>
 *   <li>{@code cronograma-total-cero}: totalGeneral == 0 (presupuesto vacío / cero).</li>
 *   <li>{@code mspdi-fecha-inicio-requerida}: MSPDI exige {@code Proyecto.fechaInicio}.</li>
 *   <li>{@code presupuesto-pu-cero}: rubro con PU = 0 (P-32, preservado por separado).</li>
 *   <li>{@code presupuesto-cantidad-cero}: rubro con cantidad = 0 (P-32, preservado).</li>
 *   <li>{@code presupuesto-sin-actividad}: rubro sin actividad (P-32, preservado).</li>
 * </ul>
 *
 * <p>El campo {@code actividadId} se incluye opcionalmente para los bloqueos
 * derivados de una actividad específica; no expone el BIGINT interno — sólo
 * UUIDv7.</p>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record BloqueoExportResponse(String codigo, UUID actividadId, String detalle) {

    public static BloqueoExportResponse deCodigo(String codigo, String detalle) {
        return new BloqueoExportResponse(codigo, null, detalle);
    }
}
