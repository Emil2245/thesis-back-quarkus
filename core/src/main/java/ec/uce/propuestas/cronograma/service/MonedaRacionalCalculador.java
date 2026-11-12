package ec.uce.propuestas.cronograma.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Plan 030 (P-35/P-36) — algoritmo canónico racional entero para convertir
 * el avance global de una actividad en su distribución monetaria por período
 * (DM §16, fila {@code Monto_actividad_periodo_i,t}).
 *
 * <p>Convenciones de unidades (escala explícita, nunca {@code double}/{@code float}):
 * <ul>
 *   <li>{@code precioUnits}: dinero a 10⁻⁶ (entero, {@code BigInteger}).
 *       Rango válido: hasta {@code NUMERIC(14,6)} del DDL V001 ⇒ hasta
 *       {@code 9_999_999_999 × 10⁶ = 9.9999×10¹⁵}.</li>
 *   <li>{@code avanceUnits_t}: avance por período a 10⁻⁴ (entero).
 *       Rango válido: hasta {@code NUMERIC(7,4)} ⇒ hasta {@code 9_999_999}.</li>
 *   <li>{@code pesoUnits}: peso ponderado de la actividad a 10⁻⁴ (entero).
 *       Mismo rango que el avance.</li>
 *   <li>Resultado: dinero a 10⁻⁶ (entero), expuesto como string decimal a
 *       escala 6 con HALF_UP.</li>
 * </ul>
 *
 * <p>Por qué {@link BigInteger} y no {@code long}:
 * el peor caso válido es {@code precioUnits × ΣavanceUnits} que alcanza
 * {@code ~10¹⁶ × ~10⁸ = ~10²⁴}, mucho más allá de {@code Long.MAX_VALUE
 * ≈ 9.22×10¹⁸}. {@code BigInteger} evita el overflow y conserva el
 * redondeo HALF_UP exacto.
 *
 * <p>Reglas (cierre textual del canon):
 * <ul>
 *   <li><b>Peso &gt; 0:</b> {@code targetUnits = HALF_UP(precioUnits ×
 *       ΣavanceUnits / pesoUnits)}. Cada base de período es
 *       {@code floor(precioUnits × avanceUnits_t / pesoUnits)}. Las unidades
 *       faltantes hasta {@code targetUnits} se asignan por mayor residuo
 *       fraccionario descendente, con desempate por período ascendente.</li>
 *   <li><b>Peso = 0, precio &gt; 0:</b> se reparten
 *       {@code precioUnits} uniformemente entre las claves activas, con
 *       residual al último período activo (no al período n).</li>
 *   <li><b>Peso = 0, precio &gt; 0, sin claves activas:</b> distribución
 *       vacía y total 0 (la actividad queda en borrador).</li>
 *   <li><b>Precio = 0:</b> toda distribución es {@code "0.000000"} y el
 *       total es {@code "0.000000"}.</li>
 *   <li>El mapa de salida conserva EXCLUSIVAMENTE las claves activas en
 *       orden numérico ascendente — el orden NO depende del orden de
 *       iteración de {@code Map.copyOf} del input; se reconstruye
 *       deterministamente desde un {@code List<Integer>} explícito.</li>
 * </ul>
 *
 * <p>Nunca se usa {@code BigDecimal.divide} sin escala, ni se convierten
 * dominios a {@code double}/{@code float}. El cálculo es puro y determinista;
 * no toca la base ni el motor.</p>
 */
public final class MonedaRacionalCalculador {

    /** Escala del dinero (DECIMAL_DIGITS_UNITS = 10⁶). */
    private static final int SCALE_DINERO = 6;

    /** Escala del porcentaje/peso (DECIMAL_DIGITS_UNITS = 10⁴). */
    private static final int SCALE_PORCENTAJE = 4;

    private MonedaRacionalCalculador() {}

