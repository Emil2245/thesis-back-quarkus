package ec.uce.propuestas.documento.service;

import ec.uce.propuestas.apu.entity.Apu;
import ec.uce.propuestas.apu.entity.ApuDetalle;
import ec.uce.propuestas.apu.entity.ApuSeccion;
import ec.uce.propuestas.apu.repository.ApuDetalleRepository;
import ec.uce.propuestas.apu.repository.ApuRepository;
import ec.uce.propuestas.apu.repository.ApuSeccionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@ApplicationScoped
public class ExportApuService {

    @Inject
    ApuRepository apuRepository;

    @Inject
    ApuSeccionRepository seccionRepository;

    @Inject
    ApuDetalleRepository detalleRepository;

    public byte[] generarXlsx(Long apuId, int precisionDinero) throws IOException {
        Apu apu = apuRepository.findByIdOptional(apuId).orElseThrow();
        List<ApuSeccion> secciones = seccionRepository.listarDeApu(apuId);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("APU " + apu.codigo);
            int rowIdx = 0;

            Row header = sheet.createRow(rowIdx++);
            header.createCell(0).setCellValue("ANÁLISIS DE PRECIOS UNITARIOS");
            Row codRow = sheet.createRow(rowIdx++);
            codRow.createCell(0).setCellValue("Código: " + apu.codigo);
            codRow.createCell(2).setCellValue("Descripción: " + apu.descripcion);
            Row uniRow = sheet.createRow(rowIdx++);
            uniRow.createCell(0).setCellValue("Unidad: " + apu.unidad);
            rowIdx++;

            for (ApuSeccion sec : secciones) {
                Row secRow = sheet.createRow(rowIdx++);
                secRow.createCell(0).setCellValue(sec.tipo.name());

                Row colHeader = sheet.createRow(rowIdx++);
                colHeader.createCell(0).setCellValue("Descripción");
                colHeader.createCell(1).setCellValue("Unidad");
                colHeader.createCell(2).setCellValue("Cantidad");
                colHeader.createCell(3).setCellValue("Precio Unit.");
                colHeader.createCell(4).setCellValue("Costo");

                List<ApuDetalle> detalles = detalleRepository.listarDeSeccion(sec.id);
                for (ApuDetalle d : detalles) {
                    Row dRow = sheet.createRow(rowIdx++);
                    dRow.createCell(0).setCellValue(d.descripcion != null ? d.descripcion : "");
                    dRow.createCell(1).setCellValue(d.unidad != null ? d.unidad : "");
                    setCellDecimal(dRow.createCell(2), d.cantidad, precisionDinero);
                    setCellDecimal(dRow.createCell(3), d.precioUnitarioTarifa, precisionDinero);
                    setCellDecimal(dRow.createCell(4), d.costo, precisionDinero);
                }

                Row subRow = sheet.createRow(rowIdx++);
                subRow.createCell(3).setCellValue("Subtotal:");
                setCellDecimal(subRow.createCell(4), sec.subtotal, precisionDinero);
                rowIdx++;
            }

            Row cdRow = sheet.createRow(rowIdx++);
            cdRow.createCell(3).setCellValue("Costo Directo:");
            setCellDecimal(cdRow.createCell(4), apu.costoDirecto, precisionDinero);

            Row ciRow = sheet.createRow(rowIdx++);
            ciRow.createCell(3).setCellValue("Costo Indirecto:");
            setCellDecimal(ciRow.createCell(4), apu.costoIndirecto, precisionDinero);

            Row ctRow = sheet.createRow(rowIdx);
            ctRow.createCell(3).setCellValue("Costo Total:");
            setCellDecimal(ctRow.createCell(4), apu.costoTotal, precisionDinero);

            for (int i = 0; i < 5; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generarTodosXlsx(Long presupuestoId, int precisionDinero) throws IOException {
        List<Apu> apus = apuRepository.list("presupuestoId", presupuestoId);
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            for (Apu apu : apus) {
                Sheet sheet = wb.createSheet(apu.codigo);
                writeApuToSheet(sheet, apu, precisionDinero);
            }
            if (apus.isEmpty()) {
                wb.createSheet("Sin APUs");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void writeApuToSheet(Sheet sheet, Apu apu, int precision) {
        List<ApuSeccion> secciones = seccionRepository.listarDeApu(apu.id);
        int rowIdx = 0;
        sheet.createRow(rowIdx++).createCell(0).setCellValue("APU: " + apu.codigo + " - " + apu.descripcion);
        sheet.createRow(rowIdx++).createCell(0).setCellValue("Unidad: " + apu.unidad);
        rowIdx++;

        for (ApuSeccion sec : secciones) {
            sheet.createRow(rowIdx++).createCell(0).setCellValue(sec.tipo.name());
            List<ApuDetalle> detalles = detalleRepository.listarDeSeccion(sec.id);
            for (ApuDetalle d : detalles) {
                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(d.descripcion != null ? d.descripcion : "");
                setCellDecimal(r.createCell(1), d.cantidad, precision);
                setCellDecimal(r.createCell(2), d.costo, precision);
            }
            Row sub = sheet.createRow(rowIdx++);
            sub.createCell(1).setCellValue("Subtotal:");
            setCellDecimal(sub.createCell(2), sec.subtotal, precision);
            rowIdx++;
        }
        Row ct = sheet.createRow(rowIdx);
        ct.createCell(1).setCellValue("TOTAL:");
        setCellDecimal(ct.createCell(2), apu.costoTotal, precision);
    }

    private void setCellDecimal(Cell cell, BigDecimal value, int scale) {
        if (value != null) {
            cell.setCellValue(value.setScale(scale, RoundingMode.HALF_UP).doubleValue());
        }
    }
}
