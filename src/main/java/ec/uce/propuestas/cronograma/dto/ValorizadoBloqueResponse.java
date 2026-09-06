package ec.uce.propuestas.cronograma.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Plan 030 (P-35/P-36) — bloque Cronograma Valorizado dentro de la
 * respuesta canónica {@link CronogramaVistasResponse}. Define exactamente la
 * forma del canon 07-api-contract §7 Apéndice B:
 *
 * <pre>
 * "valorizado": { "periodos": [ PeriodoValorizadoResponse ],
 *                 "capitulos": [ CapituloCronogramaResponse ],
 *                 "totales": TotalesCronogramaResponse }
 * </pre>
 *
 * <p>Reglas deterministas por período (escala 4 porcentajes, escala 6 dinero):
 * <ul>
 *   <li>{@code porcentajeParcial} = Σ avance_i,t sobre todas las actividades.</li>
 *   <li>{@code porcentajeAcumulado} = Σ porcentajeParcial 1..t (no se repite
 *       el parcial, contraviniendo el error visual del PDF aceptado).</li>
 *   <li>{@code montoParcial} = Σ monto_actividad_periodo_i,t.</li>
 *   <li>{@code montoAcumulado} = Σ montoParcial 1..t.</li>
 * </ul>
 *
 * <p>La jerarquía recursiva de capítulos/rubros/actividades se expone además
 * como {@code capitulos[]} para que el cliente pueda correlacionar fila↔período
 * y ver el monto por actividad/rubro en contexto, sin pedir otra vista.</p>
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ValorizadoBloqueResponse(
        List<PeriodoValorizadoResponse> periodos,
        List<CapituloCronogramaResponse> capitulos,
        TotalesCronogramaResponse totales) {}
