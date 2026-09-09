package ec.uce.propuestas.presupuesto.service;

import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.service.ApuCalculoService;
import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.motor.ApuCalculado;
import ec.uce.propuestas.motor.SeccionTipo;
import ec.uce.propuestas.presupuesto.dto.ResumenComponentesResponse;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import ec.uce.propuestas.proyecto.entity.ParametrosProyecto;
import ec.uce.propuestas.proyecto.service.ParametrosProyectoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plan 023 — capa de servicio del resumen por componente (P-30).
 *
 * <p>Reglas de la respuesta (Plan 023, lock):
 * <ul>
 *   <li>{@code porComponente}: mapa {@code <SeccionTipo, String>} en orden
 *       canónico {@code EQUIPO, MANO_OBRA, MATERIAL, TRANSPORTE}
 *       (preservado por {@link LinkedHashMap}). Cada valor es la
 *       contribución directa M/N/O/P:
 *       {@code Σ (ApuCalculado.secciones[tipo].subtotal × rubro.cantidad)}.
 *       <b>Los componentes NO contienen CI</b> — son la suma directa del
 *       subtotal del APU multiplicado por la cantidad. Esto difiere del
 *       {@code totalGeneral}: el motor aplica la frontera APU→Rubro
 *       (PU_2dp_DOWN, PT retenido a escala 6) y absorbe CI antes de
 *       llegar al write-through de {@code rubro.precioTotal}, pero el
 *       componente sólo mira el {@code subtotal} del APU por sección.
 *       Esta disociación es intencional: el chart del frontend quiere
 *       ver el aporte directo por sección.</li>
 *   <li>{@code totalGeneral}: cadena decimal a escala 6 con
 *       {@code HALF_UP}. Proviene del write-through
 *       {@code presupuesto.total} (NO se suma de los componentes porque
 *       el CI y el rounding workbook-consistent rompen la igualdad).</li>
 *   <li>{@code ivaReferencial}: {@code totalGeneral × ParametrosProyecto.iva}
 *       con la precisión natural {@code BigDecimal}, redondeado a escala
 *       6 con {@code HALF_UP} sólo en la serialización.</li>
 *   <li>{@code totalConIva}: {@code totalGeneral + ivaReferencial} a
 *       precisión natural, escalado a 6 con {@code HALF_UP} sólo al
 *       serializar.</li>
 * </ul>
 *
 * <p>El servicio es estrictamente read-only — no abre transacciones
 * nuevas, no escribe en BD. Se anota {@code @Transactional} (REQUIRED,
 * default) por consistencia: si el caller ya tiene una transacción
 * abierta, comparte su contexto; sin transacción propia, Quarkus
 * gestiona la lectura con auto-commit. El método interno
 * {@link #seccionSubtotal(ApuCalculado, SeccionTipo)} reutiliza el
 * cálculo del motor a través de {@link ApuCalculoService#calcular(Apu)},
 * introducido en este plan como seam read-only.</p>
 *
 * <p>Errores (07-api-contract.md §6):
 * <ul>
 *   <li>404 {@code no-encontrado}: presupuesto ajeno o inexistente
 *       (RNF-05: nunca 403).</li>
 * </ul>
 *
 * <p>Decisiones locked:
 * <ul>
 *   <li>No se duplica la regla de redondeo workbook-consistent (la
 *       frontera APU→Rubro vive en {@code motor/internal/Consolidador});
 *       {@code ApuCalculoService.calcular} reutiliza el cálculo del
 *       motor tal cual.</li>
 *   <li>No se introduce cache de {@link ApuCalculado} (sería I-08 +
 *       P-26 stop condition; el árbol real IESS con 298 rubros
 *       permanece sub-segundo en esta versión).</li>
 * </ul>
 */
@ApplicationScoped
public class ResumenComponentesService {

    /** Escala contractual para serialización de decimales en el resumen (P-30). */
    private static final int ESCALA = 6;

    /** Orden canónico del {@code porComponente} en el JSON (P-30 contrato estable). */
    private static final List<SeccionTipo> ORDEN_CANONICO =
            List.of(SeccionTipo.EQUIPO, SeccionTipo.MANO_OBRA, SeccionTipo.MATERIAL, SeccionTipo.TRANSPORTE);

    /**
     * ApuCalculado neutro (todos los subtotales/totales a 0) usado como
     * defensa por si D-09 fallara y un rubro apuntase a un APU inexistente.
     * No se invoca al motor ni se toca la BD: evita que el resumen 500 al
     * construir un agregado parcialmente roto.
     */
    private static final ApuCalculado APU_CALCULADO_VACIO = new ApuCalculado(
            "",
            List.of(),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO);

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    RubroRepository rubroRepository;

    @Inject
    ApuRepository apuRepository;

    @Inject
    ApuCalculoService apuCalculoService;

    @Inject
    ParametrosProyectoService parametrosProyectoService;

    /**
     * Construye el resumen por componente del presupuesto. Read-only: no
     * escribe en BD. Devuelve {@code null}-free {@link ResumenComponentesResponse}.
     */
    @Transactional
    public ResumenComponentesResponse obtenerResumen(UUID presupuestoPublicId, Long callerUsuarioId) {
        Presupuesto presupuesto = presupuestoRepository
                .findByPublicIdAndOwnerScope(presupuestoPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));

        List<Rubro> rubros = rubroRepository.listarPorPresupuesto(presupuesto.id);

        // Acumuladores por sección (orden canónico EQUIPO, MANO_OBRA, MATERIAL, TRANSPORTE).
        Map<SeccionTipo, BigDecimal> acumulado = new LinkedHashMap<>();
        for (SeccionTipo tipo : ORDEN_CANONICO) {
            acumulado.put(tipo, BigDecimal.ZERO);
        }

        // Cache de ApuCalculado por apuId para no recalcular el mismo APU varias
        // veces si está en varios rubros (no debería pasar por D-09 1:1, pero
        // defendemos contra regresiones y reduce round-trips en fixtures de prueba).
        Map<Long, ApuCalculado> cache = new java.util.HashMap<>();

        for (Rubro r : rubros) {
            ApuCalculado calc = cache.computeIfAbsent(r.apuId, apuId -> {
                Apu apu = apuRepository.findById(apuId);
                if (apu == null) {
                    // D-09 garantiza 1:1 rubro↔apu. Defensa: si faltara el
                    // APU, devolvemos un ApuCalculado neutro (sin invocar
                    // al motor ni tocar la BD) para no romper la suma.
                    return APU_CALCULADO_VACIO;
                }
                return apuCalculoService.calcular(apu);
            });

            for (SeccionTipo tipo : ORDEN_CANONICO) {
                BigDecimal subtotal = seccionSubtotal(calc, tipo);
                BigDecimal contrib = subtotal.multiply(r.cantidad);
                acumulado.merge(tipo, contrib, BigDecimal::add);
            }
        }

        // Construye porComponente en LinkedHashMap con orden canónico
        LinkedHashMap<String, String> porComponente = new LinkedHashMap<>();
        for (SeccionTipo tipo : ORDEN_CANONICO) {
            porComponente.put(tipo.name(), escala6(acumulado.get(tipo)).toPlainString());
        }

        // totalGeneral: del write-through (NO de la suma de componentes)
        BigDecimal totalGeneral = presupuesto.total == null ? BigDecimal.ZERO : presupuesto.total;

        // IVA referencial: totalGeneral × ParametrosProyecto.iva (precisión natural).
        ParametrosProyecto params = parametrosProyectoService.obtenerEfectivosSinCrear(presupuesto.proyectoId);
        BigDecimal iva = params.iva == null ? BigDecimal.ZERO : params.iva;
        BigDecimal ivaReferencial = totalGeneral.multiply(iva);

        BigDecimal totalConIva = totalGeneral.add(ivaReferencial);

        return new ResumenComponentesResponse(
                porComponente,
                escala6(totalGeneral).toPlainString(),
                escala6(ivaReferencial).toPlainString(),
                escala6(totalConIva).toPlainString());
    }

    /**
     * Devuelve el subtotal de la sección del APU calculado. Reglas idénticas
     * a {@code motor/internal/Consolidador}: subtotalM incluye la fila HM,
     * subtotalN/O/P son las sumas directas de sus filas.
     */
    private static BigDecimal seccionSubtotal(ApuCalculado calc, SeccionTipo tipo) {
        return switch (tipo) {
            case EQUIPO -> calc.subtotalM();
            case MANO_OBRA -> calc.subtotalN();
            case MATERIAL -> calc.subtotalO();
            case TRANSPORTE -> calc.subtotalP();
        };
    }

    /** Escala fija 6 con HALF_UP — idéntico al contrato P-30 / PresupuestoMapper. */
    private static BigDecimal escala6(BigDecimal value) {
        BigDecimal nonNull = value == null ? BigDecimal.ZERO : value;
        return nonNull.setScale(ESCALA, RoundingMode.HALF_UP);
    }
}
