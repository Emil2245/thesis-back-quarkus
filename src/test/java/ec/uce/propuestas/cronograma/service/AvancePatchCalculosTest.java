package ec.uce.propuestas.cronograma.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.cronograma.service.AvancePatchCalculos.ResultadoMap;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Plan 029 (P-34) — RED for {@link AvancePatchCalculos}. Cubre distribución
 * uniforme exacta, residual determinista, mover conservando suma, mover
 * fuera de rango y solapado, y redimensionar conservando la suma del
 * segmento.
 */
class AvancePatchCalculosTest {

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
    // Distribución uniforme
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void distribucion_uniforme_5_periodos_peso_100_constante() {
        Map<String, String> out = AvancePatchCalculos.distribucionUniforme(bd("100.0000"), List.of(1, 2, 3, 4, 5), 5);
        for (int i = 1; i <= 5; i++) {
            assertEquals("20.0000", out.get(Integer.toString(i)));
        }
    }

    @Test
    void distribucion_uniforme_3_periodos_peso_100_residuo_0() {
        // 100 / 3 = 33.3333 → el residual +1 va al último (3) → 33.3334
        Map<String, String> out = AvancePatchCalculos.distribucionUniforme(bd("100.0000"), List.of(1, 2, 3), 12);
        assertEquals("33.3333", out.get("1"));
        assertEquals("33.3333", out.get("2"));
        assertEquals("33.3334", out.get("3"));
    }

    @Test
    void distribucion_uniforme_redondea_base_half_up_y_aplica_residual_firmado() {
        Map<String, String> out = AvancePatchCalculos.distribucionUniforme(bd("1.0000"), List.of(1, 2, 3, 4, 5, 6), 6);
        for (int i = 1; i <= 5; i++) {
            assertEquals("0.1667", out.get(Integer.toString(i)));
        }
        assertEquals("0.1665", out.get("6"));
    }

    @Test
    void distribucion_uniforme_ordena_periodos_y_deja_residual_en_el_maximo() {
        Map<String, String> out = AvancePatchCalculos.distribucionUniforme(bd("1.0000"), List.of(6, 1, 4, 2, 5, 3), 6);
        assertEquals("0.1665", out.get("6"));
        assertEquals("0.1667", out.get("5"));
    }

    @Test
    void distribucion_uniforme_peso_33_a_2_periodos_sin_residuo() {
        // 33 / 2 = 16.5 → 16.5000 en cada período, sin residual.
        Map<String, String> out = AvancePatchCalculos.distribucionUniforme(bd("33.0000"), List.of(1, 2), 12);
        assertEquals("16.5000", out.get("1"));
        assertEquals("16.5000", out.get("2"));
        // Σ exacta a escala 4
        BigDecimal suma = new BigDecimal(out.get("1")).add(new BigDecimal(out.get("2")));
        assertEquals(0, bd("33.0000").compareTo(suma));
    }

