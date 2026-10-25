package ec.uce.propuestas.cronograma;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** Plan 021 — V021 rellena solo los mapas vacíos; no toca mapas parciales. */
@QuarkusTest
@TestProfile(NormalizeAdvanceMapsMigrationTest.AisladoProfile.class)
class NormalizeAdvanceMapsMigrationTest {
    private static final String DESVIADAS = "SELECT count(*) FROM actividad a WHERE round(a.peso_ponderado, 4) <> "
            + "round(COALESCE((SELECT sum(value::numeric) FROM jsonb_each_text(a.avance_por_periodo)), 0), 4)";

    @Inject
    DataSource dataSource;

    @Test
    void solo_mapas_vacios_se_rellenan_tras_v021() throws Exception {
        migrate("20");
        String parciales;
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            // Mapas parciales intencionales (seed UCE al 50%) y un mapa no vacío desviado sintético.
            s.execute("UPDATE actividad SET avance_por_periodo='{\"1\":\"1.0000\",\"2\":\"1.0000\"}' "
                    + "WHERE id=(SELECT min(id)+1 FROM actividad)");
            s.execute("UPDATE actividad SET avance_por_periodo='{}' WHERE id=(SELECT min(id) FROM actividad)");
            assertEquals("1", scalar(s, "SELECT count(*) FROM actividad WHERE avance_por_periodo='{}'"));
            parciales = scalar(
                    s,
                    "SELECT jsonb_agg(to_jsonb(a) ORDER BY id)::text FROM actividad a "
                            + "WHERE avance_por_periodo<>'{}'");
            assertTrue(Long.parseLong(scalar(s, DESVIADAS)) > 0);
        }
        assertEquals(1, migrate("21"));
        try (Connection c = dataSource.getConnection();
                Statement s = c.createStatement()) {
            assertEquals("0", scalar(s, "SELECT count(*) FROM actividad WHERE avance_por_periodo='{}'"));
            assertEquals(
                    parciales,
                    scalar(
                            s,
                            "SELECT jsonb_agg(to_jsonb(a) ORDER BY id)::text FROM actividad a WHERE id NOT IN "
                                    + "(SELECT min(id) FROM actividad)"),
                    "los mapas no vacíos (incluidos los parciales) no se modifican");
            assertEquals(
                    "6",
                    scalar(
                            s,
                            "SELECT count(*) FROM actividad a JOIN cronograma cr ON cr.id=a.cronograma_id "
                                    + "JOIN presupuesto p ON p.id=cr.presupuesto_id JOIN proyecto pr ON pr.id=p.proyecto_id "
                                    + "WHERE pr.codigo='UCE-CON-2026-B' AND a.id <> (SELECT min(id)+1 FROM actividad) AND round(a.peso_ponderado,4) <> round((SELECT "
                                    + "sum(value::numeric) FROM jsonb_each_text(a.avance_por_periodo)),4)"),
                    "las seis actividades UCE al 50% conservan su suma parcial");
            // La actividad antes vacía suma su peso y cubre 1..numero_periodos.
            try (ResultSet r = s.executeQuery("SELECT (SELECT count(*) FROM jsonb_object_keys(a.avance_por_periodo)), "
                    + "cr.numero_periodos, a.peso_ponderado = (SELECT sum(value::numeric) FROM "
                    + "jsonb_each_text(a.avance_por_periodo)) FROM actividad a JOIN cronograma cr ON "
                    + "cr.id=a.cronograma_id WHERE a.id=(SELECT min(id) FROM actividad)")) {
                assertTrue(r.next());
                assertEquals(r.getInt(2), r.getInt(1));
                assertTrue(r.getBoolean(3));
            }
            String antes = scalar(s, "SELECT jsonb_agg(to_jsonb(a) ORDER BY id)::text FROM actividad a");
            try (var resource =
                    getClass().getResourceAsStream("/db/migration/V021__normalize_cronograma_advance_maps.sql")) {
                assertNotNull(resource);
                s.execute(new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
            assertEquals(antes, scalar(s, "SELECT jsonb_agg(to_jsonb(a) ORDER BY id)::text FROM actividad a"));
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

    private static String scalar(Statement s, String sql) throws Exception {
        try (ResultSet r = s.executeQuery(sql)) {
            assertTrue(r.next());
            return r.getString(1);
        }
    }

    /** Perfil propio: base de datos aislada, para no adelantar el esquema de las migraciones V019/V020. */
    public static final class AisladoProfile implements io.quarkus.test.junit.QuarkusTestProfile {
        @Override
        public java.util.Map<String, String> getConfigOverrides() {
            var m = new java.util.HashMap<>(new CmtSeedWeightedAdvanceMigrationTest.SeedProfile().getConfigOverrides());
            m.put("quarkus.datasource.devservices.reuse", "false");
            m.put("quarkus.datasource.devservices.db-name", "migr021");
            return m;
        }
    }
}
