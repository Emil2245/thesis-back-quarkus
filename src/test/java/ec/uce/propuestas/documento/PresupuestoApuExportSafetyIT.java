package ec.uce.propuestas.documento;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import ec.uce.propuestas.cronograma.entity.Cronograma;
import ec.uce.propuestas.cronograma.export.CronogramaExportPreflightService;
import ec.uce.propuestas.cronograma.repository.CronogramaRepository;
import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import ec.uce.propuestas.presupuesto.repository.PresupuestoRepository;
import ec.uce.propuestas.support.AuthSupport;
import ec.uce.propuestas.usuario.auth.RecordingEnviadorCorreo;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Real isolated PostgreSQL fixtures, including a FK-valid but scope-invalid link. */
@QuarkusTest
class PresupuestoApuExportSafetyIT {
    @Inject
    DataSource ds;

    @Inject
    RecordingEnviadorCorreo mailbox;

    @Inject
    PresupuestoRepository budgets;

    @Inject
    CronogramaRepository schedules;

    @Inject
    CronogramaExportPreflightService stale;

    private record Fixture(String token, long budgetId, UUID publicId, long chapterId) {}

    private Fixture fixture() throws Exception {
        String token = AuthSupport.registrarConToken(mailbox, "safe-" + System.nanoTime() + "@test.ec");
        String project = given().contentType("application/json")
                .auth()
                .oauth2(token)
                .body(Map.of(
                        "nombreProyecto",
                        "Safety",
                        "codigo",
                        "T" + System.nanoTime(),
                        "anio",
                        2026,
                        "plazoEjecucion",
                        6,
                        "plazoUnidad",
                        "MES",
                        "direccionInstitucional",
                        "UCE"))
                .post("/api/v1/proyectos")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        long id;
        UUID publicId;
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "select p.id,p.public_id from presupuesto p join proyecto pr on pr.id=p.proyecto_id where pr.public_id=?")) {
            p.setObject(1, UUID.fromString(project));
            try (ResultSet r = p.executeQuery()) {
                r.next();
                id = r.getLong(1);
                publicId = r.getObject(2, UUID.class);
            }
        }
        long chapter;
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "insert into capitulo(presupuesto_id,item,descripcion,orden,total) values (?,'1','C',1,30) returning id")) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) {
                r.next();
                chapter = r.getLong(1);
            }
        }
        Fixture fixture = new Fixture(token, id, publicId, chapter);
        for (int n = 1; n <= 2; n++) {
            long apu = apu(fixture, "R" + n);
            try (Connection c = ds.getConnection();
                    PreparedStatement p = c.prepareStatement(
                            "insert into rubro(capitulo_id,apu_id,item,codigo,descripcion,unidad,cantidad,precio_unitario,precio_total) values (?,?,?,?,'R','u',1,?,?)")) {
                p.setLong(1, chapter);
                p.setLong(2, apu);
                p.setString(3, "1." + n);
                p.setString(4, "R" + n);
                p.setBigDecimal(5, BigDecimal.valueOf(n * 10L));
                p.setBigDecimal(6, BigDecimal.valueOf(n * 10L));
                p.executeUpdate();
            }
        }
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement("update presupuesto set total=30 where id=?")) {
            p.setLong(1, id);
            p.executeUpdate();
        }
        given().contentType("application/json")
                .auth()
                .oauth2(token)
                .body(Map.of("unidadTiempo", "MES", "numeroPeriodos", 6))
                .post("/api/v1/presupuestos/" + publicId + "/cronograma")
                .then()
                .statusCode(201);
        return fixture;
    }

    private long apu(Fixture fixture, String code) throws Exception {
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "insert into apu(presupuesto_id,codigo,descripcion,unidad) values (?,?,'Foreign-safe fixture','u') returning id")) {
            p.setLong(1, fixture.budgetId());
            p.setString(2, code);
            try (ResultSet r = p.executeQuery()) {
                r.next();
                return r.getLong(1);
            }
        }
    }

    @Test
    void crossOwnerApuLinkFailsClosedWithoutForeignReferences() throws Exception {
        Fixture selected = fixture();
        Fixture foreign = fixture();
        long foreignApu = apu(foreign, "FOREIGN-SECRET");
        // The FK checks existence, not budget scope; do not disable or alter constraints.
        try (Connection c = ds.getConnection();
                PreparedStatement p =
                        c.prepareStatement("update rubro set apu_id=? where capitulo_id=? and item='1.1'")) {
            p.setLong(1, foreignApu);
            p.setLong(2, selected.chapterId());
            assertEquals(1, p.executeUpdate());
        }
        for (String document : List.of("presupuesto", "apus")) {
            var response = given().auth()
                    .oauth2(selected.token())
                    .get("/api/v1/documentos/" + document + "/" + selected.publicId() + "/preflight?formato=xlsx")
                    .then()
                    .statusCode(409)
                    .extract()
                    .response();
            assertEquals(
                    Map.of("codigo", "export-inconsistente", "mensaje", "Vínculo APU ilegible"),
                    response.jsonPath().getMap(""));
            assertNull(response.header("Content-Disposition"));
            assertFalse(response.asString().contains(foreign.publicId().toString()));
            assertFalse(response.asString().contains("FOREIGN-SECRET"));
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "unchanged",
                "missing-total",
                "missing-fingerprint",
                "changed-total",
                "changed-fingerprint",
                "trimmed-fingerprint",
                "missing-date",
                "changed-date",
                "null-budget-total",
                "compensated-costs"
            })
    void canonicalStaleOverloadsAgreeOnEveryBranchWithoutWriting(String branch) throws Exception {
        Fixture fixture = fixture();
        var persisted = schedules.find("presupuestoId", fixture.budgetId()).firstResult();
        BigDecimal reviewedTotal = persisted.totalGeneralRevisado;
        String reviewedFingerprint = persisted.presupuestoFingerprintRevisado;
        Instant reviewedDate = persisted.fechaRevision;
        // Detached inputs: exercise missing review markers without violating NOT NULL domain constraints.
        Cronograma schedule = new Cronograma();
        schedule.totalGeneralRevisado = reviewedTotal;
        schedule.presupuestoFingerprintRevisado = reviewedFingerprint;
        schedule.fechaRevision = reviewedDate;
        Presupuesto budget = new Presupuesto();
        budget.id = fixture.budgetId();
        budget.total = budgets.findById(fixture.budgetId()).total;
        boolean expected = false;
        switch (branch) {
            case "missing-total" -> {
                schedule.totalGeneralRevisado = null;
                expected = true;
            }
            case "missing-fingerprint" -> {
                schedule.presupuestoFingerprintRevisado = null;
                expected = true;
            }
            case "changed-total" -> {
                budget.total = new BigDecimal("31");
                expected = true;
            }
            case "changed-fingerprint" -> {
                schedule.presupuestoFingerprintRevisado = "0".repeat(64);
                expected = true;
            }
            case "trimmed-fingerprint" -> schedule.presupuestoFingerprintRevisado = " " + reviewedFingerprint + " ";
            case "missing-date" -> schedule.fechaRevision = null;
            case "changed-date" -> schedule.fechaRevision = Instant.EPOCH;
            case "null-budget-total" -> {
                budget.total = null;
                schedule.totalGeneralRevisado = BigDecimal.ZERO;
            }
            case "compensated-costs" -> {
                // Same total, different rows: the canonical fingerprint must still report stale.
                try (Connection c = ds.getConnection();
                        PreparedStatement p = c.prepareStatement(
                                "update rubro set precio_total=case item when '1.1' then 11 else 19 end where capitulo_id=?")) {
                    p.setLong(1, fixture.chapterId());
                    assertEquals(2, p.executeUpdate());
                }
                expected = true;
            }
            default -> {}
        }
        BigDecimal total = budget.total == null ? BigDecimal.ZERO : budget.total.setScale(6);
        var snapshot = new CronogramaExportPreflightService.SnapshotCompleto(
                schedule, budget, null, total, null, List.of(), false, false, false, null);
        assertEquals(expected, stale.esStale(snapshot), branch);
        assertEquals(expected, stale.esStale(schedule, budget), branch);
        // Neither overload approves/reviews anything, including null/changed review dates.
        try (Connection c = ds.getConnection();
                PreparedStatement p = c.prepareStatement(
                        "select total_general_revisado,presupuesto_fingerprint_revisado,fecha_revision from cronograma where presupuesto_id=?")) {
            p.setLong(1, fixture.budgetId());
            try (ResultSet r = p.executeQuery()) {
                r.next();
                assertEquals(reviewedTotal, r.getBigDecimal(1));
                assertEquals(reviewedFingerprint, r.getString(2));
                assertEquals(reviewedDate, r.getTimestamp(3).toInstant());
            }
        }
    }
}
