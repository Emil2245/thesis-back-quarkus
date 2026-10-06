package ec.uce.propuestas.documento.exportacion;

import ec.uce.propuestas.documento.exportacion.PreflightDocumento.Detalle;
import ec.uce.propuestas.presupuesto.dto.ValidacionPresupuestoResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Fachada HTTP: evalúa únicamente la captura consistente interna. */
@ApplicationScoped
public class PreflightDocumentoService {
    @Inject
    CapturaDocumentoService captura;

    public PreflightDocumento evaluar(UUID id, Long caller, String documento, OpcionesDocumento opciones) {
        return captura.capturar(id, caller, documento, opciones).preflight();
    }

    /** Identity, not code, defines uniqueness; input is already selected-budget scoped. */
    public static List<Long> apuIdentidades(List<ec.uce.propuestas.presupuesto.entity.Rubro> seleccion) {
        return seleccion.stream().map(r -> r.apuId).distinct().toList();
    }

    /** Shared evaluator for future captured canonical P-32 lists, without recalculation. */
    public static List<Detalle> evaluarBloqueos(ValidacionPresupuestoResponse p32, boolean vacio, boolean apusVacios) {
        List<Detalle> result = new ArrayList<>();
        if (!p32.itemsPuCero().isEmpty())
            result.add(new Detalle(
                    "presupuesto-pu-cero", "Existen rubros con precio unitario cero (P-32)", p32.itemsPuCero()));
        if (!p32.itemsCantidadCero().isEmpty())
            result.add(new Detalle(
                    "presupuesto-cantidad-cero", "Existen rubros con cantidad cero (P-32)", p32.itemsCantidadCero()));
        if (!p32.itemsSinActividad().isEmpty())
            result.add(new Detalle(
                    "presupuesto-sin-actividad", "Existen rubros sin actividad (P-32)", p32.itemsSinActividad()));
        if (vacio) result.add(new Detalle("presupuesto-vacio", "El presupuesto no contiene rubros", List.of()));
        if (apusVacios) result.add(new Detalle("apus-vacios", "El presupuesto no referencia APUs", List.of()));
        return List.copyOf(result);
    }
}
