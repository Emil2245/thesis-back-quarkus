package ec.uce.propuestas.documento;

import ec.uce.propuestas.documento.exportacion.SnapshotDocumento.Firmante;
import ec.uce.propuestas.motor.SeccionTipo;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bounded, Unicode-safe presentation chunks and fixed literals; contains no financial arithmetic. */
public final class ApuTextoPresentacion {
    private ApuTextoPresentacion() {}

    public static List<String> fragmentos(String texto) {
        return PresupuestoXlsxWriter.fragmentos(PresupuestoXlsxWriter.seguro(texto));
    }

    /** Título de bloque según el spec de export §2.2 / CHK-03: literal, con sufijo (M)…(P) si está activo. */
    public static String tituloBloque(SeccionTipo tipo, boolean sufijos) {
        String nombre = switch (tipo) {
            case EQUIPO -> "EQUIPOS";
            case MANO_OBRA -> "MANO DE OBRA";
            case MATERIAL -> "MATERIALES";
            case TRANSPORTE -> "TRANSPORTE";
        };
        String sufijo = switch (tipo) {
            case EQUIPO -> "M";
            case MANO_OBRA -> "N";
            case MATERIAL -> "O";
            case TRANSPORTE -> "P";
        };
        return sufijos ? nombre + " (" + sufijo + ")" : nombre;
    }

    /**
     * Encabezados de columna A–F del bloque (spec §2.2 / CHK-04). M/N: {@code Descripción · Cantidad ·
     * Tarifa|Jornal/hr · Costo hora · Rendimiento · Costo}; O/P: {@code Descripción · Unidad · Cantidad ·
     * Precio unitario|Tarifa · (vacía) · Costo}. La columna vacía conserva el esquema de seis columnas.
     */
    public static List<String> encabezados(SeccionTipo tipo) {
        return switch (tipo) {
            case EQUIPO -> List.of("Descripción", "Cantidad", "Tarifa", "Costo hora", "Rendimiento", "Costo");
            case MANO_OBRA -> List.of("Descripción", "Cantidad", "Jornal/hr", "Costo hora", "Rendimiento", "Costo");
            case MATERIAL -> List.of("Descripción", "Unidad", "Cantidad", "Precio unitario", "", "Costo");
            case TRANSPORTE -> List.of("Descripción", "Unidad", "Cantidad", "Tarifa", "", "Costo");
        };
    }

    /** Línea del bloque de firmas: {@code titulo} marca los encabezados de sección (negrita). */
    public record LineaFirma(String texto, boolean titulo) {}

    /**
     * Bloque de firmantes del spec §5 / CHK-26: secciones {@code CONSOLIDADO POR:} y {@code APROBADO POR:}
     * (solo las que tienen firmantes), con {@code nombre\ncargo} por firmante en su orden y un slot
     * {@code FIRMA:} vacío (sin firma electrónica, fuera de alcance). Un rol fuera de esos dos (la BD no lo
     * permite) se emite al final bajo su propio encabezado {@code ROL:} para no perder al firmante.
     */
    public static List<LineaFirma> firmas(List<Firmante> firmantes) {
        var ordenados = firmantes.stream()
                .sorted(Comparator.comparingInt(Firmante::orden).thenComparing(f -> f.id().toString()))
                .toList();
        var porRol = new java.util.LinkedHashMap<String, List<Firmante>>();
        for (var rol : List.of("CONSOLIDADO", "APROBADO")) porRol.put(rol, new ArrayList<>());
        for (var f : ordenados)
            porRol.computeIfAbsent(f.rol(), r -> new ArrayList<>()).add(f);
        var lineas = new ArrayList<LineaFirma>();
        porRol.forEach((rol, delRol) -> {
            if (delRol.isEmpty()) return;
            boolean conocido = rol.equals("CONSOLIDADO") || rol.equals("APROBADO");
            lineas.add(new LineaFirma(PresupuestoXlsxWriter.seguro(rol) + (conocido ? " POR:" : ":"), true));
            for (var f : delRol) {
                lineas.add(new LineaFirma(
                        PresupuestoXlsxWriter.seguro(f.nombre()) + "\n" + PresupuestoXlsxWriter.seguro(f.cargo()),
                        false));
                lineas.add(new LineaFirma("FIRMA:", false));
            }
        });
        return List.copyOf(lineas);
    }
}
