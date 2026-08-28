package ec.uce.propuestas.schema;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(RepresentativeSeedsIT.SeedsProfile.class)
class RepresentativeSeedsIT {

    private static final List<String> PUBLIC_TABLES = List.of(
            "usuario", "firmante", "proyecto", "presupuesto", "apu", "apu_detalle", "base_insumos",
            "insumo", "plantilla_apu", "plantilla_proyecto");

    private static final String UUIDV7_VALUE =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}";
    private static final Pattern UUIDV7_LITERAL = Pattern.compile("'(" + UUIDV7_VALUE + ")'\\s*::uuid");
    private static final Pattern UNCAST_UUIDV7_LITERAL = Pattern.compile("'" + UUIDV7_VALUE + "'(?!\\s*::uuid)");

    @Inject
    DataSource dataSource;

    @Test
    void representative_seed_database_is_complete_and_reproducible() throws Exception {
        rebuild();
        assertFinalSchemaHasNoLegacyAuxiliaryArtifacts();
        assertProjectsAndTitles();
        assertBasesAndFlexibleApus();
        assertTemplatesAndInertRows();
        Map<String, List<String>> first = publicIds();

        rebuild();
        assertFinalSchemaHasNoLegacyAuxiliaryArtifacts();
        assertEquals(first, publicIds(), "seed public_id values must be stable across rebuilds");
        for (Map.Entry<String, List<String>> entry : publicIds().entrySet()) {
            assertFalse(entry.getValue().isEmpty(), entry.getKey());
            assertEquals(entry.getValue().size(), entry.getValue().stream().distinct().count(), entry.getKey());
            entry.getValue().forEach(id -> assertTrue(id.matches(
                    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"), id));
        }
    }

    @Test
    void migration_sources_author_public_id_explicitly_and_forbid_compatibility_artifacts() throws Exception {
        List<String> sources = List.of(
                readMigrationResource("db/migration/V002__seed.sql"),
                readMigrationResource("db/migration/V003__seed_insumos.sql"),
                readMigrationResource("db/migration/V004__seed_escenarios.sql"));

        Map<String, Integer> insertCounts = new LinkedHashMap<>();
        Map<String, Integer> explicitUuidLiteralCounts = new LinkedHashMap<>();
        for (String table : PUBLIC_TABLES) {
            insertCounts.put(table, 0);
            explicitUuidLiteralCounts.put(table, 0);
        }

        for (String source : sources) {
            assertCompatibilityArtifactsAreAbsent(source);
            for (InsertStatement statement : extractApiTableInserts(source)) {
                int before = insertCounts.get(statement.table);
                insertCounts.put(statement.table, before + 1);
                assertTrue(statement.columnList.contains("public_id"),
                        statement.table + " INSERT must declare public_id in its column list");
                assertTrue(UUIDV7_LITERAL.matcher(statement.body).find(),
                        statement.table + " INSERT must contain an explicit UUIDv7 literal cast with ::uuid");
                assertFalse(UNCAST_UUIDV7_LITERAL.matcher(statement.body).find(),
                        statement.table + " INSERT contains an uncast UUIDv7 literal");
                explicitUuidLiteralCounts.put(statement.table, explicitUuidLiteralCounts.get(statement.table) + 1);
            }
        }

        for (String table : PUBLIC_TABLES) {
            int inserts = insertCounts.get(table);
            assertTrue(inserts > 0,
                    "API table " + table + " must have at least one INSERT across V002-V004 (found " + inserts + ")");
            assertEquals(inserts, explicitUuidLiteralCounts.get(table),
                    "API table " + table + " must have every INSERT covered by an explicit UUIDv7 literal");
        }
    }

    private static String readMigrationResource(String path) throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, "migration resource not found on classpath: " + path);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
                return builder.toString();
            }
        }
    }

    private static void assertCompatibilityArtifactsAreAbsent(String source) {
        for (String banned : List.of(
                "es_auxiliar",
                "apu_auxiliar_id",
                "apuAuxiliarId",
                "cdAuxiliar",
                "CAMBIO_AUXILIAR",
                "fn_seed_public_id",
                "seed_public_id",
                "ALTER TABLE apu ADD COLUMN",
                "ALTER COLUMN public_id DROP DEFAULT",
                "DROP COLUMN")) {
            assertFalse(source.contains(banned),
                    "migration source must not contain compatibility token: " + banned);
        }
    }

    private static List<InsertStatement> extractApiTableInserts(String source) {
        Set<String> apiTables = Set.copyOf(PUBLIC_TABLES);
        Pattern headerPattern = Pattern.compile(
                "(?imsx)\\bINSERT\\s+INTO\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(([^)]*)\\)");
        List<InsertStatement> statements = new ArrayList<>();
        Matcher matcher = headerPattern.matcher(source);
        while (matcher.find()) {
            String table = matcher.group(1);
            if (!apiTables.contains(table)) continue;
            String columnList = matcher.group(2);
            int bodyStart = matcher.end();
            String body = scanStatementBody(source, bodyStart);
            statements.add(new InsertStatement(table, columnList, body));
        }
        return statements;
    }

    private static String scanStatementBody(String source, int fromIndex) {
        StringBuilder body = new StringBuilder();
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        for (int i = fromIndex; i < source.length(); i++) {
            char current = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (inLineComment) {
                body.append(current);
                if (current == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                body.append(current);
                if (current == '*' && next == '/') {
                    body.append(next);
                    i++;
                    inBlockComment = false;
                }
                continue;
            }
            if (inSingleQuote) {
                body.append(current);
                if (current == '\'' && next != '\'') inSingleQuote = false;
                else if (current == '\'' && next == '\'') { body.append(next); i++; }
                continue;
            }
            if (inDoubleQuote) {
                body.append(current);
                if (current == '"') inDoubleQuote = false;
                continue;
            }
            if (current == '-' && next == '-') {
                body.append(current).append(next);
                i++;
                inLineComment = true;
                continue;
            }
            if (current == '/' && next == '*') {
                body.append(current).append(next);
                i++;
                inBlockComment = true;
                continue;
            }
            if (current == '\'') { body.append(current); inSingleQuote = true; continue; }
            if (current == '"') { body.append(current); inDoubleQuote = true; continue; }
            if (current == '(') depth++;
            else if (current == ')') depth--;
            if (current == ';' && depth <= 0) {
                body.append(current);
                return body.toString();
            }
            body.append(current);
        }
        return body.toString();
    }

    private record InsertStatement(String table, String columnList, String body) {}

    private void rebuild() {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    }

    private void assertProjectsAndTitles() throws Exception {
        assertEquals(Set.of("BORRADOR", "EN_PROCESO", "FINALIZADO"), querySet("SELECT estado FROM proyecto"));
        assertEquals(3, queryLong("SELECT count(*) FROM proyecto WHERE titulo_et_1 = 'ESPECIFICACIONES TÉCNICAS'"));
        assertEquals(1, queryLong("SELECT count(*) FROM proyecto WHERE estado = 'FINALIZADO' AND titulo_et_2 = 'ESTANCIA-ACADEMICA'"));
        assertTrue(queryLong("SELECT count(*) FROM parametros_sistema WHERE id = 1") == 1);
        assertEquals(1, queryLong("SELECT count(*) FROM parametros_sistema WHERE rango_hm_max = 0.2000 AND rango_ci_max = 1.0000"));
    }

    private void assertFinalSchemaHasNoLegacyAuxiliaryArtifacts() throws Exception {
        assertEquals(0, queryLong(
                "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_schema = current_schema() "
                        + "AND lower(column_name) IN ('es_auxiliar', 'apu_auxiliar_id', 'cd_auxiliar')"));
        assertEquals(0, queryLong(
                "SELECT count(*) FROM pg_class c "
                        + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                        + "WHERE n.nspname = current_schema() "
                        + "AND lower(c.relname) LIKE '%auxiliar%'"));
        assertEquals(0, queryLong(
                "SELECT count(*) FROM pg_constraint c "
                        + "JOIN pg_namespace n ON n.oid = c.connamespace "
                        + "WHERE n.nspname = current_schema() "
                        + "AND lower(c.conname) LIKE '%auxiliar%'"));
    }

    private void assertBasesAndFlexibleApus() throws Exception {
        assertEquals(Set.of("CENTRAL", "PERSONAL", "PROYECTO"), querySet("SELECT DISTINCT tipo FROM base_insumos"));
        assertTrue(queryLong("SELECT count(*) FROM base_insumos WHERE tipo = 'PERSONAL'") >= 1);
        assertTrue(queryLong("SELECT count(*) FROM insumo i JOIN base_insumos b ON b.id = i.base_id WHERE b.tipo = 'PERSONAL'") >= 5);
        assertTrue(queryLong("SELECT count(*) FROM apu a JOIN apu_seccion s ON s.apu_id = a.id GROUP BY a.id HAVING count(*) = 4 AND count(DISTINCT s.tipo) = 4") >= 1);
        assertTrue(queryLong("SELECT count(*) FROM apu a JOIN apu_seccion s ON s.apu_id = a.id GROUP BY a.id HAVING count(*) = 1") >= 1);
        assertTrue(queryLong("SELECT count(*) FROM insumo WHERE tipo = 'TRANSPORTE'") >= 1);
        assertEquals(0, queryLong("SELECT count(*) FROM apu_detalle WHERE insumo_id IS NULL AND descripcion LIKE '%auxiliar%'"));
    }

    private void assertTemplatesAndInertRows() throws Exception {
        assertEquals(2, queryLong("SELECT count(*) FROM plantilla_apu"));
        assertEquals(2, queryLong("SELECT count(*) FROM plantilla_apu WHERE snapshot_secciones::text NOT LIKE '%apuAuxiliarId%'"));
        assertTrue(queryLong("SELECT count(*) FROM plantilla_proyecto") >= 1);
        assertTrue(queryLong("SELECT count(*) FROM presupuesto_rubro") >= 1);
        assertTrue(queryLong("SELECT count(*) FROM cronograma_actividad") >= 1);
    }

    private Map<String, List<String>> publicIds() throws Exception {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String table : PUBLIC_TABLES) result.put(table, queryStrings("SELECT public_id::text FROM " + table + " ORDER BY id"));
        return result;
    }

    private Set<String> querySet(String sql) throws Exception {
        return Set.copyOf(queryStrings(sql));
    }

    private List<String> queryStrings(String sql) throws Exception {
        List<String> result = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) result.add(rows.getString(1));
        }
        return result;
    }

    private long queryLong(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql); ResultSet rows = statement.executeQuery()) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    public static final class SeedsProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.flyway.migrate-at-start", "false", "quarkus.hibernate-orm.enabled", "false");
        }
    }
}
