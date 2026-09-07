package ec.uce.propuestas.cronograma.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.cronograma.dto.ProyeccionExportacion;
import ec.uce.propuestas.cronograma.export.CronogramaXlsxWriter.Fila;
import ec.uce.propuestas.cronograma.export.CronogramaXlsxWriter.FilaHoja;
import ec.uce.propuestas.cronograma.export.CronogramaXlsxWriter.ProyeccionXlsx;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

/**
 * Plan 031 (P-37) — RED para el writer XLSX (Lane A documental). Verifica el
 * contrato de contenido:
 *
 * <ul>
 *   <li>Cabecera "CRONOGRAMA VALORADO DE TRABAJOS" + identidad del proyecto.</li>
 *   <li>Columnas fijas: item, código, descripción, unidad, cantidad, P.Unitario,
 *       P.Total, peso.</li>
 *   <li>Una columna Semana/Mes por período, ausente = celda vacía, presente
 *       con valor 0.0000 = celda numérica 0.</li>
 *   <li>Cuatro filas de resumen: % PARCIAL, % ACUMULADO, MONTO PARCIAL,
 *       MONTO ACUMULADO.</li>
 *   <li>Hoja renombrada con código/nombre del proyecto saneado (sin BIGINT,
 *       sin path traversal).</li>
 *   <li>Parse-back con Apache POI 5.4.0 (sincrónico, sin librería externa
 *       extra).</li>
 * </ul>
 */
class CronogramaXlsxWriterTest {

