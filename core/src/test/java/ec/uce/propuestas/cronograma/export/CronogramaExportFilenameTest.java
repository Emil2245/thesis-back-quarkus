package ec.uce.propuestas.cronograma.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.cronograma.entity.Cronograma;
import ec.uce.propuestas.cronograma.export.CronogramaExportPreflightService.SnapshotCompleto;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Plan 031 (P-37, audit closure §filename) — RED para
 * {@link CronogramaDescargaService#construirFilename(SnapshotCompleto, String)}.
 *
 * <p>El nombre del archivo debe ser seguro para los filesystems comunes
 * (Windows, Unix, macOS) sin perder la identidad del proyecto:
 *
 * <ul>
 *   <li>Sin secuencias de BIGINT (≥ 6 dígitos consecutivos) — evita fuga de
 *       IDs internos.</li>
 *   <li>Sin path traversal (secuencias {@code ..} se aplanan).</li>
 *   <li>Sin caracteres prohibidos por Windows ({@code \ / : * ? " < > |}).</li>
 *   <li>Sin nombres reservados de Windows (CON, PRN, AUX, NUL,
 *       COM1..COM9, LPT1..LPT9), case-insensitive.</li>
 *   <li>Si codigo+nombre sólo producen subrayados/guiones o vacío, cae a
 *       {@code cronograma}.</li>
 *   <li>Longitud ≤ 80 caracteres antes de la extensión.</li>
 *   <li>La versión del presupuesto se preserva en el sufijo {@code -vN}.</li>
 * </ul>
 */
class CronogramaExportFilenameTest {

    private static SnapshotCompleto snap(String codigo, String nombre, Short version) {
        Proyecto p = new Proyecto();
        p.codigo = codigo;
        p.nombreProyecto = nombre;
        p.anio = (short) 2026;
        p.fechaInicio = LocalDate.parse("2026-01-01");
        Presupuesto pres = new Presupuesto();
        pres.version = version;
        Cronograma c = new Cronograma();
        return new SnapshotCompleto(
                c,
                pres,
                p,
                new BigDecimal("10.000000"),
                new BigDecimal("100.0000"),
                List.of(),
                false,
                false,
                false,
                LocalDate.parse("2026-01-01"));
    }

    @Test
    void filename_usa_codigo_y_nombre_con_separador_y_version() throws Exception {
        String f = construirFilename(snap("PROY-A", "Edificio Principal", (short) 3), "xlsx");
        assertEquals("PROY-A-Edificio_Principal-v3.xlsx", f);
    }

    @Test
    void filename_reemplaza_caracteres_prohibidos_windows() throws Exception {
        String f = construirFilename(snap("PRO/A", "Edificio<>Principal", (short) 1), "pdf");
        assertFalse(f.contains("/"), "filename no debe contener /");
        assertFalse(f.contains("<"), "filename no debe contener <");
        assertFalse(f.contains(">"), "filename no debe contener >");
        assertTrue(f.endsWith("-v1.pdf"), "conserva extension: " + f);
    }

    @Test
    void filename_elimina_secuencias_de_bigint() throws Exception {
        String f = construirFilename(snap("123456", "Rubro 9999999999", (short) 1), "xml");
        assertFalse(f.matches(".*\\d{6,}.*"), "filename no debe contener BIGINT (>=6 digitos): " + f);
    }

    @Test
    void filename_aplana_path_traversal() throws Exception {
        String f = construirFilename(snap("../../etc/passwd", "Rubro", (short) 1), "xlsx");
        assertFalse(f.contains(".."), "filename no debe contener ..: " + f);
        assertFalse(f.contains("/"), "filename no debe contener /: " + f);
        assertFalse(f.contains("\\"), "filename no debe contener \\: " + f);
    }

    @Test
    void filename_cae_a_cronograma_si_base_queda_vacia_o_solo_separadores() throws Exception {
        String f = construirFilename(snap("", "", (short) 1), "xlsx");
        assertEquals("cronograma-v1.xlsx", f);
    }

    @Test
    void filename_cae_a_cronograma_si_base_es_solo_separadores() throws Exception {
        String f = construirFilename(snap("___", "---", (short) 1), "xlsx");
        assertTrue(f.startsWith("cronograma-v"), "debe caer a cronograma: " + f);
    }

    @Test
    void filename_rechaza_nombres_reservados_windows() throws Exception {
        // El segundo param (nombre) vacío permite que la base tras sanear
        // sea exactamente el codigo — única forma de caer en el match exacto
        // de los nombres reservados.
        assertTrue(construirFilename(snap("CON", null, (short) 1), "xlsx").startsWith("cronograma-v"), "CON reservado");
        assertTrue(construirFilename(snap("prn", null, (short) 1), "xlsx").startsWith("cronograma-v"), "prn reservado");
        assertTrue(
                construirFilename(snap("COM1", null, (short) 1), "xlsx").startsWith("cronograma-v"), "COM1 reservado");
        assertTrue(
                construirFilename(snap("LPT9", null, (short) 1), "xlsx").startsWith("cronograma-v"), "LPT9 reservado");
        assertTrue(
                construirFilename(snap("nul", null, (short) 1), "xlsx").startsWith("cronograma-v"),
                "nul reservado case-insensitive");
        // CON-Aux NO es reservado (Windows reserva SOLO los tokens exactos).
        String f = construirFilename(snap("CON", "Aux", (short) 1), "xlsx");
        assertTrue(f.startsWith("CON"), "CON-Aux no es reservado: " + f);
    }

    @Test
    void filename_trunca_a_max_80_caracteres_antes_de_extension() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("a");
        }
        String f = construirFilename(snap("X", sb.toString(), (short) 1), "xlsx");
        // El total incluye -vN.xlsx; lo importante es que la base no exceda 80.
        int idxExt = f.lastIndexOf(".xlsx");
        int idxVersion = f.lastIndexOf("-v");
        assertTrue(idxVersion > 0 && idxExt > idxVersion, "filename conserva sufijo");
        String base = f.substring(0, idxVersion);
        assertTrue(base.length() <= 80, "base <= 80 caracteres (real=" + base.length() + ")");
    }

    @Test
    void filename_preserva_sufijo_de_version_siempre() throws Exception {
        String f = construirFilename(snap("P1", "Demo", (short) 7), "xml");
        assertTrue(f.contains("-v7.xml"), "version -v7.xml: " + f);
    }

    @Test
    void filename_version_null_cae_a_v1() throws Exception {
        String f = construirFilename(snap("P1", "Demo", null), "xlsx");
        assertTrue(f.contains("-v1.xlsx"), "version null -> v1: " + f);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Reflection helper — el método es package-private static.
    // ──────────────────────────────────────────────────────────────────────

    private static String construirFilename(SnapshotCompleto snap, String extension) throws Exception {
        Method m = CronogramaDescargaService.class.getDeclaredMethod(
                "construirFilename", SnapshotCompleto.class, String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, snap, extension);
    }
}
