package ec.uce.propuestas.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Plan 042 — el comparador de items jerárquicos ordena numéricamente segmento a
 * segmento. Clase pura: JUnit normal, sin {@code @QuarkusTest} (no necesita
 * contenedor).
 *
 * <p>Todos los casos con dos cifras son deliberados: con menos de diez hermanos
 * el orden lexicográfico y el natural coinciden y el test pasaría igual estando
 * el bug puesto.</p>
 */
class ItemJerarquicoTest {

    private static List<String> ordenar(String... items) {
        List<String> lista = new ArrayList<>(Arrays.asList(items));
        lista.sort(ItemJerarquico.ORDEN);
        return lista;
    }

    @Test
    @DisplayName("1.1, 1.2, 1.10, 1.12 — y NO el orden lexicográfico 1.1, 1.10, 1.12, 1.2")
    void ordenaSubcapitulosDeDosCifrasNumericamente() {
        assertEquals(List.of("1.1", "1.2", "1.10", "1.12"), ordenar("1.12", "1.2", "1.10", "1.1"));
    }

    @Test
    @DisplayName("El padre va antes que sus hijos: 2, 2.1, 2.10")
    void elPadreVaAntesQueLosHijos() {
        assertEquals(List.of("2", "2.1", "2.10"), ordenar("2.10", "2", "2.1"));
        assertTrue(ItemJerarquico.comparar("1", "1.1") < 0);
    }

    @Test
    @DisplayName("El capítulo 2 de Cetro Médico Tulcán: 2.1 … 2.9, 2.10, 2.11")
    void ordenaLosRubrosDelCapituloDos() {
        List<String> desordenados = new ArrayList<>();
        for (int i = 11; i >= 1; i--) {
            desordenados.add("2." + i);
        }
        List<String> esperado = List.of("2.1", "2.2", "2.3", "2.4", "2.5", "2.6", "2.7", "2.8", "2.9", "2.10", "2.11");
        desordenados.sort(ItemJerarquico.ORDEN);
        assertEquals(esperado, desordenados);
    }

    @Test
    @DisplayName("Un segmento con letras se compara como texto y no lanza excepción")
    void segmentoNoNumericoNoRompe() {
        assertTrue(ItemJerarquico.comparar("1.a", "1.2") != 0);
        assertEquals(List.of("1.2", "1.10", "1.a"), ordenar("1.a", "1.10", "1.2"));
        assertEquals(0, ItemJerarquico.comparar("1.a", "1.a"));
    }

    @Test
    @DisplayName("null va al final con ItemJerarquico.ORDEN")
    void nullVaAlFinal() {
        List<String> lista = new ArrayList<>(Arrays.asList("1.10", null, "1.2"));
        lista.sort(ItemJerarquico.ORDEN);
        assertEquals(Arrays.asList("1.2", "1.10", null), lista);
    }

    @Test
    @DisplayName("Profundidad mayor y segmentos vacíos: \"1.\" no se confunde con \"1\"")
    void profundidadYSegmentosVacios() {
        assertEquals(List.of("1.1.1", "1.1.2", "1.1.10", "1.2.1"), ordenar("1.2.1", "1.1.10", "1.1.2", "1.1.1"));
        assertTrue(ItemJerarquico.comparar("1", "1.") < 0);
    }

    @Test
    @DisplayName("El comparador es antisimétrico en los pares que destapan el bug")
    void esAntisimetrico() {
        assertTrue(ItemJerarquico.comparar("1.2", "1.12") < 0);
        assertTrue(ItemJerarquico.comparar("1.12", "1.2") > 0);
        assertEquals(0, ItemJerarquico.comparar("1.12", "1.12"));
    }
}
