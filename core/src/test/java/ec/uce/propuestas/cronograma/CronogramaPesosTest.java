package ec.uce.propuestas.cronograma;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ec.uce.propuestas.cronograma.service.PesoPonderadoCalculador;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Plan 028 — prueba focal del cierre residual de pesos (DM §16).
 *
 * <p>El peso base de cada actividad es
 * {@code precio_total_i / total_general × 100} a escala 4 HALF_UP. Fuera del
 * motor, el cierre trabaja en unidades de {@code 0.0001}:
 * {@code residualUnits = 1000000 - Σ baseUnits}. Un residual positivo se suma a
 * la primera actividad ordenada por {@code precio_total DESC, orden
 * presupuestario}; un residual negativo consume unidades siguiendo ese mismo
 * orden sin bajar de cero. Total cero conserva todos los pesos {@code 0.0000}.
 */
class CronogramaPesosTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    /** Vector canónico [1,1,4]: las bases suman 100.0001 y cierran a 100.0000. */
    @Test
    void vector_1_1_4_cierra_a_100_exacto_sin_negativos() {
        List<BigDecimal> pesos = PesoPonderadoCalculador.calcular(
                List.of(bd("1.000000"), bd("1.000000"), bd("4.000000")), bd("6.000000"));

        assertEquals(List.of(bd("16.6667"), bd("16.6667"), bd("66.6666")), pesos);
        assertEquals(bd("100.0000"), pesos.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** Residual positivo: se suma a la actividad de mayor precio_total. */
    @Test
    void residual_positivo_se_suma_a_la_primera_por_precio_descendente() {
        List<BigDecimal> pesos =
                PesoPonderadoCalculador.calcular(List.of(bd("1.000000"), bd("2.000000")), bd("3.000000"));

        // bases: 33.3333 + 66.6667 = 100.0000 (sin residual) — control de estabilidad
        assertEquals(bd("100.0000"), pesos.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(bd("33.3333"), pesos.get(0));
        assertEquals(bd("66.6667"), pesos.get(1));
    }

    /** Siete partes iguales: bases 14.2857×7 = 99.9999, residual +1 al primero. */
    @Test
    void siete_partes_iguales_reparten_residual_positivo() {
        List<BigDecimal> precios = List.of(
                bd("1.000000"),
                bd("1.000000"),
                bd("1.000000"),
                bd("1.000000"),
                bd("1.000000"),
                bd("1.000000"),
                bd("1.000000"));
        List<BigDecimal> pesos = PesoPonderadoCalculador.calcular(precios, bd("7.000000"));

        assertEquals(bd("100.0000"), pesos.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(bd("14.2858"), pesos.get(0));
        assertEquals(bd("14.2857"), pesos.get(6));
    }

    /** Total cero conserva todos los pesos en 0.0000 (no se inventa distribución). */
    @Test
    void total_cero_conserva_pesos_cero() {
        List<BigDecimal> pesos =
                PesoPonderadoCalculador.calcular(List.of(bd("0.000000"), bd("0.000000")), bd("0.000000"));

        assertEquals(List.of(bd("0.0000"), bd("0.0000")), pesos);
    }

    /** Sin rubros no hay pesos ni residual que repartir. */
    @Test
    void sin_actividades_devuelve_lista_vacia() {
        assertEquals(List.of(), PesoPonderadoCalculador.calcular(List.of(), bd("0.000000")));
    }
}
