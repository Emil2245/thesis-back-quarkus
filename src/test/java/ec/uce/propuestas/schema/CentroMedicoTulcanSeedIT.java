package ec.uce.propuestas.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/** Focused V014/V016 integration coverage for the Centro Médico Tulcán APU seed. */
@QuarkusTest
@TestProfile(CentroMedicoTulcanSeedIT.SeedProfile.class)
class CentroMedicoTulcanSeedIT {

    private static final List<String> SOURCE_GAP_CODES =
            List.of("500BFU", "500BFV", "500BKC", "500COH", "501067", "501B50", "501DIH", "501DS8", "501DS9", "504239");

    @Inject
    DataSource dataSource;

    @Test
    void v016_completes_provisional_source_gaps_without_changing_existing_data() throws Exception {
        String nonProvisionalDetailsBeforeV016 = rebuildThroughV015ThenApplyLatest();

        assertEquals(298, queryLong("SELECT count(*) FROM apu a " + cmtApuScope()));
        assertEquals(
                298,
                queryLong("SELECT count(*) FROM rubro r JOIN capitulo c ON c.id = r.capitulo_id "
                        + "JOIN presupuesto p ON p.id = c.presupuesto_id JOIN proyecto pr ON pr.id = p.proyecto_id "
                        + "WHERE pr.nombre_proyecto = 'Cetro Médico Tulcán' AND p.version = 1"));
        assertEquals(
                298,
                queryLong("SELECT count(*) FROM rubro r JOIN apu a ON a.id = r.apu_id " + cmtApuScope()),
                "every CMT budget row must retain its APU link");
        assertEquals(
                6,
                queryLong("SELECT count(*) FROM apu a " + cmtApuScope() + " AND a.codigo LIKE '%-B'"),
                "the six duplicate-budget APU variants must retain their -B codes");

        assertEquals(
                SOURCE_GAP_CODES,
                queryStrings("SELECT a.codigo FROM apu a " + cmtApuScope()
                        + " AND EXISTS (SELECT 1 FROM apu_detalle d JOIN apu_seccion s ON s.id = d.seccion_id"
                        + "             WHERE s.apu_id = a.id AND d.descripcion LIKE '%PROVISIONAL%')"
                        + " ORDER BY a.codigo"),
                "the ten source-gap APUs must be explicitly labelled as provisional");
        assertEquals(
                298,
                queryLong("SELECT count(*) FROM apu a " + cmtApuScope()
                        + " AND (SELECT count(*) FROM apu_seccion s WHERE s.apu_id = a.id) = 4"
                        + " AND EXISTS (SELECT 1 FROM apu_detalle d JOIN apu_seccion s ON s.id = d.seccion_id"
                        + "             WHERE s.apu_id = a.id)"),
                "all 298 APUs must have four sections and at least one detail");
        assertEquals(
                0,
                queryLong("SELECT count(*) FROM apu a " + cmtApuScope()
                        + " AND (SELECT count(*) FROM apu_seccion s WHERE s.apu_id = a.id) <> 4"),
                "all APUs must use exactly four canonical sections");
        assertEquals(
                0,
                queryLong("SELECT count(*) FROM apu a " + cmtApuScope()
                        + " AND NOT EXISTS (SELECT 1 FROM apu_detalle d JOIN apu_seccion s ON s.id = d.seccion_id"
                        + "                 WHERE s.apu_id = a.id)"),
                "all APUs must have at least one detail");
        assertEquals(
                40,
                queryLong("SELECT count(*) FROM apu_detalle d JOIN apu_seccion s ON s.id = d.seccion_id "
                        + "JOIN apu a ON a.id = s.apu_id " + cmtApuScope()
                        + " AND a.codigo IN (" + quotedSourceGapCodes() + ")"
                        + " AND d.descripcion LIKE '%PROVISIONAL%'"),
                "each provisional APU must have one labelled detail per section");
        assertEquals(
                "395115.320000",
                queryString("SELECT p.total::text FROM presupuesto p "
                        + "JOIN proyecto pr ON pr.id = p.proyecto_id WHERE pr.nombre_proyecto = 'Cetro Médico Tulcán'"
                        + " AND p.version = 1"),
                "the existing CMT budget total must remain unchanged");
        assertEquals(
                nonProvisionalDetailsBeforeV016,
                queryString(nonProvisionalDetailsFingerprintSql()),
                "V016 must not change any non-provisional APU details");

        assertTrue(
                queryLong("SELECT count(*) FROM apu_detalle d JOIN apu_seccion s ON s.id = d.seccion_id "
                                + "JOIN apu a ON a.id = s.apu_id " + cmtApuScope()
                                + " AND d.es_herramienta_menor AND d.insumo_id IS NULL")
                        > 0,
                "the source must include HM detail rows");
        assertEquals(
                0,
                queryLong("SELECT count(*) FROM apu_detalle d JOIN apu_seccion s ON s.id = d.seccion_id "
                        + "JOIN apu a ON a.id = s.apu_id " + cmtApuScope()
                        + " AND ((d.es_herramienta_menor AND d.insumo_id IS NOT NULL)"
                        + "      OR (NOT d.es_herramienta_menor AND d.insumo_id IS NULL))"),
                "only HM details may have a null input FK");
        assertEquals(
                0,
                queryLong("SELECT count(*) FROM apu_detalle d JOIN apu_seccion s ON s.id = d.seccion_id "
                        + "JOIN apu a ON a.id = s.apu_id JOIN insumo i ON i.id = d.insumo_id " + cmtApuScope()
                        + " AND NOT d.es_herramienta_menor AND i.base_id <> ("
                        + "   SELECT b.id FROM base_insumos b JOIN proyecto bp ON bp.id = b.proyecto_id"
                        + "   WHERE bp.nombre_proyecto = 'Cetro Médico Tulcán' AND b.tipo = 'PROYECTO')"),
                "non-HM details must reference the CMT project base");
    }

