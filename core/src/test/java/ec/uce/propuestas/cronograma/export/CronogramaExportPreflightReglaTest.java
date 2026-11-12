package ec.uce.propuestas.cronograma.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.cronograma.dto.BloqueoExportResponse;
import ec.uce.propuestas.cronograma.dto.CronogramaExportPreflightResponse;
import ec.uce.propuestas.cronograma.dto.WarningExportResponse;
import ec.uce.propuestas.cronograma.export.CronogramaExportPreflightService.BloqueosCalculador;
import ec.uce.propuestas.cronograma.export.CronogramaExportPreflightService.ResultadoBloqueos;
import ec.uce.propuestas.cronograma.export.CronogramaExportPreflightService.SnapshotCronograma;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Plan 031 (P-37) — RED para el evaluador determinista de exportabilidad que el
 * preflight y los writers comparten (Plan 026 §4 + 031 G4). El calculador
 * expone la regla única: P-32 limpio + estado COMPLETO + cada desviación
 * exactamente 0.0000 + avance final exactamente 100.0000.
 *
 * <p>MSPDI exige además {@code fechaInicio} poblada. La alerta stale es
 * warning no bloqueante; un cronograma completo + desactualizado sigue siendo
 * exportable a XLSX/PDF/MSPDI.</p>
 */
class CronogramaExportPreflightReglaTest {

    private static SnapshotCronograma snapshotCompleto() {
        Map<Integer, BigDecimal> avance = new LinkedHashMap<>();
        avance.put(1, new BigDecimal("40.0000"));
        avance.put(2, new BigDecimal("30.0000"));
        avance.put(3, new BigDecimal("30.0000"));
        List<BloqueosCalculador.AvanceActividad> avs = List.of(
                new BloqueosCalculador.AvanceActividad(
                        UUID.randomUUID(), new BigDecimal("50.0000"), avance, new BigDecimal("0.0000")),
                new BloqueosCalculador.AvanceActividad(
                        UUID.randomUUID(), new BigDecimal("50.0000"), avance, new BigDecimal("0.0000")));
        boolean[] tienePuCero = {false};
        boolean[] tieneCantidadCero = {false};
        boolean[] tieneSinActividad = {false};
        return new SnapshotCronograma(
                new BigDecimal("100.000000"),
                new BigDecimal("100.0000"),
                avs,
                tienePuCero,
                tieneCantidadCero,
                tieneSinActividad,
                java.time.LocalDate.parse("2026-01-01"));
    }

    @Test
    void TC_P37_01_completo_y_sin_defectos_es_exportable_para_xlsx_pdf_mspdi() {
        SnapshotCronograma snap = snapshotCompleto();
        ResultadoBloqueos r = BloqueosCalculador.evaluar(snap, FormatoExportacion.XLSX);
        assertTrue(r.exportable(), "cronograma completo + sin defectos debe ser exportable");
        assertTrue(r.bloqueos().isEmpty(), "sin defectos -> sin bloqueos");
        assertTrue(r.advertencias().isEmpty(), "sin stale -> sin warnings");
    }

    @Test
    void TC_P37_02_desviacion_distinta_de_cero_bloquea_exportacion() {
        SnapshotCronograma snap = snapshotCompleto();
        SnapshotCronograma conDesviacion = new SnapshotCronograma(
                snap.totalGeneral(),
                snap.avanceFinal(),
                List.of(new BloqueosCalculador.AvanceActividad(
                        UUID.randomUUID(),
                        new BigDecimal("50.0000"),
                        Map.of(1, new BigDecimal("49.9999")),
                        new BigDecimal("0.0001"))),
                snap.tienePuCero(),
                snap.tieneCantidadCero(),
                snap.tieneSinActividad(),
                snap.fechaInicio());
        ResultadoBloqueos r = BloqueosCalculador.evaluar(conDesviacion, FormatoExportacion.PDF);
        assertFalse(r.exportable(), "desviación != 0.0000 bloquea exportación");
        // Dos bloqueos: la desviación puntual + el estado BORRADOR derivado.
        List<String> codigos =
                r.bloqueos().stream().map(BloqueoExportResponse::codigo).toList();
        assertTrue(codigos.contains("cronograma-desviacion"), "cronograma-desviacion aparece");
        assertTrue(codigos.contains("cronograma-borrador"), "cronograma-borrador aparece cuando hay desviación");
    }

    @Test
    void TC_P37_03_avance_final_distinto_de_100_bloquea_exportacion() {
        SnapshotCronograma snap = snapshotCompleto();
        SnapshotCronograma parcial = new SnapshotCronograma(
                snap.totalGeneral(),
                new BigDecimal("99.9999"),
                snap.avances(),
                snap.tienePuCero(),
                snap.tieneCantidadCero(),
                snap.tieneSinActividad(),
                snap.fechaInicio());
        ResultadoBloqueos r = BloqueosCalculador.evaluar(parcial, FormatoExportacion.XLSX);
        assertFalse(r.exportable(), "avance final != 100.0000 bloquea exportación");
        assertTrue(
                r.bloqueos().stream().anyMatch(b -> "cronograma-borrador".equals(b.codigo())),
                "debe reportar el código cronograma-borrador por avance != 100");
    }

