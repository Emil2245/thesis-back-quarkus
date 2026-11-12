package ec.uce.propuestas.cronograma;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.cronograma.export.CronogramaExportPreflightService;
import ec.uce.propuestas.cronograma.export.FormatoExportacion;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(CmtSeedWeightedAdvanceMigrationTest.SeedProfile.class)
class CmtSeedWeightedAdvanceMigrationTest {
    private static final String CMT = "SELECT cr.id FROM cronograma cr JOIN presupuesto p ON p.id=cr.presupuesto_id "
            + "JOIN proyecto pr ON pr.id=p.proyecto_id WHERE pr.codigo='CMT-2023' AND p.version=1";
    private static final String MIGRATION = "/db/migration/V019__repair_cmt_seed_weighted_advances.sql";

    @Inject
    DataSource dataSource;

    @Inject
    CronogramaExportPreflightService preflight;

    @Test
    void repara_seed_completo_preserva_ediciones_y_evalua_preflight_real() throws Exception {
        migrate("18");
        String preserved;
        String original;
        UUID presupuesto;
        Long owner;
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            preserved = preserved(s);
            original = scalar(s, "SELECT jsonb_agg(to_jsonb(a) ORDER BY id)::text FROM actividad a");
            assertEquals("298", scalar(s, "SELECT count(*) FROM actividad WHERE cronograma_id IN (" + CMT + ")"));
            assertEquals("298", deviations(s));
            try (ResultSet r = s.executeQuery("SELECT p.public_id, pr.usuario_id FROM presupuesto p "
                    + "JOIN proyecto pr ON pr.id=p.proyecto_id WHERE pr.codigo='CMT-2023' AND p.version=1")) {
                assertTrue(r.next());
                presupuesto = r.getObject(1, UUID.class);
                owner = r.getLong(2);
            }
        }
        var before = preflight.evaluarPreflight(presupuesto, owner, FormatoExportacion.XLSX);
        assertEquals(
                298,
                before.bloqueos().stream()
                        .filter(b -> "cronograma-desviacion".equals(b.codigo()))
                        .count());
        migrate("19");
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            assertEquals("0", deviations(s), "V019 must eliminate every CMT activity deviation");
            assertEquals(preserved, preserved(s), "only CMT advance values may change, not reviews or user data");
            assertEquals(
                    0,
                    new BigDecimal(scalar(
                                    s,
                                    "SELECT sum((e.value #>> '{}')::numeric) FROM actividad a, "
                                            + "LATERAL jsonb_each(a.avance_por_periodo) e WHERE a.cronograma_id IN ("
                                            + CMT + ")"))
                            .compareTo(new BigDecimal("100.0000")));
            assertEquals(
                    "298",
                    scalar(
                            s,
                            "WITH ordered AS (SELECT a.*, row_number() OVER "
                                    + "(ORDER BY string_to_array(r.item,'.')::int[],r.id) n FROM actividad a "
                                    + "JOIN rubro r ON r.id=a.rubro_id WHERE a.cronograma_id IN (" + CMT + ")) "
                                    + "SELECT count(*) FROM ordered WHERE avance_por_periodo = "
                                    + "jsonb_build_object(((n*12+297)/298)::text,peso_ponderado::text)"));
            String corrected = scalar(s, "SELECT jsonb_agg(to_jsonb(a) ORDER BY id)::text FROM actividad a");
            s.execute(migrationSql());
            assertEquals(corrected, scalar(s, "SELECT jsonb_agg(to_jsonb(a) ORDER BY id)::text FROM actividad a"));
        }
        assertEquals(0, migrate("19"), "Flyway reapplication must not execute again");
        for (FormatoExportacion formato : FormatoExportacion.values()) {
            var after = preflight.evaluarPreflight(presupuesto, owner, formato);
            assertTrue(after.bloqueos().stream()
                    .noneMatch(b ->
                            "cronograma-desviacion".equals(b.codigo()) || "cronograma-borrador".equals(b.codigo())));
            assertTrue(after.exportable(), "fresh seed preflight: " + after.bloqueos());
            assertTrue(
                    after.warnings().stream().anyMatch(w -> "cronograma-desactualizado".equals(w.codigo())),
                    "migration must not fabricate a current review fingerprint");
        }
        // Shadow only the activity table; all mutations below are rolled back in Dev Services.
        for (String edit : new String[] {
            "UPDATE actividad SET avance_por_periodo='{}' WHERE id=(SELECT min(id) FROM actividad WHERE cronograma_id IN ("
                    + CMT + "))",
            "UPDATE actividad SET avance_por_periodo='{\"1\":\"0.3969\"}' WHERE id=(SELECT min(id) FROM actividad WHERE cronograma_id IN ("
                    + CMT + "))",
            "UPDATE actividad SET avance_por_periodo='{\"2\":\"1.0000\"}' WHERE id=(SELECT min(id) FROM actividad WHERE cronograma_id IN ("
                    + CMT + "))",
            "UPDATE actividad SET avance_por_periodo='{\"1\":\"1.0000\",\"2\":\"0.0000\"}' WHERE id=(SELECT min(id) FROM actividad WHERE cronograma_id IN ("
                    + CMT + "))",
            "UPDATE rubro SET codigo='USUARIO' WHERE id=(SELECT min(rubro_id) FROM actividad WHERE cronograma_id IN ("
                    + CMT + "))",
            "UPDATE cronograma SET numero_periodos=13 WHERE id IN (" + CMT + ")",
            "UPDATE proyecto SET codigo='CMT-USUARIO' WHERE codigo='CMT-2023'",
            "UPDATE presupuesto SET version=2 WHERE id=(SELECT presupuesto_id FROM cronograma WHERE id IN (" + CMT
                    + "))"
        }) {
            try (Connection c = dataSource.getConnection();
                    Statement s = c.createStatement()) {
                c.setAutoCommit(false);
                s.execute("CREATE TEMP TABLE actividad (LIKE public.actividad INCLUDING DEFAULTS) ON COMMIT DROP");
                try (var insert = c.prepareStatement(
                        "INSERT INTO actividad SELECT * FROM jsonb_populate_recordset(NULL::actividad, ?::jsonb)")) {
                    insert.setString(1, original);
                    insert.executeUpdate();
                }
                s.execute(edit);
                String edited = scalar(s, "SELECT jsonb_agg(to_jsonb(a) ORDER BY id)::text FROM actividad a");
                s.execute(migrationSql());
                assertEquals(
                        edited,
                        scalar(s, "SELECT jsonb_agg(to_jsonb(a) ORDER BY id)::text FROM actividad a"),
                        "one differing map must protect the entire schedule");
                c.rollback();
            }
        }
    }

    private int migrate(String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate()
                .migrationsExecuted;
    }

    private String migrationSql() throws Exception {
        try (var resource = getClass().getResourceAsStream(MIGRATION)) {
            assertNotNull(resource);
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String deviations(Statement s) throws Exception {
        return scalar(
                s,
                "SELECT count(*) FROM actividad a WHERE cronograma_id IN (" + CMT + ") "
                        + "AND peso_ponderado <> (SELECT sum(value::numeric) FROM jsonb_each_text(a.avance_por_periodo))");
    }

    private static String preserved(Statement s) throws Exception {
        return scalar(
                s,
                "SELECT jsonb_build_object('activities',(SELECT jsonb_agg(CASE WHEN cronograma_id IN ("
                        + CMT
                        + ") THEN to_jsonb(a)-'avance_por_periodo' ELSE to_jsonb(a) END ORDER BY id) FROM actividad a),"
                        + "'cronograms',(SELECT jsonb_agg(to_jsonb(cr) ORDER BY id) FROM cronograma cr),"
                        + "'budgets',(SELECT jsonb_agg(to_jsonb(p) ORDER BY id) FROM presupuesto p),"
                        + "'rubros',(SELECT jsonb_agg(to_jsonb(r) ORDER BY id) FROM rubro r),"
                        + "'projects',(SELECT jsonb_agg(to_jsonb(pr) ORDER BY id) FROM proyecto pr))::text");
    }

    private static String scalar(Statement s, String sql) throws Exception {
        try (ResultSet r = s.executeQuery(sql)) {
            assertTrue(r.next());
            return r.getString(1);
        }
    }

    public static final class SeedProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.flyway.migrate-at-start",
                    "false",
                    "quarkus.hibernate-orm.database.generation",
                    "none",
                    "quarkus.datasource.devservices.enabled",
                    "true");
        }
    }
}
