package ec.uce.propuestas.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** Focused V015 integration coverage for the sequential CMT monthly schedule seed. */
@QuarkusTest
@TestProfile(CentroMedicoTulcanScheduleIT.SeedProfile.class)
class CentroMedicoTulcanScheduleIT {

    @Inject
    DataSource dataSource;

    @Test
    void v015_assigns_cmt_activities_to_sequential_completed_monthly_batches_only() throws Exception {
        rebuild();

        assertEquals(298, queryLong("SELECT count(*) FROM actividad a " + cmtActivityScope()));
        assertEquals(
                0,
                queryLong("SELECT count(*) FROM actividad a " + cmtActivityScope()
                        + " AND ((SELECT count(*) FROM jsonb_object_keys(a.avance_por_periodo)) <> 1"
                        + "      OR NOT (a.avance_por_periodo ? '1' OR a.avance_por_periodo ? '2'"
                        + "              OR a.avance_por_periodo ? '3' OR a.avance_por_periodo ? '4'"
                        + "              OR a.avance_por_periodo ? '5' OR a.avance_por_periodo ? '6'"
                        + "              OR a.avance_por_periodo ? '7' OR a.avance_por_periodo ? '8'"
                        + "              OR a.avance_por_periodo ? '9' OR a.avance_por_periodo ? '10'"
                        + "              OR a.avance_por_periodo ? '11' OR a.avance_por_periodo ? '12')"
                        + "      OR (SELECT value::numeric FROM jsonb_each_text(a.avance_por_periodo)) <> 1.0000)"),
                "each CMT activity must have exactly one period key with full completion");
        assertEquals(
                List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"),
                queryStrings("SELECT DISTINCT keys.period_key::integer AS period_number FROM actividad a "
                        + "CROSS JOIN LATERAL jsonb_object_keys(a.avance_por_periodo) AS keys(period_key) "
                        + "JOIN cronograma cr ON cr.id = a.cronograma_id "
                        + "JOIN presupuesto p ON p.id = cr.presupuesto_id "
                        + "JOIN proyecto pr ON pr.id = p.proyecto_id "
                        + "WHERE pr.nombre_proyecto = 'Cetro Médico Tulcán' AND p.version = 1 "
                        + "ORDER BY period_number"),
                "all twelve monthly periods must contain at least one activity");
        assertEquals(
                0,
                queryLong("WITH ordered_activities AS ("
                        + " SELECT (SELECT key::integer FROM jsonb_object_keys(a.avance_por_periodo) AS keys(key)) AS period_number,"
                        + "        lag((SELECT key::integer FROM jsonb_object_keys(a.avance_por_periodo) AS keys(key)))"
                        + "            OVER (ORDER BY string_to_array(r.item, '.')::integer[], r.id) AS previous_period"
                        + " FROM actividad a JOIN rubro r ON r.id = a.rubro_id " + cmtActivityScope()
                        + ") SELECT count(*) FROM ordered_activities"
                        + " WHERE previous_period IS NOT NULL AND period_number < previous_period"),
                "monthly assignments must form contiguous batches in natural budget order");
        assertEquals(
                "100.0000",
                queryString("SELECT to_char(sum(a.peso_ponderado), 'FM999999990.0000') FROM actividad a "
                        + cmtActivityScope()),
                "CMT weighted progress must remain 100 percent");
        assertEquals(
                "FINALIZADO",
                queryString("SELECT pr.estado FROM proyecto pr WHERE pr.nombre_proyecto = 'Cetro Médico Tulcán'"),
                "the project must retain completed-project semantics");
        assertEquals(
                1,
                queryLong("SELECT count(*) FROM cronograma cr JOIN presupuesto p ON p.id = cr.presupuesto_id "
                        + "JOIN proyecto pr ON pr.id = p.proyecto_id "
                        + "WHERE pr.nombre_proyecto = 'Cetro Médico Tulcán' AND p.version = 1 "
                        + "AND cr.unidad_tiempo = 'MES' AND cr.numero_periodos = 12"));

        assertEquals(
                12,
                queryLong("SELECT count(*) FROM actividad a WHERE NOT (" + cmtActivityPredicate() + ")"),
                "the two non-CMT schedules must retain their original activity rows");
        assertEquals(
                0,
                queryLong("SELECT count(*) FROM actividad a WHERE NOT (" + cmtActivityPredicate() + ")"
                        + " AND a.avance_por_periodo NOT IN ("
                        + "'{\"1\":0.125,\"2\":0.125,\"3\":0.125,\"4\":0.125,\"5\":0.125,\"6\":0.125,\"7\":0.125,\"8\":0.125}'::jsonb,"
                        + "'{\"1\":0.125,\"2\":0.125,\"3\":0.125,\"4\":0.125}'::jsonb)"),
                "non-CMT activity distributions must remain unchanged");
    }

    private void rebuild() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private long queryLong(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private String queryString(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }

    private List<String> queryStrings(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            java.util.ArrayList<String> values = new java.util.ArrayList<>();
            while (rows.next()) values.add(rows.getString(1));
            return values;
        }
    }

    private static String cmtActivityScope() {
        return "JOIN cronograma cr ON cr.id = a.cronograma_id "
                + "JOIN presupuesto p ON p.id = cr.presupuesto_id "
                + "JOIN proyecto pr ON pr.id = p.proyecto_id "
                + "WHERE pr.nombre_proyecto = 'Cetro Médico Tulcán' AND p.version = 1";
    }

    private static String cmtActivityPredicate() {
        return "EXISTS (SELECT 1 FROM cronograma cr JOIN presupuesto p ON p.id = cr.presupuesto_id "
                + "JOIN proyecto pr ON pr.id = p.proyecto_id "
                + "WHERE cr.id = a.cronograma_id AND pr.nombre_proyecto = 'Cetro Médico Tulcán' AND p.version = 1)";
    }

    public static final class SeedProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.flyway.migrate-at-start", "false", "quarkus.hibernate-orm.enabled", "false");
        }
    }
}
