package ec.uce.propuestas.cronograma.export;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Valores MSPDI del cronograma capturados por Core; el XML se genera en Documentos. */
public final class MspdiCronograma {

    private MspdiCronograma() {}

    public record RubroMspdi(
            UUID rubroId,
            String item,
            String codigo,
            String descripcion,
            String unidad,
            BigDecimal cantidad,
            BigDecimal precioUnitario,
            BigDecimal precioTotal,
            BigDecimal pesoPonderado,
            List<Integer> periodosActivos,
            List<BigDecimal> porcentajesPorPeriodo) {}

    public record ProyeccionMspdi(
            UUID proyectoUid,
            String proyectoCodigo,
            String proyectoNombre,
            int anio,
            String unidadTiempo,
            int numeroPeriodos,
            LocalDate fechaInicio,
            List<RubroMspdi> rubros) {}
}
