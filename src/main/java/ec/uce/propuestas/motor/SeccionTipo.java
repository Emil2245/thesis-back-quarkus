package ec.uce.propuestas.motor;

public enum SeccionTipo {
    EQUIPO,
    MANO_OBRA,
    MATERIAL,
    TRANSPORTE;

    /**
     * Letra del bloque en la hoja SERCOP (dossier §B.8): M equipo, N mano de
     * obra, O materiales, P transporte. Es presentación del contrato P-18, no
     * del motor: la aritmética nunca la consulta.
     *
     * <p>Plan 032 — vive junto al enum para que exista una sola tabla de
     * letras; dos tablas que pueden divergir es peor que ninguna.</p>
     */
    public String bloque() {
        return switch (this) {
            case EQUIPO -> "M";
            case MANO_OBRA -> "N";
            case MATERIAL -> "O";
            case TRANSPORTE -> "P";
        };
    }
}
