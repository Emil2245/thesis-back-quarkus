package ec.uce.propuestas.documentos.render;

import static ec.uce.propuestas.documentos.render.ApuFixtures.hourly;
import static ec.uce.propuestas.documentos.render.ApuFixtures.row;
import static ec.uce.propuestas.documentos.render.ApuFixtures.xlsx;
import static ec.uce.propuestas.documentos.render.Projections.load;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.documentos.contract.DocumentProjectionV1;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.ApuBlock;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.ApuFooter;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.ApuSubtotal;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.ApuView;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.Detalle;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.Display;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.Firmante;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.Proyecto;
import ec.uce.propuestas.documentos.contract.DocumentProjectionV1.SeccionTipo;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

/**
 * APU XLSX layout coverage ported in stage 05 from the hand-built cases of Core's retired {@code ApuXlsxWriterTest}
 * (exact ZIP values, long text fragmentation, sheet names, manual page breaks, section continuation). The derived
 * documents start from the same Core fixture ({@code ApuDocumentoFixture.snapshot(true, true, null)}) as captured
 * through the real mapper. Characterization tests: the renderer already exists.
 */
class ApuXlsxLayoutFormatTest {
    private static final String[] LAYOUTS = {"pestanas", "apilado"};

    private static byte[] render(DocumentProjectionV1 d, String layout) {
        return ApuXlsxRenderer.renderizar(xlsx(d, layout));
    }

    private static DocumentProjectionV1 base() {
        return load("apus-hostil-pestanas");
    }

    /** Port of the old {@code sample(money, percent, detailText)}: one APU, one negative row per section. */
    private static DocumentProjectionV1 sample(int money, int percent, String detailText) {
        var original = base();
        var blocks = new ArrayList<ApuBlock>();
        int index = 0;
        for (var type : SeccionTipo.values()) {
            var value = new Detalle(
                    new UUID(1, ++index),
                    (short) 1,
                    false,
                    detailText + type,
                    "u",
                    new BigDecimal("2.000001"),
                    new BigDecimal("0.000123456"),
                    new BigDecimal("999.00"),
                    new BigDecimal("888.00"),
                    new BigDecimal("-9.125000001"),
                    new BigDecimal("-10.500000002"),
                    new BigDecimal("-12.345000003"),
                    null);
            blocks.add(new ApuBlock(
                    type,
                    type.name(),
                    (short) index,
                    List.of(row(value, value.descripcion())),
                    new BigDecimal("-23.450000"),
                    true));
        }
        var a = original.snapshot().apuRender().getFirst();
        var pie = new ApuFooter(
                blocks.stream()
                        .map(b -> new ApuSubtotal(b.tipo(), b.subtotal()))
                        .toList(),
                new BigDecimal("0.123456700"),
                new BigDecimal("-93.800000"),
                new BigDecimal("-7.89000"),
                new BigDecimal("-101.690000000"),
                new BigDecimal("-101.690000000"),
                "=footer");
        var analysis = new ApuView(a.id(), "same", "same", "description", "m²", null, blocks, pie);
        return ApuFixtures.with(original, null, new Display(money, percent), null, List.of(analysis));
    }

    /** Port of the old {@code documento(codigos, descripcion)}. */
    private static DocumentProjectionV1 documento(List<String> codigos, String descripcion) {
        var original = base();
        var a = original.snapshot().apuRender().getFirst();
        var apus = new ArrayList<ApuView>();
        for (int i = 0; i < codigos.size(); i++)
            apus.add(new ApuView(
                    new UUID(7, i + 1),
                    codigos.get(i),
                    codigos.get(i),
                    descripcion,
                    a.unidad(),
                    a.nombreProyectoHeader(),
                    a.bloques(),
                    a.pie()));
        return ApuFixtures.apus(original, apus);
    }

    // ---- helpers copied from the retired test ----

