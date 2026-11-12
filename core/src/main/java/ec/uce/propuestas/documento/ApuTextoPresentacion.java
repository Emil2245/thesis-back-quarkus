package ec.uce.propuestas.documento;

import ec.uce.propuestas.motor.SeccionTipo;

/** Fixed APU block titles captured into the document projection; contains no financial arithmetic. */
public final class ApuTextoPresentacion {
    private ApuTextoPresentacion() {}

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
}
