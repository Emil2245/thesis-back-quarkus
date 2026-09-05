package ec.uce.propuestas.cronograma.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ec.uce.propuestas.common.ErrorPayload;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.cronograma.dto.ActividadProgramarRequest;
import ec.uce.propuestas.cronograma.dto.ActividadProgramarRequest.Distribuir;
import ec.uce.propuestas.cronograma.dto.ActividadProgramarRequest.Mover;
import ec.uce.propuestas.cronograma.dto.ActividadProgramarRequest.Redimensionar;
import ec.uce.propuestas.cronograma.dto.ActividadProgramarRequest.Reemplazar;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Plan 029 (P-34) — RED for {@link AvancePatchParser}. Cubre:
 *
 * <ul>
 *   <li>Las cuatro operaciones discriminadas se enrutan correctamente.</li>
 *   <li>Propiedades desconocidas producen 400 (validacion).</li>
 *   <li>Body ausente o no-objeto produce 400.</li>
 *   <li>{@code operacion} ausente/nula/no textual produce 400.</li>
 *   <li>Mapa con claves no enteras produce 400.</li>
 *   <li>Mapa con claves {@code "01"} y {@code "1"} se rechaza por duplicado.</li>
 *   <li>Mapa con claves fuera de rango produce 400.</li>
 *   <li>Mapa con valores negativos produce 400.</li>
 *   <li>Mapa con valores no decimales produce 400.</li>
 *   <li>Mapa con valor {@code null} produce 400.</li>
 *   <li>Mapa vacío ({@code {}}) se acepta.</li>
 *   <li>Mapa con claves con valor {@code "0.0000"} se acepta y conserva.</li>
 *   <li>Periodos duplicados en DISTRIBUIR/MOVER/REDIMENSIONAR se rechazan.</li>
 *   <li>Enteros fuera de rango o negativos en bordes/posiciones se rechazan.</li>
 * </ul>
 */
class AvancePatchParserTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode json(String texto) {
        try {
            return JSON.readTree(texto);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ProblemaException parsear(String body, int n) {
        try {
            AvancePatchParser.parsear(json(body), n);
        } catch (ProblemaException p) {
            return p;
        }
        return null;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Cuerpo y discriminador
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void body_nulo_o_no_objeto_devuelve_400() {
        ProblemaException p1 = assertThrows(ProblemaException.class, () -> AvancePatchParser.parsear(null, 12));
        assertEquals(400, p1.getResponse().getStatus());
        ProblemaException p2 =
                assertThrows(ProblemaException.class, () -> AvancePatchParser.parsear(json("[1,2,3]"), 12));
        assertEquals(400, p2.getResponse().getStatus());
    }

    @Test
    void operacion_ausente_no_textual_o_no_reconocida_devuelve_400() {
        // operacion ausente
        ProblemaException p1 = parsear("{\"avancePorPeriodo\":{}}", 12);
        assertEquals(400, p1.getResponse().getStatus());
        // operacion no textual
        ProblemaException p2 = parsear("{\"operacion\":1,\"avancePorPeriodo\":{}}", 12);
        assertEquals(400, p2.getResponse().getStatus());
        // operacion desconocida
        ProblemaException p3 = parsear("{\"operacion\":\"PATCH_LO_QUESE\",\"avancePorPeriodo\":{}}", 12);
        assertEquals(400, p3.getResponse().getStatus());
    }

    // ──────────────────────────────────────────────────────────────────────
    // REEMPLAZAR_AVANCES
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void reemplazar_con_mapa_vacio_se_acepta() {
        ActividadProgramarRequest oper =
                AvancePatchParser.parsear(json("{\"operacion\":\"REEMPLAZAR_AVANCES\",\"avancePorPeriodo\":{}}"), 12);
        assertTrue(oper instanceof Reemplazar);
        assertEquals(Map.of(), ((Reemplazar) oper).avancePorPeriodo());
    }

    @Test
    void reemplazar_requiere_mapa_presente_no_nulo_y_con_decimales_string() {
        assertEquals(
                400,
                parsear("{\"operacion\":\"REEMPLAZAR_AVANCES\"}", 12)
                        .getResponse()
                        .getStatus());
        assertEquals(
                400,
                parsear("{\"operacion\":\"REEMPLAZAR_AVANCES\",\"avancePorPeriodo\":null}", 12)
                        .getResponse()
                        .getStatus());
        assertEquals(
                400,
                parsear("{\"operacion\":\"REEMPLAZAR_AVANCES\",\"avancePorPeriodo\":{\"1\":1.25}}", 12)
                        .getResponse()
                        .getStatus());
    }

    @Test
    void reemplazar_valida_claves_normalizadas_y_rechaza_duplicados_01_1() {
        // Claves redundantes que normalizan al mismo entero → 400
        ProblemaException p = parsear(
                "{\"operacion\":\"REEMPLAZAR_AVANCES\",\"avancePorPeriodo\":{\"01\":\"1.0\",\"1\":\"1.0\"}}", 12);
        assertEquals(400, p.getResponse().getStatus());
        assertTrue(((ErrorPayload) p.getResponse().getEntity()).mensaje().contains("duplicado"));
    }

    @Test
    void reemplazar_rechaza_clave_no_entera_fuera_de_rango_y_valor_negativo() {
        // Clave no entera
        ProblemaException p1 =
                parsear("{\"operacion\":\"REEMPLAZAR_AVANCES\",\"avancePorPeriodo\":{\"x\":\"1.0\"}}", 12);
        assertEquals(400, p1.getResponse().getStatus());
        // Clave fuera de rango
        ProblemaException p2 =
                parsear("{\"operacion\":\"REEMPLAZAR_AVANCES\",\"avancePorPeriodo\":{\"0\":\"1.0\"}}", 12);
        assertEquals(400, p2.getResponse().getStatus());
        // Valor negativo
        ProblemaException p3 =
                parsear("{\"operacion\":\"REEMPLAZAR_AVANCES\",\"avancePorPeriodo\":{\"1\":\"-1.0\"}}", 12);
        assertEquals(400, p3.getResponse().getStatus());
        // Valor no decimal
        ProblemaException p4 = parsear("{\"operacion\":\"REEMPLAZAR_AVANCES\",\"avancePorPeriodo\":{\"1\":true}}", 12);
        assertEquals(400, p4.getResponse().getStatus());
        // Valor null
        ProblemaException p5 = parsear("{\"operacion\":\"REEMPLAZAR_AVANCES\",\"avancePorPeriodo\":{\"1\":null}}", 12);
        assertEquals(400, p5.getResponse().getStatus());
    }

    @Test
    void reemplazar_normaliza_claves_y_valores_a_escala_4() {
        ActividadProgramarRequest oper = AvancePatchParser.parsear(
                json("{\"operacion\":\"REEMPLAZAR_AVANCES\","
                        + "\"avancePorPeriodo\":{\"1\":\"1.2345\",\"3\":\"0.0000\",\"5\":\"4\"}}"),
                10);
        Reemplazar r = (Reemplazar) oper;
        assertEquals(3, r.avancePorPeriodo().size());
        // La clave "1" y su valor válido a escala 4 se mantienen.
        assertEquals("1.2345", r.avancePorPeriodo().get("1"));
        assertEquals("0.0000", r.avancePorPeriodo().get("3"));
        assertEquals("4.0000", r.avancePorPeriodo().get("5"));
    }

    @Test
    void reemplazar_rechaza_propiedades_desconocidas() {
        ProblemaException p =
                parsear("{\"operacion\":\"REEMPLAZAR_AVANCES\",\"avancePorPeriodo\":{},\"rubroId\":\"X\"}", 12);
        assertEquals(400, p.getResponse().getStatus());
        assertTrue(((ErrorPayload) p.getResponse().getEntity()).mensaje().contains("rubroId"));
    }

    @Test
    void reemplazar_valor_con_escala_mayor_a_4_se_rechaza() {
        ProblemaException p =
                parsear("{\"operacion\":\"REEMPLAZAR_AVANCES\",\"avancePorPeriodo\":{\"1\":\"1.23456\"}}", 12);
        assertEquals(400, p.getResponse().getStatus());
    }

    // ──────────────────────────────────────────────────────────────────────
    // DISTRIBUIR_UNIFORME
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void distribuir_rechaza_arreglo_vacio_o_duplicados_o_fuera_de_rango() {
        ProblemaException p1 = parsear("{\"operacion\":\"DISTRIBUIR_UNIFORME\",\"periodos\":[]}", 12);
        assertEquals(400, p1.getResponse().getStatus());
        ProblemaException p2 = parsear("{\"operacion\":\"DISTRIBUIR_UNIFORME\",\"periodos\":[3,3]}", 12);
        assertEquals(400, p2.getResponse().getStatus());
        ProblemaException p3 = parsear("{\"operacion\":\"DISTRIBUIR_UNIFORME\",\"periodos\":[1,13]}", 12);
        assertEquals(400, p3.getResponse().getStatus());
    }

    @Test
    void distribuir_normaliza_orden_y_acepta_periodos_ordenados() {
        ActividadProgramarRequest oper =
                AvancePatchParser.parsear(json("{\"operacion\":\"DISTRIBUIR_UNIFORME\",\"periodos\":[5,1,3]}"), 10);
        Distribuir d = (Distribuir) oper;
        assertEquals(java.util.List.of(1, 3, 5), d.periodos());
    }

    // ──────────────────────────────────────────────────────────────────────
    // MOVER_SEGMENTO
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void mover_rechaza_fuera_de_rango_y_delta_cero_y_segmento_invertido() {
        ProblemaException p1 = parsear("{\"operacion\":\"MOVER_SEGMENTO\",\"inicio\":0,\"fin\":1,\"delta\":1}", 12);
        assertEquals(400, p1.getResponse().getStatus());
        ProblemaException p2 = parsear("{\"operacion\":\"MOVER_SEGMENTO\",\"inicio\":4,\"fin\":2,\"delta\":1}", 12);
        assertEquals(400, p2.getResponse().getStatus());
        ProblemaException p3 = parsear("{\"operacion\":\"MOVER_SEGMENTO\",\"inicio\":1,\"fin\":1,\"delta\":0}", 12);
        assertEquals(400, p3.getResponse().getStatus());
        ProblemaException p4 = parsear("{\"operacion\":\"MOVER_SEGMENTO\",\"inicio\":5,\"fin\":5,\"delta\":8}", 12);
        assertEquals(400, p4.getResponse().getStatus());
    }

    @Test
    void mover_normaliza_origen_y_valida_destino() {
        ActividadProgramarRequest oper = AvancePatchParser.parsear(
                json("{\"operacion\":\"MOVER_SEGMENTO\",\"inicio\":2,\"fin\":4,\"delta\":-1}"), 12);
        Mover m = (Mover) oper;
        assertEquals(2, m.inicio());
        assertEquals(4, m.fin());
        assertEquals(-1, m.delta());
    }

    // ──────────────────────────────────────────────────────────────────────
    // REDIMENSIONAR_SEGMENTO
    // ──────────────────────────────────────────────────────────────────────

    @Test
    void redimensionar_rechaza_rango_destino_invertido_y_fuera_de_rango() {
        ProblemaException p1 = parsear(
                "{\"operacion\":\"REDIMENSIONAR_SEGMENTO\",\"inicio\":1,\"fin\":3,\"nuevoInicio\":4,\"nuevoFin\":3}",
                12);
        assertEquals(400, p1.getResponse().getStatus());
        ProblemaException p2 = parsear(
                "{\"operacion\":\"REDIMENSIONAR_SEGMENTO\",\"inicio\":2,\"fin\":2,\"nuevoInicio\":12,\"nuevoFin\":15}",
                10);
        assertEquals(400, p2.getResponse().getStatus());
    }

    @Test
    void redimensionar_normaliza_campos_y_acepta() {
        ActividadProgramarRequest oper = AvancePatchParser.parsear(
                json("{\"operacion\":\"REDIMENSIONAR_SEGMENTO\",\"inicio\":2,\"fin\":4,"
                        + "\"nuevoInicio\":1,\"nuevoFin\":3}"),
                12);
        Redimensionar r = (Redimensionar) oper;
        assertEquals(2, r.inicio());
        assertEquals(4, r.fin());
        assertEquals(1, r.nuevoInicio());
        assertEquals(3, r.nuevoFin());
    }

    @Test
    void distribucion_acepta_y_construye_lista() {
        ActividadProgramarRequest oper =
                AvancePatchParser.parsear(json("{\"operacion\":\"DISTRIBUIR_UNIFORME\",\"periodos\":[1,3,5]}"), 6);
        assertEquals(3, ((Distribuir) oper).periodos().size());
    }
}
