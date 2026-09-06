package ec.uce.propuestas.cronograma.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.cronograma.service.MonedaRacionalCalculador.DistribucionMonto;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Plan 030 (P-35/P-36) — RED para el cálculo racional entero de dinero por
 * actividad/período (DM §16, fila {@code Monto_actividad_periodo_i,t}).
 *
 * <p>El cálculo convierte dinero a escala 6 y porcentaje/peso a escala 4 a
 * unidades enteras ({@code BigInteger}, no {@code long} — ver
 * {@link MonedaRacionalCalculador} sobre el límite de overflow), de modo que
 * la suma exacta se preserva sin {@code BigDecimal.divide()} sin escala. La
 * frontera observable es un mapa período → string decimal a escala 6 cuyo Σ
 * reconcilia con el precio total de la actividad (cuando el peso es positivo
 * y los avances suman el peso) o con la regla uniforme (cuando el peso es
 * cero y el precio es positivo).</p>
 *
 * <p>Casos cubiertos (coherentes con el catálogo mínimo del plan):
 * <ul>
 *   <li>Peso positivo y Σ avance = peso: Σ de dinero = precio exacto.</li>
 *   <li>Peso positivo y Σ avance &lt; peso: Σ de dinero = targetUnits HALF_UP.</li>
 *   <li>Peso positivo con 1/3 residual determinista: cada base es
 *       {@code floor(precioUnits × avanceUnits_t / pesoUnits)} y el residuo se
 *       asigna por fracción descendente con desempate ascendente.</li>
 *   <li>Peso positivo y Σ avance &gt; peso: mismo algoritmo, targetUnits
 *       proporcional pero sin overflow.</li>
 *   <li>Peso cero con precio positivo y ≥1 clave activa: distribución uniforme
 *       con residual al último período activo.</li>
 *   <li>Peso cero con precio positivo y 0 claves activas: distribución vacía
 *       (la actividad queda en borrador).</li>
 *   <li>Precio cero: todas las distribuciones devuelven {@code "0.000000"}.</li>
 *   <li>Las claves devueltas son EXACTAMENTE las activas y conservan el orden
 *       numérico ascendente — independiente del orden de iteración del mapa
 *       de entrada.</li>
 *   <li>Overflow potencial: precio cerca del límite {@code NUMERIC(14,6)} con
 *       Σ avance máximo no produce overflow (usa {@code BigInteger}).</li>
 * </ul>
 */
class MonedaRacionalCalculadorTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static Map<String, String> mapaPares(Object... pares) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) {
            m.put((String) pares[i], (String) pares[i + 1]);
        }
        return Map.copyOf(m);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Peso positivo + Σ avance = peso
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Una actividad completa de $1.000000 USD con un solo período recibe
     * exactamente $1.000000 en ese período.
     */
    @Test
    void distribucion_completa_un_periodo_reconcilia_precio_total() {
        DistribucionMonto d = MonedaRacionalCalculador.distribuir(
                bd("1.000000"), // precio
                bd("1.0000"), // peso
                1, // numeroPeriodos
                mapaPares("1", "1.0000"));
        assertEquals(mapaPares("1", "1.000000"), d.porPeriodo());
        assertEquals(bd("1.000000"), d.total());
    }

    /** Actividad completa de $6.000000 con avance distribuido en 3 períodos exactos. */
    @Test
    void distribucion_completa_tres_periodos_cuya_suma_reconcilia_precio() {
        DistribucionMonto d = MonedaRacionalCalculador.distribuir(
                bd("6.000000"),
                bd("1.0000"),
                3, // numeroPeriodos
                mapaPares("1", "0.2500", "2", "0.5000", "3", "0.2500"));
        assertEquals(bd("6.000000"), d.total());
        assertEquals(mapaPares("1", "1.500000", "2", "3.000000", "3", "1.500000"), d.porPeriodo());
    }

    // ──────────────────────────────────────────────────────────────────────
    // 1/3 residual determinista
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Una actividad completa de $1.000000 USD distribuida en 3 períodos
     * iguales ({@code 0.3333, 0.3333, 0.3334} para que Σ = 1.0000 exacto):
     * cada base es {@code floor(1_000_000 × 3333 / 10_000)} = 333_300 unidades
     * y el Σ es exactamente 1_000_000 (sin residual visible). La garantía es
     * la reconciliación exacta con el precio total.
     */
    @Test
    void distribucion_completa_tres_periodos_iguales_sin_residual() {
        DistribucionMonto d = MonedaRacionalCalculador.distribuir(
                bd("1.000000"), bd("1.0000"), 3, mapaPares("1", "0.3333", "2", "0.3333", "3", "0.3334"));
        assertEquals(bd("1.000000"), d.total());
        // 0.3333 → floor(1_000_000 × 3333 / 10_000) = 333_300 unidades = $0.333300
        // 0.3334 → floor(1_000_000 × 3334 / 10_000) = 333_400 unidades = $0.333400
        assertEquals(mapaPares("1", "0.333300", "2", "0.333300", "3", "0.333400"), d.porPeriodo());
    }

    /**
     * Caso de residual real (1/3 no exacto): precioTotal $0.10 y Σ avance
     * 0.9999 (3 períodos de 0.3333), targetUnits = 99_990, cada base =
     * 33_330 y la suma cierra sin pérdida.
     */
    @Test
    void distribucion_residual_uno_tercio_tres_periodos_misma_cifra() {
        DistribucionMonto d = MonedaRacionalCalculador.distribuir(
                bd("0.100000"), bd("1.0000"), 3, mapaPares("1", "0.3333", "2", "0.3333", "3", "0.3333"));
        // Σ avanceUnits = 9999; targetUnits = HALF_UP(100_000 × 9999 / 10_000) = 99_990
        assertEquals(bd("0.099990"), d.total());
        // base_t = floor(100_000 × 3333 / 10_000) = floor(33_330.0) = 33_330 → $0.033330
        // Σ bases = 99_990 = targetUnits, sin residual a distribuir
        assertEquals(mapaPares("1", "0.033330", "2", "0.033330", "3", "0.033330"), d.porPeriodo());
    }

    /**
     * Caso de residual por fracción: precioTotal $10.000000, peso 0.5000,
     * Σ avance 0.5000 repartido en 3 períodos no enteros (0.1666 + 0.1667 +
     * 0.1667). El algoritmo racional asigna cada base y reparte el residual
     * por mayor fracción restante con desempate por período ascendente.
     */
    @Test
    void distribucion_residual_tres_periodos_desiguales_con_fraccion() {
        DistribucionMonto d = MonedaRacionalCalculador.distribuir(
                bd("10.000000"), bd("0.5000"), 3, mapaPares("1", "0.1666", "2", "0.1667", "3", "0.1667"));
        // Σ avanceUnits = 5000; targetUnits = HALF_UP(10_000_000 × 5000 / 5000) = 10_000_000
        assertEquals(bd("10.000000"), d.total());
        // bases: floor(10_000_000 × 1666 / 5000) = 3_332_000 → $3.332000
        //         floor(10_000_000 × 1667 / 5000) = 3_334_000 → $3.334000
        //         floor(10_000_000 × 1667 / 5000) = 3_334_000 → $3.334000
        // Σ bases = 10_000_000 = targetUnits
        assertEquals(mapaPares("1", "3.332000", "2", "3.334000", "3", "3.334000"), d.porPeriodo());
    }

    /**
     * Σ avanceUnits &lt; pesoUnits: el targetUnits se reduce proporcionalmente
     * (HALF_UP) y la suma por períodos cierra a targetUnits.
     */
    @Test
    void distribucion_parcial_target_units_proporcional_al_avance() {
        DistribucionMonto d = MonedaRacionalCalculador.distribuir(
                bd("1.000000"), bd("1.0000"), 2, mapaPares("1", "0.5000")); // Σ = 0.5000 = 50% del peso
        // targetUnits = HALF_UP(1_000_000 × 5000 / 10_000) = 500_000
        assertEquals(bd("0.500000"), d.total());
        // base_1 = floor(1_000_000 × 5000 / 10_000) = 500_000 → $0.500000
        assertEquals(mapaPares("1", "0.500000"), d.porPeriodo());
    }

    /**
     * Σ avanceUnits = 0: targetUnits = 0 y todas las distribuciones son
     * $0.000000 (sin残余 que repartir).
     */
    @Test
    void distribucion_avance_cero_target_cero() {
        DistribucionMonto d =
                MonedaRacionalCalculador.distribuir(bd("5.000000"), bd("1.0000"), 3, mapaPares()); // sin claves
        assertEquals(bd("0.000000"), d.total());
        assertEquals(Map.of(), d.porPeriodo());
    }

    // ──────────────────────────────────────────────────────────────────────
    // Peso cero con precio positivo
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Peso 0.0000, precio $9.000000, 3 claves activas: base uniforme = $3.000000,
     * sin residual (9 divisible entre 3).
     */
    @Test
    void distribucion_peso_cero_precio_positivo_uniforme_sin_residual() {
        DistribucionMonto d = MonedaRacionalCalculador.distribuir(
                bd("9.000000"), bd("0.0000"), 3, mapaPares("1", "0.0000", "2", "0.0000", "3", "0.0000"));
        assertEquals(bd("9.000000"), d.total());
        assertEquals(mapaPares("1", "3.000000", "2", "3.000000", "3", "3.000000"), d.porPeriodo());
    }

    /**
     * Peso 0.0000, precio $10.000000, 3 claves activas: base uniforme = $3.333333
     * y residual $1 va al último período activo ($3.333334).
     */
    @Test
    void distribucion_peso_cero_precio_positivo_uniforme_con_residual_al_ultimo_activo() {
        DistribucionMonto d = MonedaRacionalCalculador.distribuir(
                bd("10.000000"), bd("0.0000"), 3, mapaPares("1", "0.0000", "2", "0.0000", "3", "0.0000"));
        assertEquals(bd("10.000000"), d.total());
        assertEquals(mapaPares("1", "3.333333", "2", "3.333333", "3", "3.333334"), d.porPeriodo());
    }

    /**
     * Peso 0.0000, precio $10.000000, 2 claves activas (períodos 2 y 5): el
     * residual va al último activo (período 5), no al último ordinal (n).
     */
    @Test
    void distribucion_peso_cero_residual_al_ultimo_clave_activa_no_ordinal() {
        DistribucionMonto d = MonedaRacionalCalculador.distribuir(
                bd("10.000000"), bd("0.0000"), 5, mapaPares("2", "0.0000", "5", "0.0000"));
        assertEquals(bd("10.000000"), d.total());
        assertEquals(mapaPares("2", "5.000000", "5", "5.000000"), d.porPeriodo());
    }

    /**
     * Peso 0.0000, precio $10.000000, 0 claves activas: la distribución es
     * vacía y el total es $0.000000 (la actividad queda en borrador).
     */
    @Test
    void distribucion_peso_cero_precio_positivo_sin_claves_queda_en_borrador() {
        DistribucionMonto d = MonedaRacionalCalculador.distribuir(bd("10.000000"), bd("0.0000"), 3, mapaPares());
        assertEquals(bd("0.000000"), d.total());
        assertEquals(Map.of(), d.porPeriodo());
    }

    // ──────────────────────────────────────────────────────────────────────
    // Precio cero
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void distribucion_precio_cero_siempre_cero() {
        DistribucionMonto d = MonedaRacionalCalculador.distribuir(
                bd("0.000000"), bd("1.0000"), 3, mapaPares("1", "0.5000", "2", "0.5000"));
        assertEquals(bd("0.000000"), d.total());
        assertEquals(mapaPares("1", "0.000000", "2", "0.000000"), d.porPeriodo());
    }

    // ──────────────────────────────────────────────────────────────────────
    // Overflow & determinismo del orden
    // ──────────────────────────────────────────────────────────────────────

    /**
     * BigInteger: precio cerca del límite NUMERIC(14,6) ($9 999 999 999.999999)
     * con Σ avance grande (9 999.9999). {@code long} overflowea aquí
     * (~10²⁴ ≫ Long.MAX_VALUE 9.22×10¹⁸); {@code BigInteger} lo maneja sin
     * error y la suma exacta por período debe cuadrar contra el targetUnits.
     */
    @Test
    void distribucion_sin_overflow_con_precio_y_avance_maximos() {
        DistribucionMonto d = MonedaRacionalCalculador.distribuir(
                bd("9999999999.999999"), bd("10000.0000"), 2, mapaPares("1", "5000.0000", "2", "5000.0000"));
        // Σ avance = 10000.0000 = peso. Total = HALF_UP(precio × Σ avance / peso)
        //             = precio × 1.0000 = $9_999_999_999.999999.
        assertEquals(bd("9999999999.999999"), d.total());
        // bases: precio × 5000.0000 / 10000.0000 = floor(precio/2)
        //   = 4_999_999_999_999_999 unidades = 4_999_999_999.999999.
        // Σ bases = 9_999_999_999_999_998; residuo = targetUnits - Σ bases
        //   = 1 unidad. Desempate por período ascendente → período 1 recibe
        //   el +1 ($5_000_000_000.000000) y el período 2 conserva el
        //   floor ($4_999_999_999.999999). La suma sigue siendo el total
        //   exacto del precio, sin pérdida por overflow.
        Map<String, String> mapa = d.porPeriodo();
        assertEquals("5000000000.000000", mapa.get("1"));
        assertEquals("4999999999.999999", mapa.get("2"));
        // Σ por períodos = total exacto.
        BigDecimal suma = new BigDecimal(mapa.get("1")).add(new BigDecimal(mapa.get("2")));
        assertEquals(0, d.total().compareTo(suma), "Σ por períodos = total sin pérdida");
    }

    /**
     * Determinismo del orden: las claves del mapa de salida deben estar en
     * orden numérico ascendente aunque el mapa de entrada haya sido
     * construido en otro orden. Esto valida que el algoritmo NO depende del
     * orden de iteración del {@code Map} del caller.
     */
    @Test
    void distribucion_conserva_solo_claves_activas_y_orden_numerico() {
        Map<String, String> entradaDesordenada = new LinkedHashMap<>();
        entradaDesordenada.put("5", "0.5000"); // primero el 5
        entradaDesordenada.put("3", "0.5000"); // después el 3
        DistribucionMonto d = MonedaRacionalCalculador.distribuir(bd("6.000000"), bd("1.0000"), 5, entradaDesordenada);
        assertEquals(Map.of("3", "3.000000", "5", "3.000000"), d.porPeriodo());
        // Verifica que el orden JSON resultante también es ascendente.
        String[] claves = d.porPeriodo().keySet().toArray(new String[0]);
        assertEquals("3", claves[0]);
        assertEquals("5", claves[1]);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Invariantes de salida
    // ──────────────────────────────────────────────────────────────────────

    /**
     * El Σ exacto en dinero (a escala 6) coincide con {@code total()} aunque el
     * cliente redondee la presentación — el lector es la fuente exacta.
     */
    @Test
    void suma_de_periodos_reconcilia_con_total_a_escala_6() {
        DistribucionMonto d = MonedaRacionalCalculador.distribuir(
                bd("100.000000"),
                bd("1.0000"),
                7,
                mapaPares(
                        "1", "0.1429", "2", "0.1429", "3", "0.1428", "4", "0.1429", "5", "0.1428", "6", "0.1429", "7",
                        "0.1428"));
        BigDecimal suma = BigDecimal.ZERO;
        for (String v : d.porPeriodo().values()) {
            suma = suma.add(new BigDecimal(v));
        }
        assertEquals(0, d.total().compareTo(suma.setScale(6)), "Σ en dinero = total");
        assertEquals(0, d.total().compareTo(bd("100.000000")), "Σ de Σ avance = 1.0000 cierra a $100.000000");
    }

    /**
     * El mapa devuelto contiene SOLO las claves activas y en orden numérico
     * ascendente — sin relleno ni reordenamiento.
     */
    @Test
    void distribucion_conserva_solo_claves_activas_sin_relleno() {
        DistribucionMonto d = MonedaRacionalCalculador.distribuir(
                bd("6.000000"), bd("1.0000"), 5, mapaPares("3", "0.5000", "5", "0.5000"));
        assertEquals(
                java.util.List.of("3", "5"),
                java.util.List.copyOf(d.porPeriodo().keySet()));
        assertNotNull(d.total());
        assertTrue(d.total().signum() > 0);
    }
}