    @Test
    void TC_P37_04_estado_borrador_bloquea_exportacion() {
        SnapshotCronograma snap = snapshotCompleto();
        // Estado BORRADOR = el calculador ya lo detecta por desviación != 0 o avance != 100.
        // Aquí modelamos "BORRADOR" con avance 99.9999 (sin completar todas las claves).
        SnapshotCronograma borrador = new SnapshotCronograma(
                snap.totalGeneral(),
                new BigDecimal("80.0000"),
                List.of(new BloqueosCalculador.AvanceActividad(
                        UUID.randomUUID(),
                        new BigDecimal("80.0000"),
                        Map.of(1, new BigDecimal("80.0000")),
                        new BigDecimal("0.0000"))),
                snap.tienePuCero(),
                snap.tieneCantidadCero(),
                snap.tieneSinActividad(),
                snap.fechaInicio());
        ResultadoBloqueos r = BloqueosCalculador.evaluar(borrador, FormatoExportacion.XLSX);
        assertFalse(r.exportable());
        assertTrue(
                r.bloqueos().stream().anyMatch(b -> "cronograma-borrador".equals(b.codigo())),
                "estado BORRADOR debe aparecer como bloqueo explícito");
    }

    @Test
    void TC_P37_05_total_general_cero_bloquea_exportacion() {
        SnapshotCronograma snap = snapshotCompleto();
        SnapshotCronograma totalCero = new SnapshotCronograma(
                new BigDecimal("0.000000"),
                snap.avanceFinal(),
                snap.avances(),
                snap.tienePuCero(),
                snap.tieneCantidadCero(),
                snap.tieneSinActividad(),
                snap.fechaInicio());
        ResultadoBloqueos r = BloqueosCalculador.evaluar(totalCero, FormatoExportacion.PDF);
        assertFalse(r.exportable(), "totalGeneral == 0 bloquea exportación");
        assertTrue(
                r.bloqueos().stream().anyMatch(b -> "cronograma-total-cero".equals(b.codigo())),
                "código canónico cronograma-total-cero");
    }

    @Test
    void TC_P37_06_defectos_p32_se_conservan_ordenados_y_separados() {
        boolean[] pu = {true};
        boolean[] cant = {true};
        boolean[] sinAct = {true};
        SnapshotCronograma snap = new SnapshotCronograma(
                new BigDecimal("100.000000"),
                new BigDecimal("100.0000"),
                List.of(),
                pu,
                cant,
                sinAct,
                java.time.LocalDate.parse("2026-01-01"));
        ResultadoBloqueos r = BloqueosCalculador.evaluar(snap, FormatoExportacion.XLSX);
        assertFalse(r.exportable());
        List<String> codigos =
                r.bloqueos().stream().map(BloqueoExportResponse::codigo).toList();
        assertTrue(codigos.contains("presupuesto-pu-cero"), "presupuesto-pu-cero aparece preservado");
        assertTrue(codigos.contains("presupuesto-cantidad-cero"), "presupuesto-cantidad-cero aparece preservado");
        assertTrue(codigos.contains("presupuesto-sin-actividad"), "presupuesto-sin-actividad aparece preservado");
    }

    @Test
    void TC_P37_07_mspdi_bloquea_si_proyecto_sin_fecha_inicio() {
        SnapshotCronograma snap = snapshotCompleto();
        SnapshotCronograma sinFecha = new SnapshotCronograma(
                snap.totalGeneral(),
                snap.avanceFinal(),
                snap.avances(),
                snap.tienePuCero(),
                snap.tieneCantidadCero(),
                snap.tieneSinActividad(),
                null);
        ResultadoBloqueos r = BloqueosCalculador.evaluar(sinFecha, FormatoExportacion.MSPDI);
        assertFalse(r.exportable(), "MSPDI bloquea si fechaInicio es null");
        assertTrue(
                r.bloqueos().stream().anyMatch(b -> "mspdi-fecha-inicio-requerida".equals(b.codigo())),
                "código canónico mspdi-fecha-inicio-requerida");
        // Para XLSX/PDF sigue siendo exportable (no exigen fechaInicio).
        ResultadoBloqueos rXlsx = BloqueosCalculador.evaluar(sinFecha, FormatoExportacion.XLSX);
        assertTrue(rXlsx.exportable(), "XLSX no exige fechaInicio");
    }

    @Test
    void TC_P37_08_stale_es_warning_no_bloqueante_y_aparece_en_preflight() {
        SnapshotCronograma snap = snapshotCompleto();
        ResultadoBloqueos r = BloqueosCalculador.evaluar(snap, FormatoExportacion.XLSX, /* desactualizado = */ true);
        assertTrue(r.exportable(), "stale no bloquea exportación");
        assertTrue(
                r.advertencias().stream().anyMatch(w -> "cronograma-desactualizado".equals(w.codigo())),
                "stale aparece como warning no bloqueante");
        assertNotNull(r.advertencias().stream().findFirst().orElse(null));
    }

    @Test
    void TC_P37_09_respuesta_tiene_forma_canonica_con_formato_y_snapshot() {
        SnapshotCronograma snap = snapshotCompleto();
        ResultadoBloqueos r = BloqueosCalculador.evaluar(snap, FormatoExportacion.PDF);
        CronogramaExportPreflightResponse preflight =
                new CronogramaExportPreflightResponse(r.exportable(), "PDF", r.bloqueos(), r.advertencias());
        assertEquals("PDF", preflight.formato());
        assertTrue(preflight.bloqueos().isEmpty());
        assertNotNull(preflight.warnings(), "warnings no es null (puede ser lista vacía)");
    }

    @Test
    void TC_P37_10_warning_basico_construye_con_detalle() {
        WarningExportResponse w = new WarningExportResponse("cronograma-desactualizado", "Detalle");
        assertEquals("cronograma-desactualizado", w.codigo());
        assertEquals("Detalle", w.detalle());
    }
}
