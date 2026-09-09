package ec.uce.propuestas.cronograma.dto;

/**
 * Plan 028 (P-33) — body de {@code POST /presupuestos/{presupuestoId}/cronograma}.
 *
 * <p>Forma canónica de 026/07-api-contract §7:
 * {@code { "unidadTiempo": "SEMANA|MES", "numeroPeriodos": 12 }}. El body NO
 * acepta actividades, {@code rubroId}, pesos ni identidades del cliente: la
 * autoimportación las deriva de los rubros de la versión. Las propiedades
 * desconocidas se rechazan con 400 {@code validacion} en la frontera
 * ({@code CronogramaRequestParser}).</p>
 */
public record CronogramaCrearRequest(String unidadTiempo, int numeroPeriodos) {}
