package ec.uce.propuestas.cronograma.export;

import ec.uce.propuestas.cronograma.dto.BloqueoExportResponse;
import ec.uce.propuestas.cronograma.dto.CronogramaExportPreflightResponse;
import ec.uce.propuestas.cronograma.dto.WarningExportResponse;
import ec.uce.propuestas.cronograma.entity.Actividad;
import ec.uce.propuestas.cronograma.entity.Cronograma;
import ec.uce.propuestas.cronograma.mapper.CronogramaMapper;
import ec.uce.propuestas.cronograma.repository.ActividadRepository;
import ec.uce.propuestas.cronograma.repository.CronogramaRepository;
import ec.uce.propuestas.cronograma.service.PresupuestoFingerprint;
import ec.uce.propuestas.presupuesto.dto.ValidacionPresupuestoResponse;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.presupuesto.service.ValidacionPresupuestoService;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import ec.uce.propuestas.proyecto.service.ProyectoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plan 031 (P-37) — orquestación del preflight y de la generación documental
 * del cronograma. La regla única es:
 *
 * <pre>
 *   exportable = P-32 limpio
 *                && estadoDistribución == COMPLETO
 *                && toda desviación == 0.0000
 *                && avanceFinal == 100.0000
 *                && totalGeneral != 0
 *                && (formato != MSPDI || Proyecto.fechaInicio != null)
 * </pre>
 *
 * <p>El método {@link #evaluarPreflight(UUID, Long, FormatoExportacion)} aplica
 * esa regla sobre una sola lectura consistente (lock pesimista del
 * presupuesto, mismo fingerprint, mismo snapshot); los writers consumen el
 * mismo resultado para evitar TOCTOU (Plan 031 §TOCTOU).</p>
 *
 * <p>Los bloqueos P-32 se conservan como bloqueos separados de los bloqueos
 * propios del cronograma — ejes ortogonales (Plan 026 §4).</p>
 *
 * <p>El warning stale (<code>cronograma-desactualizado</code>) es no bloqueante
 * y aparece tanto en la respuesta de preflight como en la cabecera
 * {@code X-Cronograma-Desactualizado} de la descarga.</p>
 */
@ApplicationScoped
public class CronogramaExportPreflightService {

    private static final BigDecimal CIEN = new BigDecimal("100.0000");

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    CronogramaRepository cronogramaRepository;

    @Inject
    ActividadRepository actividadRepository;

    @Inject
    ProyectoService proyectoService;

    @Inject
    ValidacionPresupuestoService validacionPresupuestoService;

    /**
     * Snapshot inmutable sobre el que preflight y writers evalúan la regla
     * única. Toda la información derivada (totales, marcas, fingerprint) se
     * congela en este record para evitar relecturas intermedias y TOCTOU.
     */
    public record SnapshotCronograma(
            BigDecimal totalGeneral,
            BigDecimal avanceFinal,
            List<BloqueosCalculador.AvanceActividad> avances,
            boolean[] tienePuCero,
            boolean[] tieneCantidadCero,
            boolean[] tieneSinActividad,
            LocalDate fechaInicio) {}

    /**
     * Resultado del cálculo de bloqueos y advertencias: inmutable y
     * reutilizable entre preflight y writers.
     */
    public record ResultadoBloqueos(
            boolean exportable, List<BloqueoExportResponse> bloqueos, List<WarningExportResponse> advertencias) {}

    /**
     * Calculadora pura de la regla de exportabilidad. Toma un snapshot
     * inmutable y un formato y devuelve bloqueos/advertencias; no toca la
     * base ni abre archivos. Vive aquí para que sea trivialmente testeable en
     * aislamiento (sin Quarkus).
     */
    public static final class BloqueosCalculador {

        /** Avance de una sola actividad — entrada mínima para la regla. */
        public record AvanceActividad(
                UUID actividadId, BigDecimal peso, Map<Integer, BigDecimal> avancePorPeriodo, BigDecimal desviacion) {}

        private BloqueosCalculador() {}

        /** Forma conveniente para los tests y para los callers sin flag stale. */
        public static ResultadoBloqueos evaluar(SnapshotCronograma snap, FormatoExportacion formato) {
            return evaluar(snap, formato, false);
        }

        /**
         * Aplica la regla canónica:
         * <ol>
         *   <li>P-32 limpio (sin PU=0, sin cantidad=0, sin sin-actividad).</li>
         *   <li>estado BORRADOR (cualquier desviación ≠ 0.0000 o avance ≠ 100.0000).</li>
         *   <li>totalGeneral ≠ 0.</li>
         *   <li>MSPDI exige {@code Proyecto.fechaInicio} no nula.</li>
         * </ol>
         * El flag {@code desactualizado} se devuelve como warning sin afectar
         * la exportabilidad.
         */
        public static ResultadoBloqueos evaluar(
                SnapshotCronograma snap, FormatoExportacion formato, boolean desactualizado) {
            List<BloqueoExportResponse> bloqueos = new ArrayList<>();
            List<WarningExportResponse> advertencias = new ArrayList<>();

            if (snap.tienePuCero[0]) {
                bloqueos.add(BloqueoExportResponse.deCodigo(
                        "presupuesto-pu-cero", "Existen rubros con precio unitario cero (P-32)"));
            }
            if (snap.tieneCantidadCero[0]) {
                bloqueos.add(BloqueoExportResponse.deCodigo(
                        "presupuesto-cantidad-cero", "Existen rubros con cantidad cero (P-32)"));
            }
            if (snap.tieneSinActividad[0]) {
                bloqueos.add(BloqueoExportResponse.deCodigo(
                        "presupuesto-sin-actividad", "Existen rubros sin actividad (P-32)"));
            }

            if (snap.totalGeneral == null || snap.totalGeneral.compareTo(BigDecimal.ZERO) <= 0) {
                bloqueos.add(BloqueoExportResponse.deCodigo(
                        "cronograma-total-cero",
                        "El cronograma no puede exportarse porque el presupuesto no tiene rubros o su total es cero"));
            }

            boolean borradorPorDesviacion = false;
            for (AvanceActividad av : snap.avances) {
                if (av.desviacion == null || av.desviacion.compareTo(BigDecimal.ZERO) != 0) {
                    bloqueos.add(new BloqueoExportResponse(
                            "cronograma-desviacion",
                            av.actividadId,
                            "La actividad tiene desviación distinta de 0.0000"));
                    borradorPorDesviacion = true;
                }
            }

            boolean borradorPorAvance = snap.avanceFinal == null || snap.avanceFinal.compareTo(CIEN) != 0;
            if (borradorPorDesviacion || borradorPorAvance) {
                bloqueos.add(BloqueoExportResponse.deCodigo(
                        "cronograma-borrador",
                        "El cronograma está en BORRADOR (desviación o avance final distintos del 100%)"));
            }

            if (formato == FormatoExportacion.MSPDI && snap.fechaInicio == null) {
                bloqueos.add(BloqueoExportResponse.deCodigo(
                        "mspdi-fecha-inicio-requerida",
                        "MSPDI exige Proyecto.fechaInicio poblada; complétala para habilitar este lane"));
            }

            if (desactualizado) {
                advertencias.add(WarningExportResponse.stale(
                        "El total o fingerprint del presupuesto cambió desde la última revisión explícita"));
            }

            boolean exportable = bloqueos.isEmpty();
            return new ResultadoBloqueos(exportable, List.copyOf(bloqueos), List.copyOf(advertencias));
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Punto de entrada del preflight
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Resuelve el presupuesto owner-scoped, captura el snapshot canónico bajo
     * el lock del presupuesto, evalúa la regla y devuelve la respuesta
     * canonicalizada. Read-only — no muta ninguna fila.
     */
    @Transactional
    public CronogramaExportPreflightResponse evaluarPreflight(
            UUID presupuestoPublicId, Long callerUsuarioId, FormatoExportacion formato) {
        if (formato == null) {
            throw new IllegalArgumentException("formato es obligatorio");
        }
        SnapshotCompleto snap = cargarSnapshot(presupuestoPublicId, callerUsuarioId);
        boolean desactualizado = esStale(snap);
        ResultadoBloqueos r = BloqueosCalculador.evaluar(snap.comun(), formato, desactualizado);
        return new CronogramaExportPreflightResponse(r.exportable(), formato.token(), r.bloqueos(), r.advertencias());
    }

    // ──────────────────────────────────────────────────────────────────────
    // Snapshot canónico
    // ──────────────────────────────────────────────────────────────────────

    /** Snapshot completo: incluye la proyección común del cronograma y del proyecto. */
    public record SnapshotCompleto(
            Cronograma cronograma,
            Presupuesto presupuesto,
            Proyecto proyecto,
            BigDecimal totalGeneral,
            BigDecimal avanceFinal,
            List<BloqueosCalculador.AvanceActividad> avances,
            boolean tienePuCero,
            boolean tieneCantidadCero,
            boolean tieneSinActividad,
            LocalDate fechaInicio) {

        public SnapshotCronograma comun() {
            return new SnapshotCronograma(
                    totalGeneral,
                    avanceFinal,
                    avances,
                    new boolean[] {tienePuCero},
                    new boolean[] {tieneCantidadCero},
                    new boolean[] {tieneSinActividad},
                    fechaInicio);
        }
    }

    /**
     * Resuelve el presupuesto owner-scoped, adquiere el lock pesimista (en una
     * sola operación owner-scoped para cerrar la grieta owner-check / lock
     * deletion race — audit closure §lock race) y carga la proyección común
     * del cronograma. La carga abre 4 consultas acotadas: presupuesto,
     * actividades, validación P-32 y fingerprint.
     */
    @Transactional
    public SnapshotCompleto cargarSnapshot(UUID presupuestoPublicId, Long callerUsuarioId) {
        // Lock pesimista owner-scoped: si el presupuesto no pertenece al caller
        // o fue borrado, devuelve empty; el caller traduce a 404 (NO 500).
        Presupuesto presupuesto = presupuestoRepository
                .findByPublicIdOwnerScopeForUpdate(presupuestoPublicId, callerUsuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Presupuesto no encontrado"));

        Cronograma cronograma = cronogramaRepository
                .findByPresupuestoAndOwnerScope(presupuesto.id, callerUsuarioId)
                .orElseThrow(() -> new IllegalArgumentException("Cronograma no encontrado"));
        cronogramaRepository.getEntityManager().refresh(cronograma);

        Proyecto proyecto = proyectoService.validarPropietario(callerUsuarioId, presupuesto.proyectoId);

        List<Actividad> actividades = actividadRepository.listarPorCronograma(cronograma.id);

        List<BloqueosCalculador.AvanceActividad> avances = new ArrayList<>(actividades.size());
        BigDecimal avanceFinal = BigDecimal.ZERO.setScale(4);
        for (Actividad act : actividades) {
            Map<String, String> mapa = CronogramaMapper.leerMapa(act.avancePorPeriodo);
            BigDecimal peso = act.pesoPonderado == null ? BigDecimal.ZERO : act.pesoPonderado.setScale(4);
            BigDecimal suma = BigDecimal.ZERO.setScale(4);
            for (String v : mapa.values()) {
                suma = suma.add(new BigDecimal(v)).setScale(4, RoundingMode.HALF_UP);
            }
            BigDecimal desviacion = peso.subtract(suma).setScale(4, RoundingMode.HALF_UP);
            avanceFinal = avanceFinal.add(suma).setScale(4, RoundingMode.HALF_UP);

            Map<Integer, BigDecimal> enteros = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : mapa.entrySet()) {
                enteros.put(Integer.parseInt(e.getKey()), new BigDecimal(e.getValue()));
            }
            avances.add(new BloqueosCalculador.AvanceActividad(act.publicId, peso, enteros, desviacion));
        }

        // P-32: reutiliza el servicio de validación vigente. La consulta de
        // P-32 ya está implementada y validada (Plan 025), de modo que
        // mantenemos la única fuente de verdad sobre los defectos.
        ValidacionPresupuestoResponse val = validacionPresupuestoService.validar(presupuestoPublicId, callerUsuarioId);
        boolean tienePuCero = !val.itemsPuCero().isEmpty();
        boolean tieneCantidadCero = !val.itemsCantidadCero().isEmpty();
        boolean tieneSinActividad = !val.itemsSinActividad().isEmpty();

        BigDecimal total = presupuesto.total == null ? BigDecimal.ZERO : presupuesto.total.setScale(6);

        return new SnapshotCompleto(
                cronograma,
                presupuesto,
                proyecto,
                total,
                avanceFinal,
                List.copyOf(avances),
                tienePuCero,
                tieneCantidadCero,
                tieneSinActividad,
                proyecto.fechaInicio);
    }

    /**
     * Cálculo puro de stale: no abre tx; lo usa
     * {@link CronogramaDescargaService} desde dentro de su propia tx. Se
     * conserva público porque la regla del canon es la misma que aplica el
     * preflight (Plan 031 §Preflight / §Warning stale).
     */
    public boolean esStale(SnapshotCompleto snap) {
        if (snap.cronograma.totalGeneralRevisado == null || snap.cronograma.presupuestoFingerprintRevisado == null) {
            return true;
        }
        if (snap.cronograma.totalGeneralRevisado.setScale(6).compareTo(snap.totalGeneral) != 0) {
            return true;
        }
        String actual = calcularFingerprint(snap.presupuesto.id);
        return !snap.cronograma.presupuestoFingerprintRevisado.trim().equals(actual);
    }

    private String calcularFingerprint(Long presupuestoId) {
        List<Object[]> filas = actividadRepository.listarSnapshotPresupuesto(presupuestoId);
        List<PresupuestoFingerprint.RubroSnapshot> snaps = filas.stream()
                .map(f -> new PresupuestoFingerprint.RubroSnapshot(
                        (String) f[0], (String) f[1], (String) f[2], (BigDecimal) f[3]))
                .toList();
        return PresupuestoFingerprint.calcular(snaps);
    }
}
