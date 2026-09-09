package ec.uce.propuestas.proyecto.dto;

import ec.uce.propuestas.proyecto.entity.ValorReferencia;
import java.time.Instant;

public record ValorReferenciaResponse(
        String clave, String valor, String descripcion, String fuente, Instant actualizado) {

    public static ValorReferenciaResponse from(ValorReferencia valor) {
        return new ValorReferenciaResponse(valor.clave, valor.valor, valor.descripcion, valor.fuente, valor.updatedAt);
    }
}
