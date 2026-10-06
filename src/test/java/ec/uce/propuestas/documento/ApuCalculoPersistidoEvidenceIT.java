package ec.uce.propuestas.documento;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.apu.dto.*;
import ec.uce.propuestas.apu.entity.*;
import ec.uce.propuestas.apu.service.*;
import ec.uce.propuestas.documento.exportacion.*;
import ec.uce.propuestas.motor.*;
import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import javax.sql.DataSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.*;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Fresh, owner-isolated DevServices fixtures. No historical rows are changed or deleted. */
@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApuCalculoPersistidoEvidenceIT {
    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    DataSource ds;

    @Inject
    ApuCrudService crud;

    @Inject
    ApuCalculoService calculo;

    @Inject
    CapturaDocumentoService captura;

    @Inject
    ApuDescargaService descarga;

    @Inject
    EntityManager em;

    private static final Path OUTPUT = Path.of("build/apu05-fresh-calculation-evidence");
    private final Map<String, Map<Integer, Long>> queryCounts = new HashMap<>();

    @AfterAll
    void scalingIsCompleteAndConstant() {
        assertEquals(Set.of("pestanas", "apilado", "pdf"), queryCounts.keySet());
        queryCounts.forEach((format, samples) -> {
            assertEquals(Set.of(1, 32), samples.keySet(), format + " complete datasets");
            assertEquals(samples.get(1), samples.get(32), format + " constant SQL");
        });
    }

    private int comparisons;

    private io.restassured.response.ValidatableResponse post(String token, String path, Map<String, ?> body) {
        return given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(body)
                .post("/api/v1/" + path)
                .then()
                .statusCode(201);
    }

    private Object scalar(String sql, Object argument) throws Exception {
        try (var c = ds.getConnection();
                var p = c.prepareStatement(sql)) {
            p.setObject(1, argument);
            try (var r = p.executeQuery()) {
                assertTrue(r.next(), sql);
                return r.getObject(1);
            }
        }
    }

    private void exact(BigDecimal expected, BigDecimal actual, String field) {
        if (expected == null) assertNull(actual, field);
        else {
            assertNotNull(actual, field);
            assertEquals(0, expected.compareTo(actual), field + " returned=" + expected + " stored=" + actual);
        }
        comparisons++;
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 32})
    void freshCalculationSurvivesPersistenceAndDownload(int count) throws Exception {
        comparisons = 0;
        Files.createDirectories(OUTPUT);
        String email = "apu-fresh-" + UUID.randomUUID() + "@ex.com";
        String token = AuthSupport.registrarConToken(mailbox, email);
        Long owner = ((Number) scalar("select id from usuario where email = ?", email)).longValue();
        String project = post(
                        token,
                        "proyectos",
                        Map.ofEntries(
                                Map.entry("nombreProyecto", "Fresh APU " + count),
                                Map.entry("anio", 2026),
                                Map.entry("plazoEjecucion", 1),
                                Map.entry("plazoUnidad", "MES"),
                                Map.entry("direccionInstitucional", "GAD")))
                .extract()
                .path("id");
        UUID budget = (UUID) scalar(
                "select p.public_id from presupuesto p join proyecto y on y.id=p.proyecto_id "
                        + "where y.public_id=? and p.version=1",
                UUID.fromString(project));
        Long budgetInternal = ((Number) scalar("select id from presupuesto where public_id=?", budget)).longValue();
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.ofEntries(
                        Map.entry("porcentajeIndirecto", "0.10"),
                        Map.entry("ciIndividualHabilitado", false),
                        Map.entry("politicaOverrides", "PRESERVAR")))
                .put("/api/v1/proyectos/" + project + "/ci")
                .then()
                .log()
                .ifValidationFails()
                .statusCode(200);
        given().contentType(JSON)
                .header("Authorization", "Bearer " + token)
                .body(Map.ofEntries(
                        Map.entry("porcentajeHerramientaMenor", "0.05"),
                        Map.entry("porcentajeIndirecto", "0.10"),
                        Map.entry("iva", "0.15"),
                        Map.entry("moneda", "USD")))
                .put("/api/v1/proyectos/" + project + "/parametros")
                .then()
                .statusCode(200);
        UUID material = UUID.fromString(post(
                        token,
                        "proyectos/" + project + "/insumos",
                        Map.ofEntries(
                                Map.entry("codigo", "MAT"),
                                Map.entry("tipo", "MATERIAL"),
                                Map.entry("descripcion", "Material fresh"),
                                Map.entry("unidad", "kg"),
                                Map.entry("precioUnitario", "0.30")))
                .extract()
                .path("id"));
        UUID labor = UUID.fromString(post(
                        token,
                        "proyectos/" + project + "/insumos",
                        Map.ofEntries(
                                Map.entry("codigo", "MO"),
                                Map.entry("tipo", "MANO_OBRA"),
                                Map.entry("descripcion", "Labor fresh"),
                                Map.entry("unidad", "h"),
                                Map.entry("precioUnitario", "0.10")))
                .extract()
                .path("id"));
        String chapter = post(token, "presupuestos/" + budget + "/capitulos", Map.of("descripcion", "Fresh chapter"))
                .extract()
                .path("capitulos[0].id");
        var returned = new LinkedHashMap<UUID, ApuCalculado>();
        for (int i = 0; i < count; i++) {
            var response = crud.crearComoRespuesta(
                    budgetInternal, new ApuCrearRequest("FRESH-" + i, "Fresh analysis " + i, "m"));
            Long internal = ((Number) scalar("select id from apu where public_id=?", response.id())).longValue();
            crud.agregarDetalle(
                    internal,
                    new ApuDetalleCrearRequest(SeccionTipo.MATERIAL, material, new BigDecimal("0.5"), null),
                    owner);
            crud.agregarDetalle(
                    internal,
                    new ApuDetalleCrearRequest(SeccionTipo.MANO_OBRA, labor, new BigDecimal("3"), BigDecimal.ONE),
                    owner);
            ApuCalculado result = QuarkusTransaction.requiringNew().call(() -> {
                em.clear();
                var calculated = calculo.recalcular(em.find(Apu.class, internal));
                em.flush();
                return calculated;
            });
            exact(new BigDecimal("0.465000"), result.costoDirecto(), "finite-decimal CD oracle");
            exact(new BigDecimal("0.046500"), result.costoIndirecto(), "finite-decimal CI oracle");
            exact(new BigDecimal("0.511500"), result.costoTotal(), "finite-decimal CT oracle");
            returned.put(response.id(), result);
            // An independent committed read, never the recalculation persistence context.
            QuarkusTransaction.requiringNew().run(() -> {
                em.clear();
                var stored = em.find(Apu.class, internal);
                exact(result.costoDirecto(), stored.costoDirecto, response.id() + " CD");
                exact(result.costoIndirecto(), stored.costoIndirecto, response.id() + " CI");
                exact(result.costoTotal(), stored.costoTotal, response.id() + " CT");
                assertTrue(stored.costoIndirecto.signum() > 0);
                var sections = em.createQuery("from ApuSeccion where apuId = :id", ApuSeccion.class)
                        .setParameter("id", internal)
                        .getResultList();
                assertEquals(
                        Set.of(SeccionTipo.values()),
                        new HashSet<>(sections.stream().map(s -> s.tipo).toList()));
                for (var section : sections) {
                    var details = em.createQuery("from ApuDetalle where seccionId = :id", ApuDetalle.class)
                            .setParameter("id", section.id)
                            .getResultList();
                    boolean hm = section.tipo == SeccionTipo.EQUIPO;
                    boolean mo = section.tipo == SeccionTipo.MANO_OBRA;
                    boolean mat = section.tipo == SeccionTipo.MATERIAL;
                    exact(
                            new BigDecimal(hm ? "0.015" : mo ? "0.30" : mat ? "0.15" : "0"),
                            section.subtotal,
                            "reloaded subtotal");
                    assertEquals(hm || mo || mat ? 1 : 0, details.size(), "section detail multiplicity");
                    for (var d : details) {
                        assertEquals(hm, d.esHerramientaMenor);
                        assertNotNull(d.publicId);
                        if (hm) assertNull(d.insumoId);
                        else
                            assertEquals(
                                    mo ? labor : material,
                                    em.createNativeQuery("select public_id from insumo where id=?")
                                            .setParameter(1, d.insumoId)
                                            .getSingleResult());
                        exact(hm ? null : new BigDecimal(mo ? "3" : "0.5"), d.cantidad, "reloaded quantity");
                        exact(mo ? BigDecimal.ONE : null, d.rendimiento, "reloaded yield");
                        exact(null, d.tarifaJornal, "reloaded jornal override");
                        exact(null, d.precioUnitarioTarifa, "reloaded price override");
                        if (!hm)
                            exact(
                                    new BigDecimal(mo ? "0.10" : "0.30"),
                                    em.createQuery("select precioUnitario from Insumo where id=:id", BigDecimal.class)
                                            .setParameter("id", d.insumoId)
                                            .getSingleResult(),
                                    "reloaded effective price");
                        exact(new BigDecimal(mo ? "0.30" : "0"), d.costoHora, "reloaded hourly cost");
                        exact(new BigDecimal(hm ? "0.015" : mo ? "0.30" : "0.15"), d.costo, "reloaded row cost");
                    }
                }
            });
            post(
                    token,
                    "presupuestos/" + budget + "/capitulos/" + chapter + "/rubros",
                    Map.of("apuId", response.id(), "cantidad", "125.000000"));
        }
        post(token, "presupuestos/" + budget + "/cronograma", Map.of("unidadTiempo", "MES", "numeroPeriodos", 1));
        var validation = given().header("Authorization", "Bearer " + token)
                .get("/api/v1/presupuestos/" + budget + "/validacion")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();
        for (String list : List.of("itemsPuCero", "itemsCantidadCero", "itemsSinActividad"))
            assertTrue(validation.getList(list).isEmpty(), list);
        var snapshot = captura.capturar(budget, owner, "apus", new OpcionesDocumento("xlsx", Map.of()));
        assertEquals(budget, snapshot.presupuestoId());
        assertEquals(1, snapshot.version());
        assertTrue(snapshot.preflight().exportable(), snapshot.preflight().toString());
        assertTrue(snapshot.preflight().bloqueos().isEmpty());
        assertTrue(snapshot.preflight().warnings().isEmpty());
        assertEquals(count, snapshot.apus().size());
        assertEquals(
                returned.keySet(),
                new LinkedHashSet<>(
                        snapshot.apus().stream().map(SnapshotDocumento.Apu::id).toList()));
        for (var a : snapshot.apus()) {
            var result = returned.get(a.id());
            exact(result.costoDirecto(), a.costoDirecto(), a.id() + " captured CD");
            exact(result.costoIndirecto(), a.costoIndirecto(), a.id() + " captured CI");
            exact(result.costoTotal(), a.costoTotal(), a.id() + " captured CT");
            int index = 0;
            for (var section : a.secciones()) {
                BigDecimal subtotal =
                        switch (section.tipo()) {
                            case EQUIPO -> result.subtotalM();
                            case MANO_OBRA -> result.subtotalN();
                            case MATERIAL -> result.subtotalO();
                            case TRANSPORTE -> result.subtotalP();
                        };
                exact(subtotal, section.subtotal(), a.id() + " " + section.tipo());
                for (var detail : section.detalles()) {
                    var row = result.filas().get(index++);
                    assertEquals(section.tipo(), row.seccion());
                    if (row.esHerramientaMenor()) {
                        // HM is a synthetic motor row: persisted quantity/price/yield are intentionally null.
                        assertNull(detail.cantidad());
                        assertNull(detail.rendimiento());
                        assertNull(detail.precioEfectivo());
                    } else {
                        exact(row.cantidad(), detail.cantidad(), "quantity");
                        exact(row.rendimiento(), detail.rendimiento(), "yield");
                        exact(row.precioUnitarioEfectivo(), detail.precioEfectivo(), "effective price");
                    }
                    // recalcular writes hourly cost only when applicable; entity default remains zero otherwise.
                    exact(
                            row.costoHora() == null ? BigDecimal.ZERO : row.costoHora(),
                            detail.costoHora(),
                            "hourly cost");
                    exact(row.costoFila(), detail.costo(), "row cost");
                }
            }
            assertEquals(result.filas().size(), index);
        }
        var document = ApuDocumentoProyeccion.proyectar(snapshot);
        var stats = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        var manifest = new StringBuilder("budget=" + budget + " version=1 project=" + project + " apus=" + count
                + " returnedStoredCapturedComparisons=" + comparisons + "\n");
        for (String format : List.of("pestanas", "apilado", "pdf")) {
            var options = new OpcionesDocumento(
                    format.equals("pdf") ? "pdf" : "xlsx", format.equals("pdf") ? Map.of() : Map.of("layout", format));
            QuarkusTransaction.requiringNew().run(em::clear);
            stats.clear();
            long start = System.nanoTime();
            var output = descarga.generar(budget, owner, options);
            long elapsed = System.nanoTime() - start;
            long queries = stats.getPrepareStatementCount();
            manifest.append(format)
                    .append(" queries=")
                    .append(queries)
                    .append(" elapsedNs=")
                    .append(elapsed)
                    .append('\n');
            Files.writeString(OUTPUT.resolve("manifest-" + count + ".txt"), manifest);
            assertNotNull(output.archivo(), output.preflight().toString());
            // Observed fresh fixture includes an existing cronograma, unlike historical seed evidence.
            assertEquals(17, queries, "Observed fixed service SQL with cronograma present");
            assertNull(
                    queryCounts
                            .computeIfAbsent(format, ignored -> new HashMap<>())
                            .put(count, queries),
                    "One sample per fixture size");
            byte[] bytes = output.archivo().bytes();
            Files.write(OUTPUT.resolve(count + "-" + format + (format.equals("pdf") ? ".pdf" : ".xlsx")), bytes);
            if (format.equals("pdf")) pdf(bytes, document);
            else xlsx(bytes, document, format.equals("apilado"));
        }
        manifest.append("totalExactComparisons=").append(comparisons).append('\n');
        Files.writeString(OUTPUT.resolve("manifest-" + count + ".txt"), manifest);
    }

    private record NumberCell(int column, BigDecimal value) {}

    private static void number(List<NumberCell> cells, int column, BigDecimal value) {
        if (value != null) cells.add(new NumberCell(column, value));
    }

    private static List<NumberCell> expected(ApuDocumentoProyeccion.Analisis a) {
        var cells = new ArrayList<NumberCell>();
        for (var b : a.bloques()) {
            boolean hourly = b.tipo() == SeccionTipo.EQUIPO || b.tipo() == SeccionTipo.MANO_OBRA;
            for (var row : b.filas()) {
                var d = row.detalle();
                number(cells, hourly ? 1 : 2, d.cantidad());
                number(cells, hourly ? 2 : 3, d.precioEfectivo());
                if (hourly) {
                    number(cells, 3, d.costoHora());
                    number(cells, 4, d.rendimiento());
                }
                number(cells, 5, d.costo());
            }
            if (b.mostrarSubtotal()) number(cells, 5, b.subtotal());
        }
        for (var s : a.pie().subtotales()) number(cells, 5, s.valor());
        number(cells, 5, a.pie().costoDirecto());
        number(cells, 4, a.pie().porcentajeIndirecto());
        number(cells, 5, a.pie().costoIndirecto());
        number(cells, 5, a.pie().costoTotal());
        number(cells, 5, a.pie().valorOfertado());
        return cells;
    }

    private void xlsx(byte[] bytes, ApuDocumentoProyeccion.Documento document, boolean stacked) throws Exception {
        try (var wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(stacked ? 1 : document.apus().size(), wb.getNumberOfSheets());
            for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                var actual = new ArrayList<NumberCell>();
                for (var row : wb.getSheetAt(i))
                    for (var cell : row) {
                        assertNotEquals(CellType.FORMULA, cell.getCellType());
                        if (cell.getCellType() == CellType.NUMERIC)
                            actual.add(new NumberCell(
                                    cell.getColumnIndex(),
                                    new BigDecimal(((XSSFCell) cell).getCTCell().getV())));
                    }
                var expected = stacked
                        ? document.apus().stream()
                                .flatMap(a -> expected(a).stream())
                                .toList()
                        : expected(document.apus().get(i));
                assertEquals(expected.size(), actual.size(), "Numeric multiplicity");
                for (int n = 0; n < expected.size(); n++) {
                    assertEquals(expected.get(n).column(), actual.get(n).column());
                    exact(expected.get(n).value(), actual.get(n).value(), "OOXML sheet=" + i + " ordinal=" + n);
                }
            }
        }
    }

    private static void pdf(byte[] bytes, ApuDocumentoProyeccion.Documento document) throws Exception {
        try (var pdf = Loader.loadPDF(bytes)) {
            var texts = new LinkedHashMap<UUID, StringBuilder>();
            var stripper = new PDFTextStripper();
            for (int i = 1; i <= pdf.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(pdf);
                UUID id = document.apus().stream()
                        .map(ApuDocumentoProyeccion.Analisis::id)
                        .filter(uuid -> text.contains("APU " + uuid))
                        .findFirst()
                        .orElseThrow();
                texts.computeIfAbsent(id, ignored -> new StringBuilder()).append(text);
            }
            assertEquals(
                    document.apus().stream()
                            .map(ApuDocumentoProyeccion.Analisis::id)
                            .toList(),
                    new ArrayList<>(texts.keySet()));
            for (var a : document.apus()) {
                var values = List.of(
                        a.pie().costoDirecto(),
                        a.pie().costoIndirecto(),
                        a.pie().costoTotal(),
                        a.pie().valorOfertado());
                var labels = List.of("Costo directo", "Costo indirecto", "Costo total", "Valor ofertado");
                for (int i = 0; i < labels.size(); i++) {
                    String label = labels.get(i);
                    String money = values.get(i)
                            .setScale(document.display().precision(), java.math.RoundingMode.HALF_UP)
                            .toPlainString();
                    assertTrue(
                            texts.get(a.id())
                                    .toString()
                                    .lines()
                                    .anyMatch(line -> line.startsWith(label) && line.endsWith(money)),
                            a.id() + " labeled stored footer " + label + " " + money);
                }
            }
        }
    }
}
