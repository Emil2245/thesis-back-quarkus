package ec.uce.propuestas.documento;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.documento.exportacion.ApuDocumentoProyeccion;
import ec.uce.propuestas.documento.exportacion.OpcionesDocumento;
import java.io.ByteArrayInputStream;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ApuXlsxWriterTest {
    private static final String[] LAYOUTS = {"pestanas", "apilado"};

    private static byte[] render(ApuDocumentoProyeccion.Documento d, String layout) {
        return ApuXlsxWriter.renderizar(d, new OpcionesDocumento("xlsx", Map.of("layout", layout)));
    }

    private static java.util.List<org.apache.poi.xssf.usermodel.XSSFRow> rows(XSSFWorkbook wb) {
        var result = new java.util.ArrayList<org.apache.poi.xssf.usermodel.XSSFRow>();
        for (var sheet : wb) {
            var sections = new java.util.HashSet<String>();
            String identity = "";
            int skipSchema = -1;
            for (var row : sheet) {
                var x = (org.apache.poi.xssf.usermodel.XSSFRow) row;
                if (printedIdentity(x)) {
                    if (label(x).startsWith("APU UUID: ") && !identity.equals(label(x))) {
                        identity = label(x);
                        sections.clear();
                    }
                    continue;
                }
                if (x.getRowNum() == skipSchema) continue;
                var next = (org.apache.poi.xssf.usermodel.XSSFRow) sheet.getRow(x.getRowNum() + 1);
                boolean heading = next != null
                        && label(next).equals("Descripción")
                        && next.getCell(5) != null
                        && next.getCell(5).getStringCellValue().equals("Costo")
                        && wb.getFontAt(x.getCell(0).getCellStyle().getFontIndex())
                                .getBold();
                if (heading && !sections.add(label(x))) {
                    skipSchema = next.getRowNum();
                    continue;
                }
                result.add(x);
            }
        }
        return result;
    }

    private static boolean printedIdentity(org.apache.poi.xssf.usermodel.XSSFRow row) {
        String text = label(row);
        int offset = text.startsWith("APU UUID: ")
                ? 0
                : text.startsWith("APU código: ") ? 1 : text.startsWith("APU descripción: ") ? 2 : -1;
        if (offset < 0 || row.getRowNum() < offset) return false;
        var start = row.getSheet().getRow(row.getRowNum() - offset);
        return start != null && label(start).matches("APU UUID: [0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    }

    private static String label(org.apache.poi.xssf.usermodel.XSSFRow row) {
        var c = row.getCell(0);
        return c == null ? "" : c.getStringCellValue();
    }

    private static org.apache.poi.xssf.usermodel.XSSFRow labelled(XSSFWorkbook wb, String label) {
        return rows(wb).stream().filter(r -> label(r).equals(label)).findFirst().orElseThrow();
    }

    private static void exact(org.apache.poi.xssf.usermodel.XSSFRow row, int column, java.math.BigDecimal value) {
        var cell = row.getCell(column);
        assertEquals(org.apache.poi.ss.usermodel.CellType.NUMERIC, cell.getCellType());
        assertEquals(value.toPlainString(), cell.getCTCell().getV());
    }

    private static ApuDocumentoProyeccion.Documento sample(int money, int percent, String detailText) {
        var original = ApuDocumentoProyeccion.proyectar(ApuDocumentoFixture.snapshot(true, true, null));
        var blocks = new java.util.ArrayList<ApuDocumentoProyeccion.Bloque>();
        int index = 0;
        for (var type : ec.uce.propuestas.motor.SeccionTipo.values()) {
            var value = new ec.uce.propuestas.documento.exportacion.SnapshotDocumento.Detalle(
                    new java.util.UUID(1, ++index),
                    (short) 1,
                    false,
                    detailText + type,
                    "u",
                    new java.math.BigDecimal("2.000001"),
                    new java.math.BigDecimal("0.000123456"),
                    new java.math.BigDecimal("999.00"),
                    new java.math.BigDecimal("888.00"),
                    new java.math.BigDecimal("-9.125000001"),
                    new java.math.BigDecimal("-10.500000002"),
                    new java.math.BigDecimal("-12.345000003"),
                    null);
            blocks.add(new ApuDocumentoProyeccion.Bloque(
                    type,
                    type.name(),
                    (short) index,
                    java.util.List.of(new ApuDocumentoProyeccion.Fila(value, value.descripcion(), null)),
                    new java.math.BigDecimal("-23.450000"),
                    true));
        }
        var a = original.apus().getFirst();
        var pie = new ApuDocumentoProyeccion.Pie(
                blocks.stream()
                        .map(b -> new ApuDocumentoProyeccion.Subtotal(b.tipo(), b.subtotal()))
                        .toList(),
                new java.math.BigDecimal("0.123456700"),
                new java.math.BigDecimal("-93.800000"),
                new java.math.BigDecimal("-7.89000"),
                new java.math.BigDecimal("-101.690000000"),
                new java.math.BigDecimal("-101.690000000"),
                "=footer");
        var analysis = new ApuDocumentoProyeccion.Analisis(
                a.id(), "same", "same", "description", "m²", null, null, blocks, pie, a.diagnostico());
        return new ApuDocumentoProyeccion.Documento(
                original.presupuestoId(),
                original.version(),
                original.proyecto(),
                original.parametros(),
                new ec.uce.propuestas.documento.exportacion.SnapshotDocumento.Display(money, percent),
                original.firmantes(),
                java.util.List.of(analysis));
    }

    private static java.util.Map<String, org.w3c.dom.Document> zipXml(byte[] bytes) throws Exception {
        var parts = new java.util.LinkedHashMap<String, org.w3c.dom.Document>();
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var zip = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                assertFalse(name.toLowerCase().contains("externallink"));
                assertFalse(name.toLowerCase().contains("vbaproject"));
                if (name.endsWith(".xml") || name.endsWith(".rels")) {
                    parts.put(name, factory.newDocumentBuilder().parse(new ByteArrayInputStream(zip.readAllBytes())));
                }
            }
        }
        return parts;
    }

    @Test
    void zipExactoTodosLosImportesNegativosYFormatosIndependientes() throws Exception {
        for (String layout : LAYOUTS)
            for (int precision : new int[] {0, 2}) {
                var d = sample(precision, 4, "detalle-");
                byte[] bytes = render(d, layout);
                var xml = zipXml(bytes);
                var sheetXml = xml.get("xl/worksheets/sheet1.xml");
                assertEquals(0, sheetXml.getElementsByTagName("f").getLength());
                assertEquals(0, sheetXml.getElementsByTagName("hyperlink").getLength());
                try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                    for (var block : d.apus().getFirst().bloques()) {
                        var detail = block.filas().getFirst().detalle();
                        var row = labelled(wb, detail.descripcion());
                        boolean hour = block.tipo() == ec.uce.propuestas.motor.SeccionTipo.EQUIPO
                                || block.tipo() == ec.uce.propuestas.motor.SeccionTipo.MANO_OBRA;
                        exact(row, hour ? 1 : 2, detail.cantidad());
                        exact(row, hour ? 2 : 3, detail.precioEfectivo());
                        if (hour) {
                            exact(row, 3, detail.costoHora());
                            exact(row, 4, detail.rendimiento());
                        }
                        exact(row, 5, detail.costo());
                        var formatter = new org.apache.poi.ss.usermodel.DataFormatter(java.util.Locale.US);
                        assertEquals(precision == 0 ? "-12" : "-12.35", formatter.formatCellValue(row.getCell(5)));
                        assertEquals("2.000001", formatter.formatCellValue(row.getCell(hour ? 1 : 2)));
                        if (hour) assertEquals("0.000123456", formatter.formatCellValue(row.getCell(4)));
                        assertEquals(
                                precision == 0 ? "0" : "0.00",
                                row.getCell(5).getCellStyle().getDataFormatString());
                        assertEquals(
                                "0.##############################",
                                row.getCell(hour ? 1 : 2).getCellStyle().getDataFormatString());
                        exact(labelled(wb, "Subtotal " + block.etiqueta()), 5, block.subtotal());
                        // Raw ZIP cell values, not POI's double representation.
                        var cells = sheetXml.getElementsByTagName("c");
                        for (var c : row)
                            if (c.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                                String address = c.getAddress().formatAsString();
                                boolean found = false;
                                for (int i = 0; i < cells.getLength(); i++) {
                                    var element = (org.w3c.dom.Element) cells.item(i);
                                    if (!address.equals(element.getAttribute("r"))) continue;
                                    assertEquals("n", element.getAttribute("t"));
                                    assertEquals(
                                            ((org.apache.poi.xssf.usermodel.XSSFCell) c)
                                                    .getCTCell()
                                                    .getV(),
                                            element.getElementsByTagName("v")
                                                    .item(0)
                                                    .getTextContent());
                                    found = true;
                                }
                                assertTrue(found);
                            }
                    }
                    var pie = d.apus().getFirst().pie();
                    exact(labelled(wb, "Costo directo"), 5, pie.costoDirecto());
                    exact(labelled(wb, "Costo indirecto"), 4, pie.porcentajeIndirecto());
                    exact(labelled(wb, "Costo indirecto"), 5, pie.costoIndirecto());
                    exact(labelled(wb, "Costo total"), 5, pie.costoTotal());
                    exact(labelled(wb, "Valor ofertado"), 5, pie.valorOfertado());
                    assertEquals(
                            "12.3457%",
                            new org.apache.poi.ss.usermodel.DataFormatter(java.util.Locale.US)
                                    .formatCellValue(
                                            labelled(wb, "Costo indirecto").getCell(4)));
                    assertEquals(
                            "0.0000%",
                            labelled(wb, "Costo indirecto")
                                    .getCell(4)
                                    .getCellStyle()
                                    .getDataFormatString());
                    for (var row : rows(wb))
                        for (var cell : row) {
                            if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
                                var elements = sheetXml.getElementsByTagName("c");
                                boolean found = false;
                                for (int i = 0; i < elements.getLength(); i++) {
                                    var element = (org.w3c.dom.Element) elements.item(i);
                                    if (!cell.getAddress().formatAsString().equals(element.getAttribute("r"))) continue;
                                    assertEquals("n", element.getAttribute("t"));
                                    assertEquals(
                                            ((org.apache.poi.xssf.usermodel.XSSFCell) cell)
                                                    .getCTCell()
                                                    .getV(),
                                            element.getElementsByTagName("v")
                                                    .item(0)
                                                    .getTextContent());
                                    found = true;
                                }
                                assertTrue(found);
                            }
                            assertNotEquals(org.apache.poi.ss.usermodel.CellType.FORMULA, cell.getCellType());
                            assertNull(cell.getHyperlink());
                        }
                    assertEquals(
                            org.apache.poi.ss.usermodel.CellType.STRING,
                            labelled(wb, "=footer").getCell(0).getCellType());
                }
                for (var part : xml.values()) {
                    var relations = part.getElementsByTagName("Relationship");
                    for (int i = 0; i < relations.getLength(); i++)
                        assertNotEquals(
                                "External", ((org.w3c.dom.Element) relations.item(i)).getAttribute("TargetMode"));
                }
            }
    }

    @Test
    void hmRespetaOrdenPorcentajeEfectivoEImportePersistido() throws Exception {
        var d = ApuDocumentoProyeccion.proyectar(
                ApuDocumentoFixture.snapshot(true, true, new java.math.BigDecimal("0.24680")));
        for (String layout : LAYOUTS)
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render(d, layout)))) {
                var all = rows(wb);
                var hm = all.stream()
                        .filter(r -> label(r).equals("Herramienta Menor 7.300%MO"))
                        .toList();
                assertEquals(2, hm.size());
                for (var row : hm) {
                    int position = all.indexOf(row);
                    assertEquals(ApuDocumentoFixture.TEXTO, label(all.get(position - 1)));
                    assertEquals(ApuDocumentoFixture.TEXTO, label(all.get(position - 2)));
                    exact(row, 5, new java.math.BigDecimal("12.000000003"));
                    exact(row, 1, new java.math.BigDecimal("2.000001"));
                    exact(row, 2, new java.math.BigDecimal("9.000000001"));
                    exact(row, 3, new java.math.BigDecimal("10.000000002"));
                }
                var ci = all.stream()
                        .filter(r -> label(r).equals("Costo indirecto"))
                        .toList();
                exact(ci.get(0), 4, new java.math.BigDecimal("0.1800"));
                exact(ci.get(1), 4, new java.math.BigDecimal("0.24680"));
                assertNotEquals(
                        d.apus().getFirst().diagnostico().costoTotal(),
                        d.apus().getFirst().pie().costoTotal());
                assertTrue(all.stream()
                        .filter(r -> label(r).equals(ApuDocumentoFixture.TEXTO))
                        .allMatch(r -> r.getCell(0).getCellType() == org.apache.poi.ss.usermodel.CellType.STRING));
            }
    }

    @Test
    void detallesLargosNoDuplicanNumerosYLayoutsSonEquivalentes() throws Exception {
        String longText = "=😃 á\t palabra\n".repeat(3500);
        var d = sample(2, 3, longText);
        java.util.List<String> first = null;
        for (String layout : LAYOUTS)
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render(d, layout)))) {
                var logical = new java.util.ArrayList<String>();
                for (var row : rows(wb)) {
                    assertTrue(row.getHeightInPoints() <= 240);
                    for (var cell : row) {
                        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                            assertTrue(cell.getStringCellValue().length() <= 32767);
                            logical.add(cell.getColumnIndex() + ":" + cell.getStringCellValue());
                        } else if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC)
                            logical.add(cell.getColumnIndex() + ":"
                                    + ((org.apache.poi.xssf.usermodel.XSSFCell) cell)
                                            .getCTCell()
                                            .getV());
                    }
                }
                if (first == null) first = logical;
                else assertEquals(first, logical);
                var all = rows(wb);
                for (var b : d.apus().getFirst().bloques()) {
                    int start = all.indexOf(labelled(wb, b.etiqueta())) + 2;
                    int end = all.indexOf(labelled(wb, "Subtotal " + b.etiqueta()));
                    var reconstructed = new StringBuilder();
                    for (int i = start; i < end; i++) {
                        var row = all.get(i);
                        reconstructed.append(label(row));
                        if (i != start)
                            for (int c = 1; c <= 5; c++) {
                                boolean unitText = c == 1
                                        && b.tipo() != ec.uce.propuestas.motor.SeccionTipo.EQUIPO
                                        && b.tipo() != ec.uce.propuestas.motor.SeccionTipo.MANO_OBRA
                                        && row.getCell(c) != null
                                        && row.getCell(c).getCellType() == org.apache.poi.ss.usermodel.CellType.STRING;
                                assertTrue(unitText
                                        || row.getCell(c) == null
                                        || row.getCell(c).getCellType() == org.apache.poi.ss.usermodel.CellType.BLANK);
                            }
                    }
                    assertEquals(b.filas().getFirst().descripcion(), reconstructed.toString());
                }
                assertEquals(
                        "0.000%",
                        labelled(wb, "Costo indirecto")
                                .getCell(4)
                                .getCellStyle()
                                .getDataFormatString());
            }
    }

    @Test
    void togglesIndependientesYResponsablesConfigurados() throws Exception {
        var s = ApuDocumentoFixture.snapshot(true, false, null);
        var p = s.parametros();
        // Each flag varies independently, including all-off and all-on combinations.
        for (int mask : new int[] {0, 1, 2, 4, 8, 16, 32, 64, 127}) {
            boolean empty = (mask & 1) != 0, suffix = (mask & 2) != 0, section = (mask & 4) != 0;
            boolean footerTotals = (mask & 8) != 0, project = (mask & 16) != 0, enumerate = (mask & 32) != 0;
            boolean footer = (mask & 64) != 0;
            var params = new ec.uce.propuestas.documento.exportacion.SnapshotDocumento.Parametros(
                    p.porcentajeHerramientaMenor(),
                    p.porcentajeIndirecto(),
                    p.ciIndividualHabilitado(),
                    p.iva(),
                    p.moneda(),
                    empty,
                    suffix,
                    section,
                    footerTotals,
                    project,
                    enumerate,
                    footer ? "@mensaje" : null,
                    p.modoCodigoRubro());
            var snapshot = new ec.uce.propuestas.documento.exportacion.SnapshotDocumento(
                    s.presupuestoId(),
                    s.version(),
                    s.vigente(),
                    s.notas(),
                    s.total(),
                    s.proyecto(),
                    params,
                    s.display(),
                    footer ? s.firmantes() : java.util.List.of(),
                    s.capitulos(),
                    s.apus(),
                    s.preflight());
            var d = ApuDocumentoProyeccion.proyectar(snapshot);
            for (String layout : LAYOUTS)
                try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render(d, layout)))) {
                    var labels = rows(wb).stream().map(ApuXlsxWriterTest::label).toList();
                    assertEquals(
                            2,
                            labels.stream()
                                    .filter(l -> l.equals("ANÁLISIS DE PRECIOS UNITARIOS"))
                                    .count());
                    assertEquals(
                            project ? 2 : 0,
                            labels.stream()
                                    .filter(l -> l.startsWith("Proyecto:"))
                                    .count());
                    assertEquals(
                            footer ? 2 : 0,
                            labels.stream().filter(l -> l.equals("@mensaje")).count());
                    int blockCount = empty ? 4 : 2;
                    assertEquals(
                            section ? blockCount * 2 : 0,
                            labels.stream()
                                    .filter(l -> l.startsWith("Subtotal ")
                                            && !l.matches("Subtotal (EQUIPO|MANO_OBRA|MATERIAL|TRANSPORTE)"))
                                    .count());
                    assertEquals(
                            footerTotals ? 8 : 0,
                            labels.stream()
                                    .filter(l -> l.matches("Subtotal (EQUIPO|MANO_OBRA|MATERIAL|TRANSPORTE)"))
                                    .count());
                    for (int i = 0; i < d.apus().size(); i++) {
                        assertTrue(labels.contains("Código: " + (enumerate ? (i + 1) + " " : "")
                                + d.apus().get(i).codigo()));
                        for (var b : d.apus().get(i).bloques()) {
                            assertEquals(suffix, b.etiqueta().matches(".* \\([MNOP]\\)$"));
                            assertTrue(labels.contains(b.etiqueta()));
                            int blockIndex = d.apus().get(i).bloques().indexOf(b);
                            if (blockIndex > 0)
                                assertTrue(labels.indexOf(d.apus()
                                                .get(i)
                                                .bloques()
                                                .get(blockIndex - 1)
                                                .etiqueta())
                                        < labels.indexOf(b.etiqueta()));
                        }
                    }
                    assertEquals(
                            empty ? 2 : 0,
                            labels.stream()
                                    .filter(l -> l.startsWith("Mano de obra"))
                                    .count());
                    assertEquals(
                            empty ? 2 : 0,
                            labels.stream()
                                    .filter(l -> l.startsWith("Transporte"))
                                    .count());
                    for (var f : s.firmantes()) {
                        String name = PresupuestoXlsxWriter.seguro(f.nombre()) + " · "
                                + PresupuestoXlsxWriter.seguro(f.cargo()) + " · "
                                + PresupuestoXlsxWriter.seguro(f.rol());
                        assertEquals(
                                footer ? 2 : 0,
                                labels.stream().filter(name::equals).count());
                    }
                }
        }
    }

    @Test
    void camposInstitucionalesCodigoUnidadPieYFirmantesTambienSeFragmentan() throws Exception {
        var base = sample(2, 0, "+literal");
        String largo = "@ñ😃\n".repeat(9000);
        var p = base.proyecto();
        var proyecto = new ec.uce.propuestas.documento.exportacion.SnapshotDocumento.Proyecto(
                p.id(),
                largo,
                p.codigo(),
                p.descripcion(),
                p.anio(),
                p.fechaInicio(),
                p.plazoEjecucion(),
                p.plazoUnidad(),
                p.estado(),
                largo,
                largo);
        var a = base.apus().getFirst();
        var pie = a.pie();
        var longPie = new ApuDocumentoProyeccion.Pie(
                pie.subtotales(),
                pie.porcentajeIndirecto(),
                pie.costoDirecto(),
                pie.costoIndirecto(),
                pie.costoTotal(),
                pie.valorOfertado(),
                largo);
        var longApu = new ApuDocumentoProyeccion.Analisis(
                a.id(), largo, largo, largo, largo, null, largo, a.bloques(), longPie, a.diagnostico());
        var f = new ec.uce.propuestas.documento.exportacion.SnapshotDocumento.Firmante(
                new java.util.UUID(3, 1), largo, largo, largo, (short) 1);
        var d = new ApuDocumentoProyeccion.Documento(
                base.presupuestoId(),
                base.version(),
                proyecto,
                base.parametros(),
                base.display(),
                java.util.List.of(f),
                java.util.List.of(longApu));
        for (String layout : LAYOUTS)
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render(d, layout)))) {
                var labels = rows(wb).stream().map(ApuXlsxWriterTest::label).toList();
                var expected = new java.util.ArrayList<String>();
                expected.add("ANÁLISIS DE PRECIOS UNITARIOS");
                for (String text : java.util.List.of(
                        largo, largo, "Proyecto: " + largo, "Código: " + largo, largo, "Unidad: " + largo))
                    expected.addAll(ApuTextoPresentacion.fragmentos(text));
                assertEquals(expected, labels.subList(0, expected.size()));
                var cierre = new java.util.ArrayList<String>();
                cierre.addAll(ApuTextoPresentacion.fragmentos(largo));
                cierre.addAll(ApuTextoPresentacion.fragmentos(largo + " · " + largo + " · " + largo));
                assertEquals(cierre, labels.subList(labels.size() - cierre.size(), labels.size()));
                for (var row : rows(wb))
                    for (var cell : row)
                        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                            assertTrue(cell.getStringCellValue().length() <= 32767);
                            assertTrue(row.getHeightInPoints() <= 240);
                        }
                assertEquals(
                        "0%",
                        labelled(wb, "Costo indirecto")
                                .getCell(4)
                                .getCellStyle()
                                .getDataFormatString());
            }
    }

    @Test
    void vacioNoInventaApus() throws Exception {
        for (String layout : LAYOUTS)
            try (var wb =
                    new XSSFWorkbook(new ByteArrayInputStream(render(documento(java.util.List.of(), ""), layout)))) {
                assertEquals(0, wb.getNumberOfSheets());
            }
    }

    @Test
    void nombresLimpianEspaciosYApostrofesExtremos() throws Exception {
        var d = documento(
                java.util.List.of("  'nombre'  ", "'", "[]:*?/\\\\", "X".repeat(30) + "'fin", "\t\n"), "texto");
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render(d, "pestanas")))) {
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                String name = wb.getSheetName(i);
                assertFalse(name.isBlank());
                assertTrue(name.length() <= 31);
                assertFalse(name.startsWith("'"));
                assertFalse(name.endsWith("'"));
                assertFalse(name.matches(".*[\\p{Cc}].*"));
            }
            assertEquals("nombre", wb.getSheetName(0));
            assertEquals("APU", wb.getSheetName(1));
        }
    }

    private ApuDocumentoProyeccion.Documento documento(java.util.List<String> codigos, String descripcion) {
        var d = ApuDocumentoProyeccion.proyectar(ApuDocumentoFixture.snapshot(true, true, null));
        var a = d.apus().getFirst();
        var apus = new java.util.ArrayList<ApuDocumentoProyeccion.Analisis>();
        for (int i = 0; i < codigos.size(); i++) {
            apus.add(new ApuDocumentoProyeccion.Analisis(
                    new java.util.UUID(7, i + 1),
                    codigos.get(i),
                    codigos.get(i),
                    descripcion,
                    a.unidad(),
                    a.especificacionTecnica(),
                    a.nombreProyectoHeader(),
                    a.bloques(),
                    a.pie(),
                    a.diagnostico()));
        }
        return new ApuDocumentoProyeccion.Documento(
                d.presupuestoId(), d.version(), d.proyecto(), d.parametros(), d.display(), d.firmantes(), apus);
    }

    @Test
    void nombresHostilesDeterministasSinFusionarIdentidades() throws Exception {
        var d = documento(
                java.util.List.of(
                        "'[]:*?/\\\\'",
                        "",
                        "History",
                        "A".repeat(40),
                        "a".repeat(40),
                        "😃".repeat(30),
                        "'fin'",
                        "NUL\u0000control"),
                "Descripción");
        var o = new OpcionesDocumento("xlsx", Map.of("layout", "pestanas"));
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(ApuXlsxWriter.renderizar(d, o)));
                var segundo = new XSSFWorkbook(new ByteArrayInputStream(ApuXlsxWriter.renderizar(d, o)))) {
            var nombres = new java.util.HashSet<String>();
            assertEquals(8, wb.getNumberOfSheets());
            for (int i = 0; i < 8; i++) {
                String nombre = wb.getSheetName(i);
                org.apache.poi.ss.util.WorkbookUtil.validateSheetName(nombre);
                assertTrue(nombres.add(nombre.toLowerCase(java.util.Locale.ROOT)));
                assertEquals(nombre, segundo.getSheetName(i));
            }
        }
    }

    @Test
    void textoLargoCompletoYSaltosDinamicos() throws Exception {
        String largo = "á 😃 palabra\n".repeat(4000);
        var d = documento(java.util.List.of("primero", "segundo"), largo);
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(
                ApuXlsxWriter.renderizar(d, new OpcionesDocumento("xlsx", Map.of("layout", "apilado")))))) {
            var sh = wb.getSheetAt(0);
            var reconstruido = new StringBuilder();
            boolean descripcion = false;
            for (var row : sh) {
                assertTrue(row.getHeightInPoints() <= 240);
                var cell = row.getCell(0);
                if (cell == null || cell.getCellType() != org.apache.poi.ss.usermodel.CellType.STRING) continue;
                String text = cell.getStringCellValue();
                assertTrue(text.length() <= 32767);
                if (printedIdentity((org.apache.poi.xssf.usermodel.XSSFRow) row)) continue;
                if (text.equals("Código: primero")) {
                    descripcion = true;
                    continue;
                }
                if (descripcion && text.startsWith("Unidad:")) break;
                if (descripcion) reconstruido.append(text);
            }
            assertEquals(largo, reconstruido.toString());
            assertTrue(sh.getRowBreaks().length > 1);
            assertTrue(sh.getRowBreaks()[0] < 85);
            assertTrue(wb.getNumCellStyles() < 12);
        }
    }

    @Test
    void cadaPaginaManualConservaIdentidadAcotadaYPresupuestoFisico() throws Exception {
        var d = documento(java.util.List.of("primero", "segundo", "tercero"), "descripción\n".repeat(4000));
        for (String layout : LAYOUTS)
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render(d, layout)))) {
                assertEquals(layout.equals("apilado") ? 1 : 3, wb.getNumberOfSheets());
                for (var sheet : wb) {
                    assertTrue(sheet.getRowBreaks().length > 3);
                    String identity = null;
                    double height = 0;
                    var breaks = java.util.Arrays.stream(sheet.getRowBreaks())
                            .boxed()
                            .collect(java.util.stream.Collectors.toSet());
                    for (var row : sheet) {
                        String text = label((org.apache.poi.xssf.usermodel.XSSFRow) row);
                        if (height == 0) {
                            assertTrue(text.startsWith("APU UUID: "), text);
                            identity = text;
                            assertTrue(d.apus().stream()
                                    .anyMatch(a -> identityText(a).equals(text)));
                            assertTrue(label((org.apache.poi.xssf.usermodel.XSSFRow) sheet.getRow(row.getRowNum() + 1))
                                    .startsWith("APU código: "));
                            assertTrue(label((org.apache.poi.xssf.usermodel.XSSFRow) sheet.getRow(row.getRowNum() + 2))
                                    .startsWith("APU descripción: "));
                        }
                        if (printedIdentity((org.apache.poi.xssf.usermodel.XSSFRow) row)) {
                            if (text.startsWith("APU UUID: ")) assertEquals(identity, text);
                            assertTrue(text.length() <= 110);
                            for (int column = 1; column <= 5; column++) assertNull(row.getCell(column));
                            assertTrue(row.getCell(0).getCellStyle().getWrapText());
                            assertFalse(row.getCell(0).getCellStyle().getShrinkToFit());
                        }
                        height += row.getHeightInPoints();
                        assertTrue(height <= 650, "page height " + height);
                        if (breaks.contains(row.getRowNum())) height = 0;
                    }
                    assertTrue(height > 0);
                }
            }
    }

    private static String identityText(ApuDocumentoProyeccion.Analisis a) {
        return "APU UUID: " + a.id();
    }

    @Test
    void seccionesNoQuedanHuerfanasYContinuanConSuEsquema() throws Exception {
        var base = sample(2, 3, "body|".repeat(5000));
        var a = base.apus().getFirst();
        var blocks = new java.util.ArrayList<ApuDocumentoProyeccion.Bloque>(a.bloques());
        var last = blocks.getLast();
        blocks.set(
                blocks.size() - 1,
                new ApuDocumentoProyeccion.Bloque(
                        last.tipo(),
                        last.etiqueta(),
                        last.ordenCapturado(),
                        java.util.List.of(),
                        last.subtotal(),
                        true));
        var analysis = new ApuDocumentoProyeccion.Analisis(
                a.id(),
                a.codigo(),
                a.etiquetaCodigo(),
                "x".repeat(900),
                a.unidad(),
                a.especificacionTecnica(),
                a.nombreProyectoHeader(),
                blocks,
                a.pie(),
                a.diagnostico());
        var d = new ApuDocumentoProyeccion.Documento(
                base.presupuestoId(),
                base.version(),
                base.proyecto(),
                base.parametros(),
                base.display(),
                base.firmantes(),
                java.util.List.of(analysis));
        for (String layout : LAYOUTS)
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render(d, layout)))) {
                var sheet = wb.getSheetAt(0);
                var breaks = java.util.Arrays.stream(sheet.getRowBreaks())
                        .boxed()
                        .collect(java.util.stream.Collectors.toSet());
                String active = null, schema = null;
                int headings = 0;
                for (var raw : sheet) {
                    var row = (org.apache.poi.xssf.usermodel.XSSFRow) raw;
                    String text = label(row);
                    if (text.startsWith("APU UUID: ")) schema = null;
                    var block = blocks.stream()
                            .filter(b -> b.etiqueta().equals(text))
                            .findFirst();
                    if (block.isPresent()) {
                        active = text;
                        headings++;
                        assertFalse(breaks.contains(row.getRowNum()), "orphan section " + text);
                        var columns = sheet.getRow(row.getRowNum() + 1);
                        assertEquals("Descripción", label(columns));
                        assertEquals(
                                block.get().tipo() == ec.uce.propuestas.motor.SeccionTipo.MATERIAL
                                                || block.get().tipo() == ec.uce.propuestas.motor.SeccionTipo.TRANSPORTE
                                        ? "Unidad"
                                        : "Cantidad",
                                columns.getCell(1).getStringCellValue());
                        assertFalse(breaks.contains(columns.getRowNum()), "orphan schema " + text);
                        schema = text;
                    } else if (active != null && !printedIdentity(row) && !text.equals("Descripción")) {
                        assertEquals(active, schema, "detail/subtotal lacks current page schema");
                        if (text.equals("Subtotal " + active)) active = null;
                    } else if (active == null
                            && (text.startsWith("Costo ") || text.equals("Valor ofertado") || text.equals("=footer"))) {
                        assertNull(schema, "footer must not inherit section context");
                    }
                    if (active == null) schema = null;
                }
                assertTrue(headings > blocks.size(), "giant physical chunks must repeat schemas");
                assertEquals(
                        3,
                        java.util.stream.StreamSupport.stream(sheet.spliterator(), false)
                                .filter(r -> r.getCell(5) != null
                                        && r.getCell(5).getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                                        && ((org.apache.poi.xssf.usermodel.XSSFCell) r.getCell(5))
                                                .getCTCell()
                                                .getV()
                                                .equals("-12.345000003"))
                                .count());
            }
    }

    @Test
    void rechazaOpcionesAjenas() {
        var d = documento(java.util.List.of(), "");
        assertThrows(
                IllegalArgumentException.class,
                () -> ApuXlsxWriter.renderizar(d, new OpcionesDocumento("pdf", Map.of())));
        assertThrows(
                IllegalArgumentException.class,
                () -> ApuXlsxWriter.renderizar(d, new OpcionesDocumento("xlsx", Map.of("orientacion", "vertical"))));
    }

    @Test
    void conservaIdentidadesYCostosPersistidosEnAmbosLayouts() throws Exception {
        var d = ApuDocumentoProyeccion.proyectar(ApuDocumentoFixture.snapshot(true, true, null));
        for (String layout : new String[] {"pestanas", "apilado"}) {
            byte[] bytes = ApuXlsxWriter.renderizar(d, new OpcionesDocumento("xlsx", Map.of("layout", layout)));
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                assertEquals(layout.equals("pestanas") ? 2 : 1, wb.getNumberOfSheets());
                int costos = 0;
                int totales = 0;
                for (var sheet : wb) {
                    assertEquals(0, sheet.getPrintSetup().getFitHeight());
                    assertEquals(1, sheet.getPrintSetup().getFitWidth());
                    for (var row : sheet)
                        for (var cell : row) {
                            assertNotEquals(org.apache.poi.ss.usermodel.CellType.FORMULA, cell.getCellType());
                            assertNull(cell.getHyperlink());
                            if (cell instanceof org.apache.poi.xssf.usermodel.XSSFCell x
                                    && x.getCTCell().isSetV()) {
                                if ("12.000000003".equals(x.getCTCell().getV())) costos++;
                                if (PresupuestoDocumentoFixture.EXACTO.equals(
                                        x.getCTCell().getV())) totales++;
                            }
                        }
                }
                assertEquals(8, costos);
                assertEquals(4, totales);
            }
        }
    }
}
