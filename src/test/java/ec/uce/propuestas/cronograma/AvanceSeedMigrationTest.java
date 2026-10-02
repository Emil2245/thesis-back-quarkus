package ec.uce.propuestas.cronograma;

import static org.junit.jupiter.api.Assertions.*;

import ec.uce.propuestas.recalculo.Alcance;
import ec.uce.propuestas.recalculo.RecalculoService;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(AvanceSeedMigrationTest.LegacySeedProfile.class)
class AvanceSeedMigrationTest {
    @Inject
    DataSource dataSource;

    @Inject
    RecalculoService recalculo;

    @Test
    void normaliza_solo_numeros_y_desbloquea_consolidacion_de_seeds() throws Exception {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("17")
                .load()
                .migrate();
        String sql;
        try (var resource = getClass()
                .getResourceAsStream("/db/migration/V018__normalize_cronograma_advance_decimal_strings.sql")) {
            assertNotNull(resource, "V018 forward migration must exist");
            sql = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            // Rollback removes the shadow table before this pooled connection is reused.
            c.setAutoCommit(false);
            s.execute("CREATE TEMP TABLE actividad (id bigint, avance_por_periodo jsonb) ON COMMIT DROP");
            s.execute("INSERT INTO actividad VALUES "
                    + "(1, '{\"1\":0.123456789012345678901234567890,\"2\":2.5000,"
                    + "\"03\":\"02.5000\",\"4\":true,\"5\":null,\"6\":[1],\"7\":{}}'),"
                    + "(2, '{}'), (3, NULL), (4, 'null'), (5, '[1,2]'), (6, 'true')");
            s.execute(sql);
            String fingerprint = fingerprint(s);
            try (ResultSet r = s.executeQuery("SELECT avance_por_periodo->>'1', avance_por_periodo->>'2', "
                    + "avance_por_periodo->>'03', jsonb_typeof(avance_por_periodo->'4'), "
                    + "avance_por_periodo->'5' = 'null'::jsonb, avance_por_periodo->'6' = '[1]'::jsonb, "
                    + "avance_por_periodo->'7' = '{}'::jsonb FROM actividad WHERE id = 1")) {
                assertTrue(r.next());
                assertEquals("0.123456789012345678901234567890", r.getString(1));
                assertEquals("2.5000", r.getString(2));
                assertEquals("02.5000", r.getString(3));
                assertEquals("boolean", r.getString(4));
                assertTrue(r.getBoolean(5));
                assertTrue(r.getBoolean(6));
                assertTrue(r.getBoolean(7));
            }
            try (ResultSet r =
                    s.executeQuery("SELECT avance_por_periodo::text FROM actividad WHERE id > 1 ORDER BY id")) {
                for (String expected : new String[] {"{}", null, "null", "[1, 2]", "true"}) {
                    assertTrue(r.next());
                    assertEquals(expected, r.getString(1));
                }
            }
            s.execute(sql);
            assertEquals(fingerprint, fingerprint(s), "reapplication must be a semantic no-op");
            c.rollback();
        }
        List<Long> apus = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement();
                ResultSet r =
                        s.executeQuery("SELECT min(r.apu_id) FROM rubro r JOIN capitulo ca ON ca.id=r.capitulo_id "
                                + "JOIN presupuesto p ON p.id=ca.presupuesto_id JOIN cronograma cr ON cr.presupuesto_id=p.id "
                                + "JOIN proyecto pr ON pr.id=p.proyecto_id "
                                + "WHERE pr.nombre_proyecto IN ('Cetro Médico Tulcán', 'Rehabilitación de consultorios UCE') "
                                + "GROUP BY pr.id ORDER BY pr.id")) {
            while (r.next()) apus.add(r.getLong(1));
        }
        assertEquals(2, apus.size(), "actual CMT and UCE seeds must be present");
        String unchanged;
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            unchanged = seedFields(s);
            try (ResultSet r = s.executeQuery("SELECT count(*) FROM actividad a, "
                    + "LATERAL jsonb_each(a.avance_por_periodo) e WHERE jsonb_typeof(e.value)='number'")) {
                assertTrue(r.next());
                assertTrue(r.getLong(1) > 0, "V017 must exercise actual numeric legacy advances");
            }
        }
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            assertEquals(unchanged, seedFields(s), "migration must preserve every other activity field");
            try (ResultSet r = s.executeQuery("SELECT count(*) FROM actividad a, "
                    + "LATERAL jsonb_each(a.avance_por_periodo) e WHERE jsonb_typeof(e.value)<>'string'")) {
                assertTrue(r.next());
                assertEquals(0, r.getLong(1), "all actual seed advance entries must now be strings");
            }
        }
        for (Long apu : apus) {
            assertDoesNotThrow(
                    () -> recalculo.recalcular(new Alcance.Apu(apu)),
                    "linked APU recalculation must consolidate the actual legacy seed schedule");
        }
    }

    private static String seedFields(Statement s) throws Exception {
        try (ResultSet r = s.executeQuery(
                "SELECT jsonb_agg(to_jsonb(a) - 'avance_por_periodo' ORDER BY id)::text FROM actividad a")) {
            assertTrue(r.next());
            return r.getString(1);
        }
    }

    private static String fingerprint(Statement s) throws Exception {
        try (ResultSet r = s.executeQuery(
                "SELECT jsonb_agg(jsonb_build_array(id, avance_por_periodo) ORDER BY id)::text FROM actividad")) {
            assertTrue(r.next());
            return r.getString(1);
        }
    }

    public static final class LegacySeedProfile implements QuarkusTestProfile {
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
