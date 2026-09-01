package ec.uce.propuestas.presupuesto.mapper;

import ec.uce.propuestas.presupuesto.dto.CapituloResponse;
import ec.uce.propuestas.presupuesto.dto.PresupuestoResponse;
import ec.uce.propuestas.presupuesto.dto.PresupuestoVersionResponse;
import ec.uce.propuestas.presupuesto.dto.RubroResponse;
import ec.uce.propuestas.presupuesto.entity.Capitulo;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Plan 021 — mappers estáticos para el read model del agregado
 * {@code Presupuesto → Capitulo → Rubro}. Reglas de serialización:
 * <ul>
 *   <li>Identidad externa siempre {@code publicId} UUIDv7 (Plan 07 / WU-03).</li>
 *   <li>Decimales como cadena con escala contractual fija 6 y
 *       {@code HALF_UP} (P-30). {@code toDecimalString} nunca recorta ceros:
 *       {@code 10} se emite como {@code "10.000000"}.</li>
 *   <li>Árbol recursivo sin tope; hijos ordenados por {@code item}
 *       ascendente (lexicográfico coincide con orden natural "1", "1.1",
 *       "1.1.1", "2", …).</li>
 *   <li>Rubros ordenados por {@code item} dentro de cada capítulo.</li>
 * </ul>
 */
public final class PresupuestoMapper {

    /** Escala contractual para todos los decimales monetarios / cantidades. */
    static final int ESCALA = 6;

    private PresupuestoMapper() {}

    /**
     * Versión del presupuesto con su {@code origenId} ya resuelto a UUID
     * público (la entidad {@link Presupuesto} sólo conoce el BIGINT interno).
     * Si {@code origenPublicId} es {@code null}, el campo se omite vía
     * {@code @JsonInclude(NON_NULL)} en el DTO.
     */
    public static PresupuestoVersionResponse toVersionResponse(Presupuesto p, UUID origenPublicId) {
        return new PresupuestoVersionResponse(
                p.publicId, p.version, p.esVigente, origenPublicId, p.notas, p.createdAt, toDecimalString(p.total));
    }

    /**
     * Read model completo del árbol (cabecera + capítulos recursivos + rubros).
     * El parámetro {@code apuPublicos} mapea cada {@code BIGINT apuId} interno
     * a su UUID público para emitirlo como tal en el JSON (P-29 contrato).
     */
    public static PresupuestoResponse toPresupuestoResponseConInyecciones(
            Presupuesto p, List<Capitulo> capitulos, List<Rubro> rubros, Map<Long, UUID> apuPublicos) {
        Map<Long, List<Rubro>> rubrosPorCapitulo = new HashMap<>();
        for (Rubro r : rubros) {
            rubrosPorCapitulo
                    .computeIfAbsent(r.capituloId, k -> new ArrayList<>())
                    .add(r);
        }
        rubrosPorCapitulo.values().forEach(list -> list.sort(Comparator.comparing(r -> r.item)));

        Map<Long, List<Capitulo>> hijosPorPadre = new HashMap<>();
        List<Capitulo> raices = new ArrayList<>();
        for (Capitulo c : capitulos) {
            if (c.parentId == null) {
                raices.add(c);
            } else {
                hijosPorPadre
                        .computeIfAbsent(c.parentId, k -> new ArrayList<>())
                        .add(c);
            }
        }
        raices.sort(Comparator.comparing(c -> c.item));
        hijosPorPadre.values().forEach(list -> list.sort(Comparator.comparing(c -> c.item)));

        List<CapituloResponse> result = new ArrayList<>(raices.size());
        for (Capitulo raiz : raices) {
            result.add(toCapitulo(raiz, hijosPorPadre, rubrosPorCapitulo, apuPublicos));
        }
        return new PresupuestoResponse(p.publicId, p.version, p.esVigente, toDecimalString(p.total), result);
    }

    private static CapituloResponse toCapitulo(
            Capitulo c,
            Map<Long, List<Capitulo>> hijosPorPadre,
            Map<Long, List<Rubro>> rubrosPorCapitulo,
            Map<Long, UUID> apuPublicos) {
        List<CapituloResponse> hijos = hijosPorPadre.getOrDefault(c.id, List.of()).stream()
                .map(h -> toCapitulo(h, hijosPorPadre, rubrosPorCapitulo, apuPublicos))
                .toList();
        List<RubroResponse> rubros = rubrosPorCapitulo.getOrDefault(c.id, List.of()).stream()
                .map(r -> toRubro(r, apuPublicos.get(r.apuId)))
                .toList();
        return new CapituloResponse(
                c.publicId, c.item, c.descripcion, c.orden, toDecimalString(c.total), hijos, rubros);
    }

    private static RubroResponse toRubro(Rubro r, UUID apuPublicId) {
        return new RubroResponse(
                r.publicId,
                r.item,
                r.codigo,
                r.descripcion,
                r.unidad,
                toDecimalString(r.cantidad),
                toDecimalString(r.precioUnitario),
                toDecimalString(r.precioTotal),
                apuPublicId);
    }

    /**
     * Formato canónico contractual: escala fija 6, {@code HALF_UP}, siempre
     * 6 dígitos decimales visibles (incluido el cero, {@code "0.000000"}).
     * No se aplica {@code stripTrailingZeros()} para preservar un shape
     * estable entre respuestas: {@code 10} se serializa como
     * {@code "10.000000"}, no como {@code "10"}. Se usa {@code toPlainString()}
     * sobre el valor ya escalado para evitar la notación científica que
     * produciría {@code toString()} en escalados grandes (P-30).
     */
    static String toDecimalString(BigDecimal value) {
        BigDecimal nonNull = value == null ? BigDecimal.ZERO : value;
        return nonNull.setScale(ESCALA, RoundingMode.HALF_UP).toPlainString();
    }
}