    /**
     * Distribuye el dinero de la actividad entre sus períodos activos usando
     * el algoritmo racional entero de DM §16.
     *
     * @param precioTotal        precio total de la actividad (escala ≤ 6).
     * @param pesoPonderado      peso ponderado de la actividad (escala 4).
     * @param numeroPeriodos     rango vigente del cronograma (sólo para
     *                           validar claves activas; ya validado por la
     *                           frontera HTTP, pero se respeta el contrato
     *                           interno).
     * @param avancePorPeriodo   mapa de claves activas (período → avance a
     *                           escala 4). Periodos ausentes son inactivos.
     * @return distribución con {@code porPeriodo} (escala 6) y {@code total}
     *         (escala 6).
     */
    public static DistribucionMonto distribuir(
            BigDecimal precioTotal,
            BigDecimal pesoPonderado,
            int numeroPeriodos,
            Map<String, String> avancePorPeriodo) {
        BigInteger precioUnits = aUnidadesDinero(precioTotal);
        BigInteger pesoUnits = aUnidadesPorcentaje(pesoPonderado);

        // Precio cero: atajo determinista — toda distribución a 0.
        if (precioUnits.signum() == 0) {
            Map<String, String> ceros = new LinkedHashMap<>();
            List<Integer> activas = clavesActivasOrdenadas(avancePorPeriodo, numeroPeriodos);
            for (int p : activas) {
                ceros.put(Integer.toString(p), "0.000000");
            }
            return new DistribucionMonto(ceros, BigDecimal.ZERO.setScale(SCALE_DINERO));
        }

        List<Integer> activas = clavesActivasOrdenadas(avancePorPeriodo, numeroPeriodos);
        BigInteger[] avanceUnits = new BigInteger[activas.size()];
        BigInteger sumaAvance = BigInteger.ZERO;
        for (int i = 0; i < activas.size(); i++) {
            String clave = Integer.toString(activas.get(i));
            String valorCrudo = avancePorPeriodo.get(clave);
            BigInteger v = aUnidadesPorcentaje(new BigDecimal(valorCrudo));
            avanceUnits[i] = v;
            sumaAvance = sumaAvance.add(v);
        }

        if (pesoUnits.signum() == 0) {
            // Peso cero + precio positivo: uniforme entre activas, residual al
            // último activo. Sin activas → borrador (total 0).
            if (activas.isEmpty()) {
                return new DistribucionMonto(Map.of(), BigDecimal.ZERO.setScale(SCALE_DINERO));
            }
            return uniformeResidualUltimoActivo(precioUnits, activas);
        }

        return distribucionPorAvance(precioUnits, pesoUnits, activas, avanceUnits, sumaAvance);
    }

    /**
     * Implementa la rama principal: targetUnits con HALF_UP y bases con FLOOR,
     * distribuyendo el residual por mayor fracción descendente y desempate
     * ascendente por período.
     */
    private static DistribucionMonto distribucionPorAvance(
            BigInteger precioUnits,
            BigInteger pesoUnits,
            List<Integer> activas,
            BigInteger[] avanceUnits,
            BigInteger sumaAvance) {
        // targetUnits = HALF_UP(precioUnits × sumaAvance / pesoUnits)
        // HALF_UP para números positivos: floor((n + d/2) / d)
        BigInteger numeradorTarget = precioUnits.multiply(sumaAvance);
        BigInteger targetUnits =
                numeradorTarget.add(pesoUnits.divide(BigInteger.TWO)).divide(pesoUnits);

        if (activas.isEmpty() || targetUnits.signum() == 0) {
            return new DistribucionMonto(Map.of(), deUnidadesDinero(targetUnits));
        }

        BigInteger[] bases = new BigInteger[activas.size()];
        BigInteger[] fracciones = new BigInteger[activas.size()]; // numerador del residuo (×pesoUnits)
        BigInteger sumaBases = BigInteger.ZERO;
        for (int i = 0; i < activas.size(); i++) {
            BigInteger n = precioUnits.multiply(avanceUnits[i]);
            BigInteger[] div = n.divideAndRemainder(pesoUnits);
            bases[i] = div[0]; // floor
            fracciones[i] = div[1]; // 0 ≤ frac < pesoUnits
            sumaBases = sumaBases.add(bases[i]);
        }
        BigInteger residuo = targetUnits.subtract(sumaBases);

        // Asignación de residuo por mayor fracción descendente; empate por
        // período ascendente (las activas ya vienen ordenadas asc).
        List<Integer> indices = new ArrayList<>(activas.size());
        for (int i = 0; i < activas.size(); i++) {
            indices.add(i);
        }
        indices.sort((a, b) -> {
            int cmp = fracciones[b].compareTo(fracciones[a]); // descendente por fracción
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(activas.get(a), activas.get(b)); // empate → período ascendente
        });

        BigInteger restante = residuo;
        for (int idx : indices) {
            if (restante.signum() == 0) {
                break;
            }
            bases[idx] = bases[idx].add(BigInteger.ONE);
            restante = restante.subtract(BigInteger.ONE);
        }

        Map<String, String> porPeriodo = new LinkedHashMap<>();
        for (int i = 0; i < activas.size(); i++) {
            porPeriodo.put(
                    Integer.toString(activas.get(i)), deUnidadesDinero(bases[i]).toPlainString());
        }
        BigDecimal total = deUnidadesDinero(targetUnits);
        return new DistribucionMonto(porPeriodo, total);
    }

