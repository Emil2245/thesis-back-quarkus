package ec.uce.propuestas.cronograma.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

/**
 * Plan 031 (P-37, audit closure) — cuerpo tipado de la respuesta HTTP
 * {@code 409 Conflict} cuando la exportación del cronograma está bloqueada. La
 * forma canónica conserva, byte-a-byte, los {@code bloqueos[]} y
 * {@code warnings[]} del MISMO snapshot que vio el writer, de modo que el cliente
 * pueda correlacionar el 409 con la respuesta de preflight sin reinterpretar
 * estado intermedio.
 *
 * <p>Reglas de diseño:
 *
 * <ul>
 *   <li>El campo {@code codigo} siempre es {@code export-bloqueado} — preserva
 *       el contrato del catálogo de tipos del módulo {@code documento}.</li>
 *   <li>El campo {@code mensaje} resume el conteo de bloqueos para humanos
 *       (ej. «3 bloqueo(s)»); NO se calcula nada nuevo ni se omite
 *       información.</li>
 *   <li>Los arreglos {@code bloqueos[]} y {@code warnings[]} nunca son null;
 *       siempre se devuelve la lista (posiblemente vacía) capturada en el
 *       snapshot congelado por el lock pesimista del presupuesto.</li>
 *   <li>{@code presupuestoId} lleva el UUIDv7 del presupuesto que se intentó
 *       exportar (no la fila interna BIGINT).</li>
 *   <li>{@code formato} lleva el token del formato solicitado
 *       ({@code xlsx|pdf|mspdi}) para correlación con preflight.</li>
 * </ul>
 *
 * <p>Esta clase es inmutable y se serializa con Jackson 2.x; el módulo la
 * construye exclusivamente desde
 * {@link ec.uce.propuestas.cronograma.export.CronogramaDescargaService} y la
 * entrega al resource REST para que la respuesta NO se reconstruya a partir de
 * entidades gestionadas (regla TOCTOU).</p>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record BloqueoExportDetalle(
        UUID presupuestoId,
        String formato,
        String codigo,
        String mensaje,
        List<BloqueoExportResponse> bloqueos,
        List<WarningExportResponse> warnings) {

    /**
     * Constructor de conveniencia que clona las listas (Jackson NO debe mutar
     * las colecciones del snapshot que el service conserva fuera de la
     * transacción).
     */
    public BloqueoExportDetalle {
        bloqueos = bloqueos == null ? List.of() : List.copyOf(bloqueos);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
