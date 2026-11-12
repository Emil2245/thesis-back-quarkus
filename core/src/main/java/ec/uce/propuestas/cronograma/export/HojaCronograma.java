package ec.uce.propuestas.cronograma.export;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Valores de la hoja del cronograma (filas, capítulos y encabezado) que la
 * proyección de exportación entrega a la captura de Documentos. Sin
 * dependencias de renderizado: el XLSX se genera en el servicio Documentos.
 */
public final class HojaCronograma {

    private HojaCronograma() {}

    /** Una fila de rubro en la hoja. */
    public record Fila(
            String item,
            String codigo,
            String descripcion,
            String unidad,
            BigDecimal cantidad,
            BigDecimal precioUnitario,
            BigDecimal precioTotal,
            BigDecimal peso) {}

    /**
     * Fila + periodos: el array {@code porcentajesPorPeriodo} (longitud = n)
     * lleva el porcentaje parcial por período (escala 4); el mapa
     * {@code montoPorPeriodo} lleva sólo los períodos activos (escala 6).
     */
    public record FilaHoja(
            Fila fila, List<BigDecimal> porcentajesPorPeriodo, Map<Integer, BigDecimal> montoPorPeriodo) {}

    /**
     * Fila de capítulo/subcapítulo: agrupación sin datos de actividad (CHK-22). {@code antesDeFila} es el
     * índice, dentro de {@code filas}, de la primera fila de rubro que se imprime después de este capítulo.
     */
    public record CapituloHoja(String item, String descripcion, BigDecimal total, int antesDeFila) {}

    /** Proyección completa que recibe el writer (una sola estructura inmutable). */
    public record ProyeccionXlsx(
            String proyectoCodigo,
            String proyectoNombre,
            int anio,
            String unidadTiempo,
            int numeroPeriodos,
            LocalDate fechaInicio,
            List<FilaHoja> filas,
            List<CapituloHoja> capitulos,
            String institucion,
            String subdireccion,
            int precisionDinero,
            int precisionPorcentaje) {
        public ProyeccionXlsx {
            capitulos = capitulos == null ? List.of() : List.copyOf(capitulos);
            institucion = institucion == null ? "" : institucion;
            subdireccion = subdireccion == null ? "" : subdireccion;
        }

        /** Sin capítulos, institución ni configuración de display: precisiones por defecto (2 y 4). */
        public ProyeccionXlsx(
                String proyectoCodigo,
                String proyectoNombre,
                int anio,
                String unidadTiempo,
                int numeroPeriodos,
                LocalDate fechaInicio,
                List<FilaHoja> filas) {
            this(
                    proyectoCodigo,
                    proyectoNombre,
                    anio,
                    unidadTiempo,
                    numeroPeriodos,
                    fechaInicio,
                    filas,
                    List.of(),
                    "",
                    "",
                    2,
                    4);
        }

        /** Texto de la fila de institución del encabezado, vacío si el proyecto no la tiene. */
        public String institucionVisible() {
            return java.util.stream.Stream.of(institucion, subdireccion)
                    .filter(s -> !s.isBlank())
                    .collect(java.util.stream.Collectors.joining(" · "));
        }

        /** {@code valor} a {@code precisionDinero} dp (HALF_UP), solo presentación. */
        public String dinero(BigDecimal valor) {
            return valor == null
                    ? ""
                    : valor.setScale(precisionDinero, java.math.RoundingMode.HALF_UP)
                            .toPlainString();
        }

        /** {@code valor} (ya en unidades de porcentaje) a {@code precisionPorcentaje} dp (HALF_UP). */
        public String porcentaje(BigDecimal valor) {
            return valor == null
                    ? ""
                    : valor.setScale(precisionPorcentaje, java.math.RoundingMode.HALF_UP)
                            .toPlainString();
        }
    }
}