    /**
     * Distribución uniforme entre activas cuando {@code pesoUnits = 0} y
     * {@code precioUnits > 0}. Residual (1 unidad) al último período activo.
     */
    private static DistribucionMonto uniformeResidualUltimoActivo(BigInteger precioUnits, List<Integer> activas) {
        int n = activas.size();
        BigInteger nBi = BigInteger.valueOf(n);
        BigInteger[] div = precioUnits.divideAndRemainder(nBi);
        BigInteger base = div[0]; // floor
        BigInteger residuo = div[1];
        Map<String, String> porPeriodo = new LinkedHashMap<>();
        for (int i = 0; i < activas.size(); i++) {
            BigInteger b = base;
            // Residual al ÚLTIMO ACTIVO (no al último ordinal del rango).
            if (residuo.signum() > 0 && i == activas.size() - 1) {
                b = b.add(residuo);
            }
            porPeriodo.put(Integer.toString(activas.get(i)), deUnidadesDinero(b).toPlainString());
        }
        BigDecimal total = deUnidadesDinero(precioUnits);
        return new DistribucionMonto(porPeriodo, total);
    }

    /**
     * Devuelve las claves activas (1..{@code numeroPeriodos}) en orden
     * numérico ascendente. Las claves ausentes son inactivas (no 0.0000); las
     * claves con valor {@code "0.0000"} cuentan como activas (semántica de
     * Plan 029 / D-10).
     *
     * <p>El orden se reconstruye desde el parseo explícito de cada clave —
     * nunca se confía en el orden de iteración del {@code Map} de entrada, lo
     * que garantiza determinismo independientemente del proveedor del mapa.</p>
     */
    private static List<Integer> clavesActivasOrdenadas(Map<String, String> mapa, int numeroPeriodos) {
        if (mapa == null || mapa.isEmpty()) {
            return List.of();
        }
        List<Integer> activas = new ArrayList<>();
        for (Map.Entry<String, String> e : mapa.entrySet()) {
            int p;
            try {
                p = Integer.parseInt(e.getKey());
            } catch (NumberFormatException nfe) {
                continue;
            }
            if (p < 1 || p > numeroPeriodos) {
                continue;
            }
            activas.add(p);
        }
        Collections.sort(activas);
        return List.copyOf(activas);
    }

    private static BigInteger aUnidadesDinero(BigDecimal v) {
        if (v == null) {
            return BigInteger.ZERO;
        }
        BigDecimal cuantizado = v.setScale(SCALE_DINERO, RoundingMode.HALF_UP);
        return cuantizado.movePointRight(SCALE_DINERO).toBigIntegerExact();
    }

    private static BigInteger aUnidadesPorcentaje(BigDecimal v) {
        if (v == null) {
            return BigInteger.ZERO;
        }
        BigDecimal cuantizado = v.setScale(SCALE_PORCENTAJE, RoundingMode.HALF_UP);
        return cuantizado.movePointRight(SCALE_PORCENTAJE).toBigIntegerExact();
    }

    private static BigDecimal deUnidadesDinero(BigInteger units) {
        return new BigDecimal(units, SCALE_DINERO);
    }

    /**
     * Resultado inmutable: dinero por período (escala 6) y total (escala 6).
     *
     * <p>El mapa conserva el orden de inserción ascendente por período — las
     * ramas que producen el mapa lo construyen con {@link LinkedHashMap} en
     * ese orden, y aquí se envuelve con {@link Collections#unmodifiableMap}
     * para garantizar inmutabilidad sin perderlo. {@code Map.copyOf} sobre
     * {@code LinkedHashMap} produciría un {@code ImmutableCollections.MapN}
     * cuyo orden de iteración NO es necesariamente el de inserción.</p>
     */
    public record DistribucionMonto(Map<String, String> porPeriodo, BigDecimal total) {

        public DistribucionMonto {
            porPeriodo = porPeriodo == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(porPeriodo));
            total = total == null ? BigDecimal.ZERO.setScale(SCALE_DINERO) : total.setScale(SCALE_DINERO);
        }
    }
}