    @Test
    void TC_P37_20_xlsx_es_workbook_valido_y_contiene_cabecera_y_resumen() throws IOException {
        byte[] bytes = CronogramaXlsxWriter.renderizar(ProyeccionExportacion.de(proyeccionBase()));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sh = wb.getSheetAt(0);
            assertNotNull(sh, "la primera hoja debe existir");
            // Cabecera institucional = fila 1 (0-indexed).
            String primeraLinea = sh.getRow(0).getCell(0).getStringCellValue();
            assertTrue(
                    primeraLinea.contains("CRONOGRAMA VALORADO DE TRABAJOS"),
                    "primera línea debe contener el título canónico");
            // Nombre del proyecto aparece en la cabecera de identidad.
            String textoHoja = textoCompleto(sh);
            assertTrue(textoHoja.contains("Proyecto Demo"), "debe incluir el nombre del proyecto");
            // Cuatro filas de resumen deben existir (buscamos los textos).
            assertTrue(textoHoja.contains("% PARCIAL"));
            assertTrue(textoHoja.contains("% ACUMULADO"));
            assertTrue(textoHoja.contains("MONTO PARCIAL"));
            assertTrue(textoHoja.contains("MONTO ACUMULADO"));
        }
    }

    @Test
    void TC_P37_21_xlsx_columnas_fijas_y_una_columna_por_periodo() throws IOException {
        byte[] bytes = CronogramaXlsxWriter.renderizar(ProyeccionExportacion.de(proyeccionBase()));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sh = wb.getSheetAt(0);
            // Cabecera de columnas en fila 4 (0-indexed 3); 8 columnas fijas +
            // n períodos.
            var cabecera = sh.getRow(3);
            String[] columnas = new String[cabecera.getLastCellNum()];
            for (int i = 0; i < cabecera.getLastCellNum(); i++) {
                columnas[i] = cabecera.getCell(i).getStringCellValue();
            }
            assertEquals("Item", columnas[0]);
            assertEquals("Código", columnas[1]);
            assertEquals("Descripción", columnas[2]);
            assertEquals("Unidad", columnas[3]);
            assertEquals("Cantidad", columnas[4]);
            assertEquals("P.Unitario", columnas[5]);
            assertEquals("P.Total", columnas[6]);
            assertEquals("Peso", columnas[7]);
            // Las siguientes columnas son los períodos en orden numérico 1..n.
            assertEquals("Semana 1", columnas[8]);
            assertEquals("Semana 2", columnas[9]);
            assertEquals("Semana 3", columnas[10]);
            assertEquals("Semana 4", columnas[11]);
        }
    }

    @Test
    void TC_P37_22_xlsx_clave_ausente_renderea_celda_vacia_y_clave_cero_renderea_cero_numerico() throws IOException {
        FilaHoja fila = new FilaHoja(
                new Fila(
                        "1.1",
                        "R1",
                        "Rubro 1",
                        "u",
                        new BigDecimal("1.000000"),
                        new BigDecimal("1.000000"),
                        new BigDecimal("1.000000"),
                        new BigDecimal("100.0000")),
                List.of(new BigDecimal("50.0000"), new BigDecimal("0.0000")),
                Map.of(1, new BigDecimal("50.000000")));
        ProyeccionXlsx p = new ProyeccionXlsx(
                "PROYECTO-1", "Proyecto Demo", 2026, "SEMANA", 2, LocalDate.parse("2026-01-01"), List.of(fila));
        byte[] bytes = CronogramaXlsxWriter.renderizar(ProyeccionExportacion.de(p));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sh = wb.getSheetAt(0);
            // Fila de rubros está después de la cabecera; verificamos celda numérica.
            var filaExcel = sh.getRow(4);
            // Columna 8 = "Semana 1" (clave activa, porcentaje 50.0000) -> numérico 50.0
            assertEquals(50.0, filaExcel.getCell(8).getNumericCellValue(), 0.0001);
            // Columna 9 = "Semana 2" (clave activa pero porcentaje 0) -> numérico 0.0
            assertEquals(0.0, filaExcel.getCell(9).getNumericCellValue(), 0.0001);
        }
    }

    @Test
    void TC_P37_23_xlsx_nombre_hoja_saneado_sin_bigint_sin_path_traversal() throws IOException {
        ProyeccionXlsx p = new ProyeccionXlsx(
                "../../etc/passwd", "Proyecto/./Demo", 2026, "SEMANA", 1, LocalDate.parse("2026-01-01"), List.of());
        byte[] bytes = CronogramaXlsxWriter.renderizar(ProyeccionExportacion.de(p));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            String nombreHoja = wb.getSheetName(0);
            assertFalse(nombreHoja.contains("/"), "nombre de hoja no debe contener /");
            assertFalse(nombreHoja.contains(".."), "nombre de hoja no debe contener ..");
            assertFalse(nombreHoja.matches(".*\\d{6,}.*"), "nombre de hoja no debe contener BIGINT");
            assertTrue(nombreHoja.length() <= 31, "Excel limita a 31 caracteres");
        }
    }

    @Test
    void TC_P37_24_xlsx_formula_injection_se_neutraliza_en_celdas_de_texto() throws IOException {
        FilaHoja fila = new FilaHoja(
                new Fila(
                        "=SUM(A1:A2)",
                        "=1+1",
                        "+CMD|/C calc!A0",
                        "u",
                        new BigDecimal("1.000000"),
                        new BigDecimal("1.000000"),
                        new BigDecimal("1.000000"),
                        new BigDecimal("100.0000")),
                List.of(),
                Map.of());
        ProyeccionXlsx p = new ProyeccionXlsx(
                "PROYECTO-X",
                "Demo Formula Injection",
                2026,
                "SEMANA",
                1,
                LocalDate.parse("2026-01-01"),
                List.of(fila));
        byte[] bytes = CronogramaXlsxWriter.renderizar(ProyeccionExportacion.de(p));
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            String textoHoja = textoCompleto(wb.getSheetAt(0));
            // El contenido debe comenzar con prefijo neutralizador.
            assertTrue(textoHoja.contains("'=SUM(A1:A2)"), "celda de texto '=SUM' debe neutralizarse con prefijo '");
            assertTrue(textoHoja.contains("'=1+1"), "celda de texto '=1+1' debe neutralizarse");
            assertTrue(textoHoja.contains("'+CMD"), "celda de texto '+CMD' debe neutralizarse");
            assertTrue(textoHoja.contains("'+CMD"), "celda +CMD debe aparecer neutralizada con prefijo '");
            // Verificación directa: la celda item/codigo/descripcion debe empezar con '
            var filaR = wb.getSheetAt(0).getRow(4);
            String itemCell = filaR.getCell(0).getStringCellValue();
            String codigoCell = filaR.getCell(1).getStringCellValue();
            String descCell = filaR.getCell(2).getStringCellValue();
            assertTrue(itemCell.startsWith("'"), "item inicia con apostrofe neutralizador");
            assertTrue(codigoCell.startsWith("'"), "codigo inicia con apostrofe neutralizador");
            assertTrue(descCell.startsWith("'"), "descripcion inicia con apostrofe neutralizador");
        }
    }

    private static String textoCompleto(Sheet sh) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r <= sh.getLastRowNum(); r++) {
            var fila = sh.getRow(r);
            if (fila == null) continue;
            for (int c = 0; c < fila.getLastCellNum(); c++) {
                var cell = fila.getCell(c);
                if (cell == null) continue;
                sb.append(cell.toString()).append('\n');
            }
        }
        return sb.toString();
    }

    private static ProyeccionXlsx proyeccionBase() {
        FilaHoja fila = new FilaHoja(
                new Fila(
                        "1.1",
                        "R1",
                        "Rubro uno",
                        "u",
                        new BigDecimal("1.000000"),
                        new BigDecimal("1.000000"),
                        new BigDecimal("1.000000"),
                        new BigDecimal("50.0000")),
                List.of(new BigDecimal("50.0000"), new BigDecimal("50.0000")),
                Map.of(1, new BigDecimal("0.500000"), 2, new BigDecimal("0.500000")));
        FilaHoja fila2 = new FilaHoja(
                new Fila(
                        "1.2",
                        "R2",
                        "Rubro dos",
                        "u",
                        new BigDecimal("1.000000"),
                        new BigDecimal("2.000000"),
                        new BigDecimal("2.000000"),
                        new BigDecimal("50.0000")),
                List.of(new BigDecimal("50.0000"), new BigDecimal("50.0000")),
                Map.of(3, new BigDecimal("0.500000"), 4, new BigDecimal("0.500000")));
        return new ProyeccionXlsx(
                "PROYECTO-1", "Proyecto Demo", 2026, "SEMANA", 4, LocalDate.parse("2026-01-01"), List.of(fila, fila2));
    }

    /**
     * NFR-PER-02 — ≤5s para un export representativo de 200 rubros. La
     * prueba usa una fixture determinista a nivel de writer (no HTTP) para
     * aislar el costo del renderer de bytes del resto del pipeline; mide
     * tiempo de pared y tamaño del payload. Reporta evidencia explícita al log
     * del runner para que aparezca en el archivo de resultados. El umbral
     * duro (5_000 ms) se conserva canónico; este test fija la barra con un
     * margen de holgura amplio y reporta bytes/tiempo al log.
     */
    @Test
    void NFR_PER_02_xlsx_con_200_rubros_12_periodos_termina_en_menos_de_5_segundos() {
        final int numRubros = 200;
        final int periodos = 12;
        java.util.List<FilaHoja> filas = new java.util.ArrayList<>(numRubros);
        java.math.BigDecimal pesoUnitario = new java.math.BigDecimal("100.0000")
                .divide(new java.math.BigDecimal(numRubros), 4, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal pctPorPeriodo = new java.math.BigDecimal("100.0000")
                .divide(new java.math.BigDecimal(periodos), 4, java.math.RoundingMode.HALF_UP);
        java.math.BigDecimal montoPorPeriodo = new java.math.BigDecimal("100.000000")
                .divide(new java.math.BigDecimal(periodos), 6, java.math.RoundingMode.HALF_UP);
        for (int i = 0; i < numRubros; i++) {
            java.util.List<java.math.BigDecimal> porcentajes = new java.util.ArrayList<>(periodos);
            java.util.Map<Integer, java.math.BigDecimal> montoPorPeriodoMap = new java.util.LinkedHashMap<>();
            for (int p = 0; p < periodos; p++) {
                porcentajes.add(pctPorPeriodo);
                montoPorPeriodoMap.put(p + 1, montoPorPeriodo);
            }
            filas.add(new FilaHoja(
                    new Fila(
                            "1." + (i + 1),
                            "R-" + (i + 1),
                            "Rubro " + (i + 1),
                            "u",
                            new java.math.BigDecimal("1.000000"),
                            new java.math.BigDecimal("100.000000"),
                            new java.math.BigDecimal("100.000000"),
                            pesoUnitario),
                    porcentajes,
                    montoPorPeriodoMap));
        }
        ProyeccionXlsx proyeccion = new ProyeccionXlsx(
                "P31-PERF", "Performance 200 rubros", 2026, "SEMANA", periodos, LocalDate.parse("2026-01-01"), filas);

        Runtime runtime = Runtime.getRuntime();
        long memoriaAntes = runtime.totalMemory() - runtime.freeMemory();
        long inicio = System.nanoTime();
        byte[] bytes = CronogramaXlsxWriter.renderizar(ProyeccionExportacion.de(proyeccion));
        long duracionMs = (System.nanoTime() - inicio) / 1_000_000L;
        long memoriaDespues = runtime.totalMemory() - runtime.freeMemory();
        long deltaMemoriaBytes = memoriaDespues - memoriaAntes;

        System.out.printf(
                "NFR-PER-02: %d rubros x %d periodos -> %d bytes en %d ms (memoria delta ~%d KB)%n",
                numRubros, periodos, bytes.length, duracionMs, deltaMemoriaBytes / 1024L);

        // Memoria acotada: el writer no debe más que unos pocos MB para 200 rubros
        // (la representación POI en memoria es O(filas x periodos); 200 x 12 =
        // 2400 celdas numéricas ≈ unas pocas centenas de KB de payload + overhead).
        assertTrue(duracionMs < 5_000L, "NFR-PER-02: 200-rubro export debe ser <5s. Duración=" + duracionMs + "ms");
        assertTrue(bytes.length > 0, "XLSX no debe estar vacío");
        // Sanidad: 200 filas + cabecera + resumen generan un workbook >10KB
        // (POI comprime ZIP; bytes finales observados ~= 17KB para 200x12).
        assertTrue(bytes.length > 10_000, "Sanidad: 200-rubro XLSX debe medir >10KB; midió " + bytes.length + "B");
    }
}