    private static List<XSSFRow> rows(XSSFWorkbook wb) {
        var result = new ArrayList<XSSFRow>();
        for (var sheet : wb) {
            var sections = new HashSet<String>();
            String identity = "";
            int skipSchema = -1;
            for (var raw : sheet) {
                var x = (XSSFRow) raw;
                if (printedIdentity(x)) {
                    if (label(x).startsWith("APU UUID: ") && !identity.equals(label(x))) {
                        identity = label(x);
                        sections.clear();
                    }
                    continue;
                }
                if (x.getRowNum() == skipSchema) continue;
                var next = (XSSFRow) sheet.getRow(x.getRowNum() + 1);
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

    private static boolean printedIdentity(XSSFRow row) {
        String text = label(row);
        int offset = text.startsWith("APU UUID: ")
                ? 0
                : text.startsWith("APU código: ") ? 1 : text.startsWith("APU descripción: ") ? 2 : -1;
        if (offset < 0 || row.getRowNum() < offset) return false;
        var start = row.getSheet().getRow(row.getRowNum() - offset);
        return start != null && label(start).matches("APU UUID: [0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    }

    private static String label(XSSFRow row) {
        var c = row.getCell(0);
        return c == null ? "" : c.getStringCellValue();
    }

    private static XSSFRow labelled(XSSFWorkbook wb, String label) {
        return rows(wb).stream()
                .filter(r -> label(r).equals(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError(label));
    }

    private static void exact(XSSFRow row, int column, BigDecimal value) {
        var cell = row.getCell(column);
        assertEquals(CellType.NUMERIC, cell.getCellType());
        assertEquals(value.toPlainString(), cell.getCTCell().getV());
    }

    private static Map<String, org.w3c.dom.Document> zipXml(byte[] bytes) throws Exception {
        var parts = new LinkedHashMap<String, org.w3c.dom.Document>();
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                String name = entry.getName();
                assertFalse(name.toLowerCase(Locale.ROOT).contains("externallink"));
                assertFalse(name.toLowerCase(Locale.ROOT).contains("vbaproject"));
                if (name.endsWith(".xml") || name.endsWith(".rels"))
                    parts.put(name, factory.newDocumentBuilder().parse(new ByteArrayInputStream(zip.readAllBytes())));
            }
        }
        return parts;
    }

    /** Oracle copied from the retired Core {@code PresupuestoXlsxWriter.fragmentos} via {@code ApuTextoPresentacion}. */
    static List<String> fragmentos(String raw) {
        String text = raw == null ? "" : raw.replaceAll("[\\p{Cc}&&[^\\n\\t]]", "");
        if (text.length() <= 32767 && altura(text, 30) <= 240) return List.of(text);
        var result = new ArrayList<String>();
        int start = 0;
        while (start < text.length()) {
            int end = start;
            int lines = 1;
            int columns = 0;
            int boundary = start;
            while (end < text.length()) {
                int cp = text.codePointAt(end);
                int nextLines = lines;
                int nextColumns = columns + (cp == '\t' ? 4 : 1);
                if (cp == '\n') {
                    nextLines++;
                    nextColumns = 0;
                } else if (nextColumns > 30) {
                    nextLines++;
                    nextColumns = 1;
                }
                if (nextLines > 10) break;
                end += Character.charCount(cp);
                lines = nextLines;
                columns = nextColumns;
                if (Character.isWhitespace(cp)) boundary = end;
            }
            if (end < text.length() && boundary > start) end = boundary;
            result.add(text.substring(start, end));
            start = end;
        }
        return result.isEmpty() ? List.of("") : List.copyOf(result);
    }

    private static float altura(String value, int width) {
        int lines = 0;
        for (String line : value.split("\n", -1)) lines += Math.max(1, (line.length() + width - 1) / width);
        return Math.min(409, Math.max(24, lines * 15));
    }

    // ---- ported cases ----

    @Test
    void exactZipValuesAllNegativeAmountsAndIndependentFormats() throws Exception {
        for (String layout : LAYOUTS)
            for (int precision : new int[] {0, 2}) {
                var d = sample(precision, 4, "detalle-");
                var apu = d.snapshot().apuRender().getFirst();
                byte[] bytes = render(d, layout);
                var xml = zipXml(bytes);
                var sheetXml = xml.get("xl/worksheets/sheet1.xml");
                assertEquals(0, sheetXml.getElementsByTagName("f").getLength());
                assertEquals(0, sheetXml.getElementsByTagName("hyperlink").getLength());
                var formatter = new DataFormatter(Locale.US);
                try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                    for (var block : apu.bloques()) {
                        var detail = block.filas().getFirst().detalle();
                        var row = labelled(wb, detail.descripcion());
                        boolean hour = hourly(block.tipo());
                        exact(row, hour ? 1 : 2, detail.cantidad());
                        exact(row, hour ? 2 : 3, detail.precioEfectivo());
                        if (hour) {
                            exact(row, 3, detail.costoHora());
                            exact(row, 4, detail.rendimiento());
                        }
                        exact(row, 5, detail.costo());
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
                    }
                    var pie = apu.pie();
                    exact(labelled(wb, "Costo directo"), 5, pie.costoDirecto());
                    exact(labelled(wb, "Costo indirecto"), 4, pie.porcentajeIndirecto());
                    exact(labelled(wb, "Costo indirecto"), 5, pie.costoIndirecto());
                    exact(labelled(wb, "Costo total"), 5, pie.costoTotal());
                    exact(labelled(wb, "Valor ofertado"), 5, pie.valorOfertado());
                    assertEquals(
                            "12.3457%",
                            formatter.formatCellValue(
                                    labelled(wb, "Costo indirecto").getCell(4)));
                    assertEquals(
                            "0.0000%",
                            labelled(wb, "Costo indirecto")
                                    .getCell(4)
                                    .getCellStyle()
                                    .getDataFormatString());
                    var elements = sheetXml.getElementsByTagName("c");
                    for (var row : rows(wb))
                        for (var cell : row) {
                            if (cell.getCellType() == CellType.NUMERIC) {
                                boolean found = false;
                                for (int i = 0; i < elements.getLength(); i++) {
                                    var element = (Element) elements.item(i);
                                    if (!cell.getAddress().formatAsString().equals(element.getAttribute("r"))) continue;
                                    assertEquals("n", element.getAttribute("t"));
                                    assertEquals(
                                            ((XSSFCell) cell).getCTCell().getV(),
                                            element.getElementsByTagName("v")
                                                    .item(0)
                                                    .getTextContent());
                                    found = true;
                                }
                                assertTrue(found);
                            }
                            assertNotEquals(CellType.FORMULA, cell.getCellType());
                            assertNull(cell.getHyperlink());
                        }
                    assertEquals(
                            CellType.STRING, labelled(wb, "=footer").getCell(0).getCellType());
                }
                for (var part : xml.values()) {
                    var relations = part.getElementsByTagName("Relationship");
                    for (int i = 0; i < relations.getLength(); i++)
                        assertNotEquals("External", ((Element) relations.item(i)).getAttribute("TargetMode"));
                }
            }
    }

    @Test
    void longDetailsNeverDuplicateNumbersAndLayoutsAreEquivalent() throws Exception {
        String longText = "=😃 á\t palabra\n".repeat(3500);
        var d = sample(2, 3, longText);
        List<String> first = null;
        for (String layout : LAYOUTS)
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render(d, layout)))) {
                var logical = new ArrayList<String>();
                for (var row : rows(wb)) {
                    assertTrue(row.getHeightInPoints() <= 240);
                    for (var cell : row) {
                        if (cell.getCellType() == CellType.STRING) {
                            assertTrue(cell.getStringCellValue().length() <= 32767);
                            logical.add(cell.getColumnIndex() + ":" + cell.getStringCellValue());
                        } else if (cell.getCellType() == CellType.NUMERIC)
                            logical.add(cell.getColumnIndex() + ":"
                                    + ((XSSFCell) cell).getCTCell().getV());
                    }
                }
                if (first == null) first = logical;
                else assertEquals(first, logical);
                var all = rows(wb);
                for (var b : d.snapshot().apuRender().getFirst().bloques()) {
                    int start = all.indexOf(labelled(wb, b.etiqueta())) + 2;
                    int end = all.indexOf(labelled(wb, "Subtotal " + b.etiqueta()));
                    var reconstructed = new StringBuilder();
                    for (int i = start; i < end; i++) {
                        var row = all.get(i);
                        reconstructed.append(label(row));
                        if (i != start)
                            for (int c = 1; c <= 5; c++) {
                                boolean unitText = c == 1
                                        && !hourly(b.tipo())
                                        && row.getCell(c) != null
                                        && row.getCell(c).getCellType() == CellType.STRING;
                                assertTrue(unitText
                                        || row.getCell(c) == null
                                        || row.getCell(c).getCellType() == CellType.BLANK);
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
    void institutionalCodeUnitFooterAndSignerFieldsAreFragmentedToo() throws Exception {
        var base = sample(2, 0, "+literal");
        String largo = "@ñ😃\n".repeat(9000);
        var p = base.snapshot().proyecto();
        var proyecto = new Proyecto(
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
        var a = base.snapshot().apuRender().getFirst();
        var pie = a.pie();
        var longPie = new ApuFooter(
                pie.subtotales(),
                pie.porcentajeIndirecto(),
                pie.costoDirecto(),
                pie.costoIndirecto(),
                pie.costoTotal(),
                pie.valorOfertado(),
                largo);
        var longApu = new ApuView(a.id(), largo, largo, largo, largo, largo, a.bloques(), longPie);
        var f = new Firmante(new UUID(3, 1), largo, largo, largo, (short) 1);
        var d = ApuFixtures.with(base, proyecto, null, List.of(f), List.of(longApu));
        for (String layout : LAYOUTS)
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render(d, layout)))) {
                var labels =
                        rows(wb).stream().map(ApuXlsxLayoutFormatTest::label).toList();
                var expected = new ArrayList<String>();
                expected.add("ANÁLISIS DE PRECIOS UNITARIOS");
                for (String text : List.of(
                        largo,
                        largo,
                        "PROYECTO: " + largo,
                        "Año: " + p.anio(),
                        "Proyecto: " + largo,
                        "Código: " + largo,
                        largo,
                        "Unidad: " + largo)) expected.addAll(fragmentos(text));
                assertEquals(expected, labels.subList(0, expected.size()));
                var closing = new ArrayList<String>();
                closing.addAll(fragmentos(largo));
                closing.addAll(fragmentos(largo + ":"));
                closing.addAll(fragmentos(largo + "\n" + largo));
                closing.add("FIRMA:");
                assertEquals(closing, labels.subList(labels.size() - closing.size(), labels.size()));
                for (var row : rows(wb))
                    for (var cell : row)
                        if (cell.getCellType() == CellType.STRING) {
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
    void sheetNamesTrimSpacesAndEdgeApostrophes() throws Exception {
        var d = documento(List.of("  'nombre'  ", "'", "[]:*?/\\\\", "X".repeat(30) + "'fin", "\t\n"), "texto");
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

    @Test
    void hostileSheetNamesAreDeterministicAndNeverMergeIdentities() throws Exception {
        var d = documento(
                List.of(
                        "'[]:*?/\\\\'",
                        "",
                        "History",
                        "A".repeat(40),
                        "a".repeat(40),
                        "😃".repeat(30),
                        "'fin'",
                        "NUL\u0000control"),
                "Descripción");
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render(d, "pestanas")));
                var second = new XSSFWorkbook(new ByteArrayInputStream(render(d, "pestanas")))) {
            var names = new HashSet<String>();
            assertEquals(8, wb.getNumberOfSheets());
            for (int i = 0; i < 8; i++) {
                String name = wb.getSheetName(i);
                WorkbookUtil.validateSheetName(name);
                assertTrue(names.add(name.toLowerCase(Locale.ROOT)));
                assertEquals(name, second.getSheetName(i));
            }
        }
    }

    @Test
    void longTextIsCompleteWithDynamicBreaks() throws Exception {
        String largo = "á 😃 palabra\n".repeat(4000);
        var d = documento(List.of("primero", "segundo"), largo);
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render(d, "apilado")))) {
            var sh = wb.getSheetAt(0);
            var rebuilt = new StringBuilder();
            boolean description = false;
            for (var raw : sh) {
                assertTrue(raw.getHeightInPoints() <= 240);
                var cell = raw.getCell(0);
                if (cell == null || cell.getCellType() != CellType.STRING) continue;
                String text = cell.getStringCellValue();
                assertTrue(text.length() <= 32767);
                if (printedIdentity((XSSFRow) raw)) continue;
                if (text.equals("Código: primero")) {
                    description = true;
                    continue;
                }
                if (description && text.startsWith("Unidad:")) break;
                if (description) rebuilt.append(text);
            }
            assertEquals(largo, rebuilt.toString());
            assertTrue(sh.getRowBreaks().length > 1);
            assertTrue(sh.getRowBreaks()[0] < 85);
            assertTrue(wb.getNumCellStyles() < 12);
        }
    }

    @Test
    void everyManualPageKeepsBoundedIdentityAndPhysicalBudget() throws Exception {
        var d = documento(List.of("primero", "segundo", "tercero"), "descripción\n".repeat(4000));
        var identities = d.snapshot().apuRender().stream()
                .map(a -> "APU UUID: " + a.id())
                .toList();
        for (String layout : LAYOUTS)
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render(d, layout)))) {
                assertEquals(layout.equals("apilado") ? 1 : 3, wb.getNumberOfSheets());
                for (var sheet : wb) {
                    assertTrue(sheet.getRowBreaks().length > 3);
                    String identity = null;
                    double height = 0;
                    var breaks = Arrays.stream(sheet.getRowBreaks()).boxed().collect(Collectors.toSet());
                    for (var raw : sheet) {
                        var row = (XSSFRow) raw;
                        String text = label(row);
                        if (height == 0) {
                            assertTrue(text.startsWith("APU UUID: "), text);
                            identity = text;
                            assertTrue(identities.contains(text));
                            assertTrue(label((XSSFRow) sheet.getRow(row.getRowNum() + 1))
                                    .startsWith("APU código: "));
                            assertTrue(label((XSSFRow) sheet.getRow(row.getRowNum() + 2))
                                    .startsWith("APU descripción: "));
                        }
                        if (printedIdentity(row)) {
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

    @Test
    void sectionsAreNeverOrphanedAndContinueWithTheirSchema() throws Exception {
        var base = sample(2, 3, "body|".repeat(5000));
        var a = base.snapshot().apuRender().getFirst();
        var blocks = new ArrayList<>(a.bloques());
        var last = blocks.getLast();
        blocks.set(
                blocks.size() - 1,
                new ApuBlock(last.tipo(), last.etiqueta(), last.ordenCapturado(), List.of(), last.subtotal(), true));
        var analysis = new ApuView(
                a.id(),
                a.codigo(),
                a.etiquetaCodigo(),
                "x".repeat(900),
                a.unidad(),
                a.nombreProyectoHeader(),
                blocks,
                a.pie());
        var d = ApuFixtures.apus(base, List.of(analysis));
        for (String layout : LAYOUTS)
            try (var wb = new XSSFWorkbook(new ByteArrayInputStream(render(d, layout)))) {
                var sheet = wb.getSheetAt(0);
                var breaks = Arrays.stream(sheet.getRowBreaks()).boxed().collect(Collectors.toSet());
                String active = null, schema = null;
                int headings = 0;
                for (var raw : sheet) {
                    var row = (XSSFRow) raw;
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
                                hourly(block.get().tipo()) ? "Cantidad" : "Unidad",
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
                        StreamSupport.stream(sheet.spliterator(), false)
                                .filter(r -> r.getCell(5) != null
                                        && r.getCell(5).getCellType() == CellType.NUMERIC
                                        && ((XSSFCell) r.getCell(5))
                                                .getCTCell()
                                                .getV()
                                                .equals("-12.345000003"))
                                .count());
            }
    }
}
