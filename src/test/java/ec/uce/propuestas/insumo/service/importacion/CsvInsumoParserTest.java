package ec.uce.propuestas.insumo.service.importacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.insumo.entity.TipoInsumo;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvInsumoParserTest {

    private static List<FilaInsumo> parse(String csv) {
        return CsvInsumoParser.parse(csv.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void archivo_viejo_de_4_columnas_sigue_importando_como_material() {
        List<FilaInsumo> f = parse("codigo,descripcion,unidad,precio\nM-1,Cemento,kg,12.50\n");
        assertEquals(1, f.size());
        assertTrue(f.get(0).valida());
        assertEquals(TipoInsumo.MATERIAL, f.get(0).tipo());
        assertEquals("kg", f.get(0).unidad());
        assertEquals(new BigDecimal("12.50"), f.get(0).precioUnitario());
    }

    @Test
    void cinco_columnas_en_orden_mezclado_con_los_cuatro_tipos() {
        List<FilaInsumo> f = parse("precio,tipo,codigo,unidad,descripcion\n"
                + "1.5,MATERIAL,M-1,kg,Cemento\n"
                + "3.2,mo,MO-1,,Peón\n"
                + "10,Mano de obra,MO-2,h,Albañil\n"
                + "25,EQ,EQ-1,h,Mezcladora\n"
                + "0.8,TRANSPORTE,TR-1,m3,Volqueta\n");
        assertTrue(f.stream().allMatch(FilaInsumo::valida));
        assertEquals(
                List.of(
                        TipoInsumo.MATERIAL,
                        TipoInsumo.MANO_OBRA,
                        TipoInsumo.MANO_OBRA,
                        TipoInsumo.EQUIPO,
                        TipoInsumo.TRANSPORTE),
                f.stream().map(FilaInsumo::tipo).toList());
        assertEquals("h", f.get(1).unidad());
        assertEquals("Peón", f.get(1).descripcion());
        assertEquals(new BigDecimal("0.8"), f.get(4).precioUnitario());
    }

    @Test
    void celda_tipo_en_blanco_es_material() {
        List<FilaInsumo> f = parse("codigo,descripcion,tipo,unidad,precio\nM-1,Arena,,m3,5\n");
        assertEquals(TipoInsumo.MATERIAL, f.get(0).tipo());
    }

    @Test
    void tipo_invalido_es_error_de_fila() {
        FilaInsumo f = parse("codigo,descripcion,tipo,unidad,precio\nX-1,Algo,HERRAMIENTA,u,5\n")
                .get(0);
        assertFalse(f.valida());
        assertEquals("tipo", f.errorCampo());
    }

    @Test
    void mano_de_obra_sin_unidad_usa_h() {
        FilaInsumo f = parse("codigo,descripcion,tipo,unidad,precio\nMO-1,Peón,MANO_OBRA,,3\n")
                .get(0);
        assertTrue(f.valida());
        assertEquals("h", f.unidad());
    }

    @Test
    void equipo_con_unidad_m3_es_error() {
        FilaInsumo f = parse("codigo,descripcion,tipo,unidad,precio\nEQ-1,Grúa,EQUIPO,m3,3\n")
                .get(0);
        assertFalse(f.valida());
        assertEquals("unidad", f.errorCampo());
    }

    @Test
    void material_sin_unidad_es_error() {
        FilaInsumo f = parse("codigo,descripcion,unidad,precio\nM-1,Arena,,5\n").get(0);
        assertFalse(f.valida());
        assertEquals("unidad", f.errorCampo());
    }

    @Test
    void falta_columna_requerida_lanza() {
        assertThrows(IllegalArgumentException.class, () -> parse("codigo,descripcion,unidad\nM-1,Arena,m3\n"));
    }

    @Test
    void codigo_duplicado_y_precio_invalido_son_errores_de_fila() {
        List<FilaInsumo> f =
                parse("codigo,descripcion,unidad,precio\nM-1,A,kg,1\nM-1,B,kg,1\nM-2,C,kg,abc\nM-3,D,kg,0\n");
        assertTrue(f.get(0).valida());
        assertEquals("codigo", f.get(1).errorCampo());
        assertEquals("precio", f.get(2).errorCampo());
        assertEquals("precio", f.get(3).errorCampo());
    }
}