    @Test
    void distribucion_uniforme_4_periodos_21_3_residuo_positivo_y_suma_exacta() {
        // 21.3333 entre 4 → 5.3333 × 4 = 21.3332 + residual +1 → 5.3333,5.3333,5.3333,5.3334
        Map<String, String> out = AvancePatchCalculos.distribucionUniforme(bd("21.3333"), List.of(1, 2, 3, 4), 10);
        assertEquals("5.3333", out.get("1"));
        assertEquals("5.3333", out.get("2"));
        assertEquals("5.3333", out.get("3"));
        assertEquals("5.3334", out.get("4"));
        BigDecimal suma = BigDecimal.ZERO;
        for (String v : out.values()) {
            suma = suma.add(new BigDecimal(v));
        }
        assertEquals(0, bd("21.3333").compareTo(suma));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Mover
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void mover_segmento_2_4_a_5_7_conserva_valores_y_suma() {
        Map<String, String> actual = mapaPares("2", "2.0000", "3", "3.0000", "4", "4.0000", "9", "1.0000");
        ResultadoMap r = AvancePatchCalculos.mover(actual, 2, 4, 3, 10);
        assertEquals("1.0000", r.mapa().get("9"));
        assertEquals("2.0000", r.mapa().get("5"));
        assertEquals("3.0000", r.mapa().get("6"));
        assertEquals("4.0000", r.mapa().get("7"));
        // Las claves 2,3,4 ya no existen en el destino
        assertEquals(4, r.mapa().size());
    }

    @Test
    void mover_segmento_fuera_de_rango_devuelve_400_sin_mutacion() {
        Map<String, String> actual = mapaPares("1", "1.0000", "5", "5.0000");
        ProblemaException p =
                assertThrows(ProblemaException.class, () -> AvancePatchCalculos.mover(actual, 1, 1, 10, 10));
        assertEquals(400, p.getResponse().getStatus());
    }

    @Test
    void mover_segmento_requiere_maximo_actual_no_adyacente() {
        // Si hay clave adyacente activa, el rango no es máximo → 400
        Map<String, String> actual = mapaPares("1", "1.0", "2", "2.0", "3", "3.0");
        ProblemaException p =
                assertThrows(ProblemaException.class, () -> AvancePatchCalculos.mover(actual, 1, 2, 4, 10));
        assertEquals(400, p.getResponse().getStatus());
    }

    @Test
    void mover_segmento_colisiona_con_clave_fuera_del_segmento_devuelve_409() {
        Map<String, String> actual = mapaPares("1", "1.0", "2", "2.0", "5", "5.0");
        // Mover [1,2] +3 → caería sobre la clave 5 → 409 segmento-solapado
        AvanceSegmentoException ex =
                assertThrows(AvanceSegmentoException.class, () -> AvancePatchCalculos.mover(actual, 1, 2, 3, 10));
        assertEquals(409, ex.getResponse().getStatus());
        assertEquals(
                "segmento-solapado",
                ((ec.uce.propuestas.common.ErrorPayload) ex.getResponse().getEntity()).codigo());
    }

    // ──────────────────────────────────────────────────────────────────────
    // Redimensionar
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void redimensionar_conserva_suma_del_segmento_y_redistribuye() {
        // Segmento [1,3] con 1+2+3=6 en unidades 6000 (0.0001). Destino [4,5]
        // → 6000/2 = 3000+3000 y conserva suma exacta.
        Map<String, String> actual = mapaPares("1", "1.0000", "2", "2.0000", "3", "3.0000");
        ResultadoMap r = AvancePatchCalculos.redimensionar(actual, 1, 3, 4, 5, 10);
        assertEquals("3.0000", r.mapa().get("4"));
        assertEquals("3.0000", r.mapa().get("5"));
        // Σ exacta
        BigDecimal suma =
                new BigDecimal(r.mapa().get("4")).add(new BigDecimal(r.mapa().get("5")));
        assertEquals(0, bd("6.0000").compareTo(suma));
    }

    @Test
    void redimensionar_suma_no_entera_reparte_residual_al_ultimo_periodo() {
        // Segmento [1,2] suma 10.0000 → destino [4,6]: 10/3 = 3 + residual 1 → 3.3333+3.3333+3.3334
        Map<String, String> actual = mapaPares("1", "5.0000", "2", "5.0000");
        ResultadoMap r = AvancePatchCalculos.redimensionar(actual, 1, 2, 4, 6, 10);
        // 100000 / 3 = 33333 + residuo 1 → 3.3333,3.3333,3.3334
        assertEquals("3.3333", r.mapa().get("4"));
        assertEquals("3.3333", r.mapa().get("5"));
        assertEquals("3.3334", r.mapa().get("6"));
        // Σ exacta
        BigDecimal suma = BigDecimal.ZERO;
        for (String v : r.mapa().values()) {
            suma = suma.add(new BigDecimal(v));
        }
        assertEquals(0, bd("10.0000").compareTo(suma));
    }

    @Test
    void redimensionar_usa_la_misma_base_half_up_y_residual_firmado() {
        Map<String, String> actual = mapaPares("1", "1.0000");
        ResultadoMap r = AvancePatchCalculos.redimensionar(actual, 1, 1, 2, 7, 7);
        for (int i = 2; i <= 6; i++) {
            assertEquals("0.1667", r.mapa().get(Integer.toString(i)));
        }
        assertEquals("0.1665", r.mapa().get("7"));
    }

    @Test
    void redimensionar_solo_conserva_suma_del_segmento_no_del_peso_total() {
        // Actividad con peso total 10 → segmento [1] suma 1.0; destino [3,5].
        // Solo se redistribuye 1.0 entre 3 y 5 (no 10).
        Map<String, String> actual = mapaPares("1", "1.0000");
        ResultadoMap r = AvancePatchCalculos.redimensionar(actual, 1, 1, 3, 5, 10);
        // 10000 / 3 = 3333 + residuo 1 → 0.3333,0.3333,0.3334 → suma 1.0
        assertEquals(3, r.mapa().size());
        BigDecimal suma = BigDecimal.ZERO;
        for (String v : r.mapa().values()) {
            suma = suma.add(new BigDecimal(v));
        }
        assertEquals(0, bd("1.0000").compareTo(suma));
    }

    @Test
    void redimensionar_409_si_destino_solapa_con_claves_externas() {
        Map<String, String> actual = mapaPares("1", "1.0", "4", "5.0");
        AvanceSegmentoException ex = assertThrows(
                AvanceSegmentoException.class, () -> AvancePatchCalculos.redimensionar(actual, 1, 1, 3, 5, 10));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    void redimensionar_rechaza_si_segmento_no_es_maximo() {
        Map<String, String> actual = mapaPares("1", "1.0", "2", "2.0", "3", "3.0");
        ProblemaException p =
                assertThrows(ProblemaException.class, () -> AvancePatchCalculos.redimensionar(actual, 1, 2, 4, 6, 10));
        assertEquals(400, p.getResponse().getStatus());
    }

    // ──────────────────────────────────────────────────────────────────────
    // Claves cero activas
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void clave_cero_es_activa_y_participa_en_segmentos() {
        Map<String, String> actual = mapaPares("1", "1.0000", "3", "0.0000", "4", "4.0000");
        // Mover [3,4] (incluye el cero como activo) por +1 → 4,5
        ResultadoMap r = AvancePatchCalculos.mover(actual, 3, 4, 1, 10);
        assertEquals("0.0000", r.mapa().get("4"));
        assertEquals("4.0000", r.mapa().get("5"));
        assertEquals(3, r.mapa().size());
    }
}
