package ec.uce.propuestas.presupuesto.dto;

import java.util.List;
import java.util.UUID;

/**
 * Plan 024 (P-31) — una de las dos versiones dentro de la respuesta de
 * comparación. Incluye el {@code totalGeneral} persistente y el desglose por
 * capítulo raíz (ordenado por {@code item} ascendente; sólo capítulos raíz, no
 * el sub-árbol completo, para mantener la respuesta compacta y enfocada en la
 * comparación de totales).
 *
 * <p>El contrato comercial sólo necesita el primer nivel del árbol; un
 * desglose recursivo se descartó para acotar el payload y porque la práctica
 * del usuario compara a nivel de capítulo raíz.</p>
 */
public record ComparacionItem(
        UUID presupuestoId, Short version, String totalGeneral, List<CapituloRaizComparacion> porCapituloRaiz) {}
