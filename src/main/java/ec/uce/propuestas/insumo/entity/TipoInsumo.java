package ec.uce.propuestas.insumo.entity;

public enum TipoInsumo {
    EQUIPO,
    MANO_OBRA,
    MATERIAL,
    TRANSPORTE;

    /** Unidad fija NO editable para MO/Equipo según el schema. */
    public static boolean esUnidadFijaH(TipoInsumo tipo) {
        return tipo == EQUIPO || tipo == MANO_OBRA;
    }
}
