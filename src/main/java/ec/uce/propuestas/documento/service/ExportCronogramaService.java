package ec.uce.propuestas.documento.service;

import ec.uce.propuestas.cronograma.entity.Actividad;
import ec.uce.propuestas.cronograma.entity.Cronograma;
import ec.uce.propuestas.cronograma.repository.ActividadRepository;
import ec.uce.propuestas.cronograma.repository.CronogramaRepository;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@ApplicationScoped
public class ExportCronogramaService {

    @Inject
    CronogramaRepository cronogramaRepository;

    @Inject
    ActividadRepository actividadRepository;

    @Inject
    RubroRepository rubroRepository;

    public byte[] generarXlsx(Long presupuestoId, int precisionDinero) throws IOException {
        Cronograma cron = cronogramaRepository
                .findByPresupuesto(presupuestoId)
                .orElseThrow(() -> new IllegalStateException("Cronograma no encontrado"));

        List<Actividad> actividades = actividadRepository.listByCronograma(cron.id);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Cronograma");
            int rowIdx = 0;

            Row title = sheet.createRow(rowIdx++);
            title.createCell(0)
                    .setCellValue("CRONOGRAMA DE EJECUCIÓN — " + cron.unidadTiempo + " (" + cron.numeroPeriodos + ")");
            rowIdx++;

            Row colHeader = sheet.createRow(rowIdx++);
            colHeader.createCell(0).setCellValue("Item");
            colHeader.createCell(1).setCellValue("Descripción");
            colHeader.createCell(2).setCellValue("Precio Total");
            colHeader.createCell(3).setCellValue("Peso (%)");
            for (int p = 1; p <= cron.numeroPeriodos; p++) {
                colHeader.createCell(3 + p).setCellValue("P" + p);
            }

            for (Actividad act : actividades) {
                Row r = sheet.createRow(rowIdx++);
                Optional<Rubro> rubroOpt = rubroRepository.findByIdOptional(act.rubroId);
                String item = rubroOpt.map(rb -> rb.item).orElse("—");
                String desc = rubroOpt.map(rb -> rb.descripcion).orElse("—");
                BigDecimal precioTotal = rubroOpt.map(rb -> rb.precioTotal).orElse(BigDecimal.ZERO);

                r.createCell(0).setCellValue(item);
                r.createCell(1).setCellValue(desc);
                setCellDecimal(r.createCell(2), precioTotal, precisionDinero);
                setCellDecimal(r.createCell(3), act.pesoPonderado.multiply(BigDecimal.valueOf(100)), 2);

                for (int p = 1; p <= cron.numeroPeriodos; p++) {
                    BigDecimal avance = act.avancePorPeriodo.get(String.valueOf(p));
                    if (avance != null && avance.signum() > 0) {
                        setCellDecimal(r.createCell(3 + p), avance.multiply(BigDecimal.valueOf(100)), 2);
                    }
                }
            }

            int totalCols = 4 + cron.numeroPeriodos;
            for (int i = 0; i < totalCols; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void setCellDecimal(Cell cell, BigDecimal value, int scale) {
        if (value != null) {
            cell.setCellValue(value.setScale(scale, RoundingMode.HALF_UP).doubleValue());
        }
    }
}
