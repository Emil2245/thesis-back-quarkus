package ec.uce.propuestas.presupuesto.service;

import ec.uce.propuestas.common.ProblemaException;
import ec.uce.propuestas.presupuesto.dto.RubroRefResponse;
import ec.uce.propuestas.presupuesto.dto.ValidacionPresupuestoResponse;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Plan 025 (P-32) — capa de servicio de validación de integridad del
 * presupuesto. Endpoint
 * {@code GET /presupuestos/{presupuestoId}/validacion}; respuesta
 * {@link ValidacionPresupuestoResponse}.
 *
 * <p>Responsabilidad única: dada la identidad externa del presupuesto, calcular
 * tres listas planas e independientes de referencias a rubros con defectos y
 * derivar el flag {@code exportable}. El método es <strong>read-only</strong>:
 * no muta ninguna fila de la base; el contrato P-32 incluye explícitamente
 * esta garantía (TC-P32-13).</p>
 *
 * <p>Decisiones locked:
 * <ul>
 *   <li>Resolución del presupuesto con
 *       {@link PresupuestoRepository#findByPublicIdAndOwnerScope(UUID, Long)} —
 *       ajeno o inexistente se traduce en 404 {@code no-encontrado} (nunca
 *       403 — RNF-05).</li>
 *   <li>Defectos ortogonales: PU=0, cantidad=0 y falta de actividad se
 *       evalúan de forma independiente; un mismo rubro puede aparecer en
 *       varias listas si acumula varios defectos (TC-P32-05).</li>
 *   <li>{@code PU=0} se detecta con
 *       {@code precioUnitario.compareTo(BigDecimal.ZERO) == 0}; {@code cantidad=0}
 *       con la misma comparación sobre {@code cantidad}. Cada lista se computa
 *       por separado a partir del conjunto completo de rubros del presupuesto.</li>
 *   <li>Cobertura por actividad se delega a
 *       {@link PresupuestoRepository#findRubrosCubiertosPorCronograma(Long)}
 *       (SQL nativo narrow, mirroring Plan 024): los rubros cuyo {@code id}
 *       interno NO aparezca en ese conjunto son {@code sinActividad}. Si el
 *       presupuesto no tiene cronograma, el conjunto es vacío y todos los
 *       rubros quedan como defectuosos (TC-P32-04).</li>
 *   <li>Las tres listas se ordenan por {@code item} ascendente con tie-break
 *       estable por {@code publicId.toString()}; ningún rubro puede aparecer
 *       más de una vez dentro de la misma lista.</li>
 *   <li>{@code exportable} se deriva como
 *       {@code itemsPuCero.isEmpty() && itemsCantidadCero.isEmpty() &&
 *       itemsSinActividad.isEmpty()} — presupuesto sin rubros ni cronograma
 *       produce listas vacías y {@code exportable=true} (TC-P32-02).</li>
 * </ul>
 */
@ApplicationScoped
public class ValidacionPresupuestoService {

    @Inject
    PresupuestoRepository presupuestoRepository;

    @Inject
    RubroRepository rubroRepository;

    /**
     * Calcula los defectos de validación del presupuesto del path. Read-only:
     * ninguna operación de escritura ni flush.
     */
    public ValidacionPresupuestoResponse validar(UUID presupuestoPublicId, Long callerUsuarioId) {
        Presupuesto presupuesto = presupuestoRepository
                .findByPublicIdAndOwnerScope(presupuestoPublicId, callerUsuarioId)
                .orElseThrow(() -> ProblemaException.noEncontrado("Presupuesto no encontrado"));

        List<Rubro> rubros = rubroRepository.listarPorPresupuesto(presupuesto.id);
        if (rubros.isEmpty()) {
            return new ValidacionPresupuestoResponse(true, List.of(), List.of(), List.of());
        }

        Set<Long> cubiertos = presupuestoRepository.findRubrosCubiertosPorCronograma(presupuesto.id);

        List<RubroRefResponse> itemsPuCero = rubros.stream()
                .filter(r -> r.precioUnitario != null && r.precioUnitario.compareTo(BigDecimal.ZERO) == 0)
                .sorted(ORDEN_ITEM_UUID)
                .map(ValidacionPresupuestoService::toRef)
                .toList();

        List<RubroRefResponse> itemsCantidadCero = rubros.stream()
                .filter(r -> r.cantidad != null && r.cantidad.compareTo(BigDecimal.ZERO) == 0)
                .sorted(ORDEN_ITEM_UUID)
                .map(ValidacionPresupuestoService::toRef)
                .toList();

        List<RubroRefResponse> itemsSinActividad = rubros.stream()
                .filter(r -> !cubiertos.contains(r.id))
                .sorted(ORDEN_ITEM_UUID)
                .map(ValidacionPresupuestoService::toRef)
                .toList();

        boolean exportable = itemsPuCero.isEmpty() && itemsCantidadCero.isEmpty() && itemsSinActividad.isEmpty();
        return new ValidacionPresupuestoResponse(exportable, itemsPuCero, itemsCantidadCero, itemsSinActividad);
    }

    /** Orden estable: {@code item} ascendente, tie-break por UUID público. */
    private static final Comparator<Rubro> ORDEN_ITEM_UUID = Comparator.comparing(
                    (Rubro r) -> r.item == null ? "" : r.item)
            .thenComparing(r -> r.publicId == null ? "" : r.publicId.toString());

    /** Proyección reducida (4 campos) — preserva identidad pública UUIDv7. */
    private static RubroRefResponse toRef(Rubro r) {
        return new RubroRefResponse(r.publicId, r.item, r.codigo, r.descripcion);
    }
}
