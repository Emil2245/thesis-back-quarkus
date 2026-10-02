package ec.uce.propuestas.cronograma;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.cronograma.export.CronogramaExportPreflightService;
import ec.uce.propuestas.cronograma.export.FormatoExportacion;
import ec.uce.propuestas.cronograma.service.VistasCronogramaService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(CmtSeedWeightedAdvanceMigrationTest.SeedProfile.class)
class UceSeedWeightedAdvanceMigrationTest {
    private static final String UCE = "SELECT cr.id FROM cronograma cr JOIN presupuesto p ON p.id=cr.presupuesto_id "
            + "JOIN proyecto pr ON pr.id=p.proyecto_id WHERE pr.codigo='UCE-CON-2026-B' AND p.version=1";
    private static final String FIRST = "SELECT min(id) FROM actividad WHERE cronograma_id IN (" + UCE + ")";
    private static final String MIGRATION = "/db/migration/V020__repair_uce_seed_weighted_advances.sql";

    @Inject
    DataSource dataSource;

    @Inject
    CronogramaExportPreflightService preflight;

    @Inject
    VistasCronogramaService vistas;

    @Test
    void convierte_unidades_sin_completar_programacion_y_protege_el_cohorte_entero() throws Exception {
        migrate("19");
        String original;
        String preserved;
        UUID presupuesto;
        Long owner;
        UUID cronograma;
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            cronograma = UUID.fromString(scalar(s, "SELECT public_id FROM cronograma WHERE id IN (" + UCE + ")"));
            assertEquals("12", deviations(s));
            try (ResultSet r = s.executeQuery(
                    "SELECT p.public_id, pr.usuario_id FROM presupuesto p JOIN proyecto pr ON pr.id=p.proyecto_id WHERE pr.codigo='UCE-CON-2026-B' AND p.version=1")) {
                assertTrue(r.next());
                presupuesto = r.getObject(1, UUID.class);
                owner = r.getLong(2);
            }
        }
        // Revisión actual real, no NULL: corregir unidades no debe alterarla.
        vistas.marcarRevisado(cronograma, owner);
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            original = activities(s);
            preserved = preserved(s);
            assertEquals(
                    "true",
                    scalar(
                            s,
                            "SELECT (total_general_revisado IS NOT NULL AND fecha_revision IS NOT NULL AND presupuesto_fingerprint_revisado IS NOT NULL)::text FROM cronograma WHERE id IN ("
                                    + UCE + ")"));
        }
        migrate("20");
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            assertEquals("6", deviations(s), "V020 debe dejar solo las seis programaciones deliberadamente parciales");
            assertEquals(preserved, preserved(s));
            assertEquals(
                    "71.3206",
                    scalar(
                            s,
                            "SELECT sum(value::numeric)::text FROM actividad a, LATERAL jsonb_each_text(a.avance_por_periodo) WHERE cronograma_id IN ("
                                    + UCE + ")"));
            try (ResultSet r = s.executeQuery(
                    "SELECT r.item, a.peso_ponderado, a.avance_por_periodo::text, e.key, e.value #>> '{}', jsonb_typeof(e.value) FROM actividad a JOIN rubro r ON r.id=a.rubro_id, LATERAL jsonb_each(a.avance_por_periodo) e WHERE cronograma_id IN ("
                            + UCE + ") ORDER BY r.item,e.key::int")) {
                int periods = 0;
                while (r.next()) {
                    boolean full = r.getString(1).startsWith("1.");
                    int count = full ? 8 : 4;
                    int period = Integer.parseInt(r.getString(4));
                    BigDecimal weight = r.getBigDecimal(2);
                    BigDecimal base =
                            weight.multiply(new BigDecimal("0.125")).setScale(4, java.math.RoundingMode.HALF_UP);
                    BigDecimal target = weight.multiply(new BigDecimal(full ? "1" : "0.5"))
                            .setScale(4, java.math.RoundingMode.HALF_UP);
                    BigDecimal expected =
                            period == count ? target.subtract(base.multiply(BigDecimal.valueOf(count - 1))) : base;
                    assertTrue(period >= 1 && period <= count);
                    assertEquals("string", r.getString(6));
                    BigDecimal value = new BigDecimal(r.getString(5));
                    assertTrue(value.scale() <= 4);
                    assertEquals(0, expected.compareTo(value));
                    periods++;
                }
                assertEquals(72, periods, "No agregar los meses 5–8 ausentes en las últimas seis actividades");
            }
            String corrected = activities(s);
            s.execute(migrationSql());
            assertEquals(corrected, activities(s));
        }
        assertEquals(0, migrate("20"));
        for (FormatoExportacion formato : FormatoExportacion.values()) {
            var result = preflight.evaluarPreflight(presupuesto, owner, formato);
            assertFalse(result.exportable());
            assertTrue(result.warnings().stream().noneMatch(w -> "cronograma-desactualizado".equals(w.codigo())));
            assertEquals(
                    6,
                    result.bloqueos().stream()
                            .filter(b -> "cronograma-desviacion".equals(b.codigo()))
                            .count());
        }
        for (String edit : new String[] {
            "UPDATE actividad SET avance_por_periodo='{}' WHERE id=(" + FIRST + ")",
            "UPDATE actividad SET avance_por_periodo=jsonb_set(avance_por_periodo,'{1}','\"0.1260\"') WHERE id=("
                    + FIRST + ")",
            "UPDATE actividad SET avance_por_periodo=avance_por_periodo-'8' WHERE id=(" + FIRST + ")",
            "UPDATE actividad SET avance_por_periodo=avance_por_periodo||'{\"9\":\"0.0000\"}' WHERE id=(" + FIRST + ")",
            "UPDATE actividad SET avance_por_periodo=jsonb_set(avance_por_periodo,'{1}','0.125') WHERE id=(" + FIRST
                    + ")",
            "UPDATE actividad SET peso_ponderado=peso_ponderado+0.0001 WHERE id=(" + FIRST + ")",
            "UPDATE rubro SET codigo='USUARIO' WHERE id=(SELECT rubro_id FROM actividad WHERE id=(" + FIRST + "))",
            "UPDATE rubro SET item='9.9.9' WHERE id=(SELECT rubro_id FROM actividad WHERE id=(" + FIRST + "))",
            "UPDATE cronograma SET numero_periodos=9 WHERE id IN (" + UCE + ")",
            "UPDATE cronograma SET unidad_tiempo='SEMANA' WHERE id IN (" + UCE + ")",
            "UPDATE presupuesto SET version=2 WHERE id=(SELECT presupuesto_id FROM cronograma WHERE id IN (" + UCE
                    + "))",
            "UPDATE proyecto SET codigo='USUARIO' WHERE codigo='UCE-CON-2026-B'",
            "UPDATE actividad SET cronograma_id=(SELECT id FROM cronograma WHERE id NOT IN (" + UCE
                    + ") LIMIT 1) WHERE id=(" + FIRST + ")",
            "UPDATE rubro SET capitulo_id=(SELECT ca.id FROM capitulo ca WHERE presupuesto_id NOT IN (SELECT presupuesto_id FROM cronograma WHERE id IN ("
                    + UCE + ")) LIMIT 1) WHERE id=(SELECT rubro_id FROM actividad WHERE id=(" + FIRST + "))",
            "INSERT INTO actividad SELECT * FROM actividad WHERE id=(" + FIRST + ")"
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
                String edited = preserved(s) + activities(s);
                s.execute(migrationSql());
                assertEquals(edited, preserved(s) + activities(s), edit);
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

    private static String activities(Statement s) throws Exception {
        return scalar(s, "SELECT jsonb_agg(to_jsonb(a) ORDER BY id)::text FROM actividad a");
    }

    private static String deviations(Statement s) throws Exception {
        return scalar(
                s,
                "SELECT count(*) FROM actividad a WHERE cronograma_id IN (" + UCE
                        + ") AND peso_ponderado <> (SELECT sum(value::numeric) FROM jsonb_each_text(a.avance_por_periodo))");
    }

    private static String preserved(Statement s) throws Exception {
        return scalar(
                s,
                "SELECT jsonb_build_object('activities',(SELECT jsonb_agg(CASE WHEN cronograma_id IN (" + UCE
                        + ") THEN to_jsonb(a)-'avance_por_periodo' ELSE to_jsonb(a) END ORDER BY id) FROM actividad a), 'cronograms',(SELECT jsonb_agg(to_jsonb(cr) ORDER BY id) FROM cronograma cr), 'budgets',(SELECT jsonb_agg(to_jsonb(p) ORDER BY id) FROM presupuesto p), 'rubros',(SELECT jsonb_agg(to_jsonb(r) ORDER BY id) FROM rubro r), 'projects',(SELECT jsonb_agg(to_jsonb(pr) ORDER BY id) FROM proyecto pr))::text");
    }

    private static String scalar(Statement s, String sql) throws Exception {
        try (ResultSet r = s.executeQuery(sql)) {
            assertTrue(r.next());
            return r.getString(1);
        }
    }
}