    private String rebuildThroughV015ThenApplyLatest() throws Exception {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(org.flywaydb.core.api.MigrationVersion.fromVersion("15"))
                .load()
                .migrate();
        String nonProvisionalDetailsBeforeV016 = queryString(nonProvisionalDetailsFingerprintSql());
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        return nonProvisionalDetailsBeforeV016;
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

    private static String nonProvisionalDetailsFingerprintSql() {
        return "SELECT md5(coalesce(string_agg(concat_ws('|', a.codigo, s.tipo, d.orden, d.descripcion, "
                + "d.es_herramienta_menor, d.cantidad, d.tarifa_jornal, d.costo_hora, d.rendimiento, d.unidad, "
                + "d.precio_unitario_tarifa, d.costo, i.codigo), E'\\n' ORDER BY a.codigo, s.tipo, d.orden, d.id), '')) "
                + "FROM apu_detalle d JOIN apu_seccion s ON s.id = d.seccion_id "
                + "JOIN apu a ON a.id = s.apu_id JOIN presupuesto p ON p.id = a.presupuesto_id "
                + "JOIN proyecto pr ON pr.id = p.proyecto_id LEFT JOIN insumo i ON i.id = d.insumo_id "
                + "WHERE pr.nombre_proyecto = 'Cetro Médico Tulcán' AND p.version = 1 "
                + "AND a.codigo NOT IN (" + quotedSourceGapCodes() + ")";
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

    private static String cmtApuScope() {
        return "JOIN presupuesto p ON p.id = a.presupuesto_id JOIN proyecto pr ON pr.id = p.proyecto_id "
                + "WHERE pr.nombre_proyecto = 'Cetro Médico Tulcán' AND p.version = 1";
    }

    private static String quotedSourceGapCodes() {
        return SOURCE_GAP_CODES.stream()
                .map(code -> "'" + code + "'")
                .collect(java.util.stream.Collectors.joining(", "));
    }

    public static final class SeedProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.flyway.migrate-at-start", "false", "quarkus.hibernate-orm.enabled", "false");
        }
    }
}
