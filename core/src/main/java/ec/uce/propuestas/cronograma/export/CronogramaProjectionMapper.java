package ec.uce.propuestas.cronograma.export;

import ec.uce.propuestas.cronograma.dto.ProyeccionExportacion;
import ec.uce.propuestas.documentos.contract.CronogramaProjectionV1;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** Transfers Core-calculated values without recomputing money, weights or active periods. */
public final class CronogramaProjectionMapper {
    private CronogramaProjectionMapper() {}

    public static CronogramaProjectionV1 fromSheet(ProyeccionExportacion source, String format, String paper) {
        var hoja = source.hoja();
        var rows = hoja.filas().stream()
                .map(row -> {
                    var value = row.fila();
                    var amounts = row.montoPorPeriodo().entrySet().stream()
                            .sorted(Comparator.comparingInt(e -> e.getKey()))
                            .map(e -> new CronogramaProjectionV1.PeriodAmount(e.getKey(), text(e.getValue())))
                            .toList();
                    return new CronogramaProjectionV1.SheetRow(
                            value.item(),
                            value.codigo(),
                            value.descripcion(),
                            value.unidad(),
                            text(value.cantidad()),
                            text(value.precioUnitario()),
                            text(value.precioTotal()),
                            text(value.peso()),
                            row.porcentajesPorPeriodo().stream()
                                    .map(n -> n == null ? null : text(n))
                                    .toList(),
                            amounts);
                })
                .toList();
        var chapters = hoja.capitulos().stream()
                .map(c ->
                        new CronogramaProjectionV1.Chapter(c.item(), c.descripcion(), text(c.total()), c.antesDeFila()))
                .toList();
        var totals = new CronogramaProjectionV1.Totals(
                texts(source.parcialPorcentaje()),
                texts(source.acumuladoPorcentaje()),
                texts(source.parcialMonto()),
                texts(source.acumuladoMonto()));
        return new CronogramaProjectionV1(
                CronogramaProjectionV1.VERSION,
                format,
                paper,
                new CronogramaProjectionV1.Sheet(
                        hoja.proyectoCodigo(),
                        hoja.proyectoNombre(),
                        hoja.anio(),
                        hoja.unidadTiempo(),
                        hoja.numeroPeriodos(),
                        hoja.fechaInicio() == null ? null : hoja.fechaInicio().toString(),
                        hoja.institucion(),
                        hoja.subdireccion(),
                        hoja.precisionDinero(),
                        hoja.precisionPorcentaje(),
                        rows,
                        chapters,
                        totals),
                null);
    }

    public static CronogramaProjectionV1 fromMspdi(CronogramaMspdiWriter.ProyeccionMspdi source) {
        var rows = source.rubros().stream()
                .map(r -> new CronogramaProjectionV1.MspdiRow(
                        r.rubroId().toString(),
                        r.item(),
                        r.codigo(),
                        r.descripcion(),
                        r.unidad(),
                        text(r.cantidad()),
                        text(r.precioUnitario()),
                        text(r.precioTotal()),
                        text(r.pesoPonderado()),
                        r.periodosActivos(),
                        texts(r.porcentajesPorPeriodo())))
                .toList();
        return new CronogramaProjectionV1(
                CronogramaProjectionV1.VERSION,
                "mspdi",
                null,
                null,
                new CronogramaProjectionV1.Mspdi(
                        source.proyectoUid().toString(),
                        source.proyectoCodigo(),
                        source.proyectoNombre(),
                        source.anio(),
                        source.unidadTiempo(),
                        source.numeroPeriodos(),
                        source.fechaInicio().toString(),
                        rows));
    }

    private static String text(BigDecimal value) {
        return value.toPlainString();
    }

    private static List<String> texts(List<BigDecimal> values) {
        return values.stream().map(CronogramaProjectionMapper::text).toList();
    }
}
