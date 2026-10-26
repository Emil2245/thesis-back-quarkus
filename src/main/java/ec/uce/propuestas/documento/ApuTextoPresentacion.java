package ec.uce.propuestas.documento;

import ec.uce.propuestas.motor.SeccionTipo;
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
}
