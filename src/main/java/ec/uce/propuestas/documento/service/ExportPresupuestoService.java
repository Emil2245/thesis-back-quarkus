package ec.uce.propuestas.documento.service;

import ec.uce.propuestas.presupuesto.entity.Capitulo;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.entity.Rubro;
import ec.uce.propuestas.presupuesto.repository.CapituloRepository;
import ec.uce.propuestas.presupuesto.repository.RubroRepository;
import ec.uce.propuestas.presupuesto.service.PresupuestoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@ApplicationScoped
public class ExportPresupuestoService {

    @Inject
    PresupuestoService presupuestoService;

    @Inject
    CapituloRepository capituloRepository;

    @Inject
    RubroRepository rubroRepository;

    public byte[] generarXlsx(Long presupuestoId, int precisionDinero) throws IOException {
        Presupuesto p = presupuestoService.validar(presupuestoId);
        List<Capitulo> todosCaps = capituloRepository.listByPresupuesto(presupuestoId);

        Map<Long, List<Capitulo>> hijosPorPadre = new HashMap<>();
        for (Capitulo c : todosCaps) {
            hijosPorPadre.computeIfAbsent(c.parentId, k -> new ArrayList<>()).add(c);
        }

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Presupuesto");
            int rowIdx = 0;

            Row title = sheet.createRow(rowIdx++);
            title.createCell(0).setCellValue("PRESUPUESTO — Versión " + p.version);
            rowIdx++;

            Row colHeader = sheet.createRow(rowIdx++);
            colHeader.createCell(0).setCellValue("Item");
            colHeader.createCell(1).setCellValue("Código");
            colHeader.createCell(2).setCellValue("Descripción");
            colHeader.createCell(3).setCellValue("Unidad");
            colHeader.createCell(4).setCellValue("Cantidad");
            colHeader.createCell(5).setCellValue("P. Unitario");
            colHeader.createCell(6).setCellValue("P. Total");

            List<Capitulo> raices = hijosPorPadre.getOrDefault(null, Collections.emptyList());
            for (Capitulo raiz : raices) {
                rowIdx = escribirCapitulo(sheet, rowIdx, raiz, hijosPorPadre, precisionDinero, 0);
            }

            rowIdx++;
            Row totalRow = sheet.createRow(rowIdx);
            totalRow.createCell(2).setCellValue("TOTAL PRESUPUESTO:");
            setCellDecimal(totalRow.createCell(6), p.total, precisionDinero);

            for (int i = 0; i < 7; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private int escribirCapitulo(
            Sheet sheet, int rowIdx, Capitulo cap, Map<Long, List<Capitulo>> hijosPorPadre, int precision, int depth) {
        Row capRow = sheet.createRow(rowIdx++);
        String indent = "  ".repeat(depth);
        capRow.createCell(0).setCellValue(indent + cap.item);
        capRow.createCell(2).setCellValue(cap.descripcion);
        setCellDecimal(capRow.createCell(6), cap.total, precision);

        List<Rubro> rubros = rubroRepository.listByCapitulo(cap.id);
        for (Rubro r : rubros) {
            Row rRow = sheet.createRow(rowIdx++);
            rRow.createCell(0).setCellValue("  ".repeat(depth + 1) + r.item);
            rRow.createCell(1).setCellValue(r.codigo);
            rRow.createCell(2).setCellValue(r.descripcion);
            rRow.createCell(3).setCellValue(r.unidad);
            setCellDecimal(rRow.createCell(4), r.cantidad, precision);
            setCellDecimal(rRow.createCell(5), r.precioUnitario, precision);
            setCellDecimal(rRow.createCell(6), r.precioTotal, precision);
        }

        List<Capitulo> hijos = hijosPorPadre.getOrDefault(cap.id, Collections.emptyList());
        for (Capitulo hijo : hijos) {
            rowIdx = escribirCapitulo(sheet, rowIdx, hijo, hijosPorPadre, precision, depth + 1);
        }

        return rowIdx;
    }

    private void setCellDecimal(Cell cell, BigDecimal value, int scale) {
        if (value != null) {
            cell.setCellValue(value.setScale(scale, RoundingMode.HALF_UP).doubleValue());
        }
    }
}
