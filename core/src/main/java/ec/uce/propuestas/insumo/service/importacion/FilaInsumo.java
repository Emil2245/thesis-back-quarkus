package ec.uce.propuestas.insumo.service.importacion;

import ec.uce.propuestas.insumo.entity.TipoInsumo;
import java.math.BigDecimal;

/** Fila parseada y validada del CSV de insumos (P-15). Pura, sin I/O. */
public record FilaInsumo(
        String codigo,
        TipoInsumo tipo,
        String descripcion,
        String unidad,
        BigDecimal precioUnitario,
        String errorCampo,
        String errorMensaje) {

    public boolean valida() {
        return errorCampo == null && errorMensaje == null;
    }

    public static FilaInsumo error(String campo, String mensaje) {
        return new FilaInsumo(null, null, null, null, null, campo, mensaje);
    }
}
