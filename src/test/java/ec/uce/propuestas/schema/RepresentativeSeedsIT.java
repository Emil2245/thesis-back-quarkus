package ec.uce.propuestas.schema;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(RepresentativeSeedsIT.SeedsProfile.class)
class RepresentativeSeedsIT {

    private static final List<String> PUBLIC_TABLES = List.of(
            "usuario", "firmante", "proyecto", "presupuesto", "apu", "apu_detalle", "base_insumos",
            "insumo", "plantilla_apu", "plantilla_proyecto");

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
