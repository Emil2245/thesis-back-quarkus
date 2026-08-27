package ec.uce.propuestas.schema;

import static org.junit.jupiter.api.Assertions.*;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.sql.*;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(SchemaBaselineIT.V001OnlyProfile.class)
class SchemaBaselineIT {

    private static Set<String> names(String csv) {
        return Set.of(csv.split(","));
    }

    private static final Set<String> TABLES = names("usuario,refresh_token,token_usuario,proyecto,firmante,parametros_sistema,parametros_proyecto,"
            + "base_insumos,insumo,unidad_catalogo,presupuesto,capitulo,apu,apu_seccion,apu_detalle,rubro,plantilla_apu,"
            + "plantilla_proyecto,cronograma,actividad,valor_referencia,log_actividad,descuento_global_snapshot,"
            + "presupuesto_descuento_global,presupuesto_rubro,cronograma_actividad");
    private static final Set<String> PUBLIC_TABLES = names("usuario,firmante,proyecto,presupuesto,apu,apu_detalle,base_insumos,insumo,plantilla_apu,plantilla_proyecto");
    private static final Set<String> INTERNAL_TABLES = names("refresh_token,token_usuario,parametros_sistema,parametros_proyecto,apu_seccion,"
            + "descuento_global_snapshot,presupuesto_descuento_global,presupuesto_rubro,cronograma_actividad,capitulo,rubro,cronograma,"
            + "actividad,unidad_catalogo,valor_referencia,log_actividad");
    private static final Set<String> IDENTITY_TABLES = names("usuario,refresh_token,token_usuario,proyecto,firmante,base_insumos,insumo,presupuesto,"
            + "capitulo,apu,apu_seccion,apu_detalle,rubro,plantilla_apu,plantilla_proyecto,cronograma,actividad,presupuesto_rubro,cronograma_actividad");
    private static final Set<String> RANGES = names("rango_hm_min,rango_hm_max,rango_ci_min,rango_ci_max,rango_descuento_min,rango_descuento_max,rango_iva_min,rango_iva_max");

    @Inject
    DataSource dataSource;

    @BeforeEach
    void migrateOnlyV001() {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("1")).cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("1")).load().migrate();
    }

    @Test
    void baseline_declares_the_complete_structural_table_set() throws Exception {
        assertEquals(TABLES, tableNames());
    }

    @Test
    void relational_identity_and_foreign_keys_remain_bigint() throws Exception {
        for (String table : IDENTITY_TABLES) {
            Column id = column(table, "id");
            assertNotNull(id, table);
            assertEquals("bigint", id.type(), table);
            assertEquals("YES", id.identity(), table);
        }
        assertEquals("smallint", column("parametros_sistema", "id").type());
        assertEquals("bigint", column("parametros_proyecto", "proyecto_id").type());
        String sql = "SELECT kcu.table_name,kcu.column_name,c.data_type FROM information_schema.key_column_usage kcu "
                + "JOIN information_schema.table_constraints tc ON tc.constraint_name=kcu.constraint_name AND tc.table_schema=kcu.table_schema "
                + "JOIN information_schema.columns c ON c.table_schema=kcu.table_schema AND c.table_name=kcu.table_name AND c.column_name=kcu.column_name "
                + "WHERE kcu.table_schema=current_schema() AND tc.constraint_type='FOREIGN KEY'";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) assertEquals("bigint", rows.getString("data_type"), rows.getString(1) + "." + rows.getString(2));
        }
    }

    @Test
    void public_id_is_limited_to_api_addressable_tables() throws Exception {
        assertEquals(PUBLIC_TABLES, tablesWithColumn("public_id"));
        for (String table : PUBLIC_TABLES) {
            Column id = column(table, "public_id");
            assertNotNull(id, table);
            assertEquals("uuid", id.type(), table);
            assertEquals("NO", id.nullability(), table);
            assertTrue(id.defaultExpression().contains("uuidv7()"), table);
            assertTrue(hasUniqueConstraint(table, "public_id"), table);
        }
        assertTrue(tablesWithColumn("public_id").stream().noneMatch(INTERNAL_TABLES::contains));
        assertTrue(columnNames("parametros_sistema", "calc_%").isEmpty());
        assertTrue(columnNames("parametros_sistema", "display_%").isEmpty());
    }

    @Test
    void ranges_et_templates_discount_and_inert_seams_are_structural() throws Exception {
        assertEquals(RANGES, columnNames("parametros_sistema", "rango_%"));
        for (String range : RANGES) {
            Column value = column("parametros_sistema", range);
            assertEquals("numeric", value.type(), range);
            assertEquals(5, value.precision(), range);
            assertEquals(4, value.scale(), range);
        }
        assertColumns("proyecto:titulo_et_1:text,proyecto:titulo_et_2:text,proyecto:plantilla_proyecto_origen_id:bigint,"
                + "apu:especificacion_tecnica:text,plantilla_apu:especificacion_tecnica:text,plantilla_apu:snapshot_secciones:jsonb,"
                + "plantilla_proyecto:snapshot_estructura:jsonb,descuento_global_snapshot:porcentaje_aplicado:numeric,"
                + "descuento_global_snapshot:valores_originales:jsonb,presupuesto_descuento_global:porcentaje_actual:numeric,"
                + "presupuesto_rubro:presupuesto_id:bigint,presupuesto_rubro:apu_id:bigint,cronograma_actividad:presupuesto_id:bigint,cronograma_actividad:rubro_id:bigint");
        assertForeignKeys("base_insumos:usuario_id:usuario,proyecto:plantilla_proyecto_origen_id:plantilla_proyecto,"
                + "presupuesto_rubro:presupuesto_id:presupuesto,presupuesto_rubro:apu_id:apu,"
                + "cronograma_actividad:presupuesto_id:presupuesto,cronograma_actividad:rubro_id:rubro");
    }

    @Test
    void public_id_immutability_trigger_is_installed_on_each_public_table() throws Exception {
        assertEquals(PUBLIC_TABLES, tablesWithTrigger());
        assertTrue(exists("SELECT 1 FROM pg_proc WHERE proname='fn_assert_public_id_immutable' AND prorettype='trigger'::regtype"));
    }

    @Test
    void baseline_contains_no_apu_to_apu_link_columns() throws Exception {
        assertTrue(tablesWithColumn("es_auxiliar").isEmpty());
        assertTrue(tablesWithColumn("apu_auxiliar_id").isEmpty());
    }

    private Set<String> tableNames() throws Exception {
        return querySet("SELECT table_name FROM information_schema.tables WHERE table_schema=current_schema() "
                + "AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history'");
    }

    private Set<String> tablesWithColumn(String name) throws Exception {
        return querySet("SELECT table_name FROM information_schema.columns WHERE table_schema=current_schema() AND column_name=?", name);
    }

    private Set<String> columnNames(String table, String pattern) throws Exception {
        return querySet("SELECT column_name FROM information_schema.columns WHERE table_schema=current_schema() AND table_name=? AND column_name LIKE ?", table, pattern);
    }

    private Set<String> querySet(String sql, String... parameters) throws Exception {
        Set<String> result = new HashSet<>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) statement.setString(i + 1, parameters[i]);
            try (ResultSet rows = statement.executeQuery()) { while (rows.next()) result.add(rows.getString(1)); }
        }
        return result;
    }

    private Column column(String table, String name) throws Exception {
        String sql = "SELECT data_type,is_nullable,column_default,is_identity,numeric_precision,numeric_scale FROM information_schema.columns "
                + "WHERE table_schema=current_schema() AND table_name=? AND column_name=?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            statement.setString(2, name);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? new Column(row.getString(1), row.getString(2), row.getString(3), row.getString(4), row.getInt(5), row.getInt(6)) : null;
            }
        }
    }

    private void assertColumns(String specifications) throws Exception {
        for (String specification : specifications.split(",")) {
            String[] fields = specification.split(":");
            Column value = column(fields[0], fields[1]);
            assertNotNull(value, specification);
            assertEquals(fields[2], value.type(), specification);
        }
    }

    private boolean hasUniqueConstraint(String table, String column) throws Exception {
        return exists("SELECT 1 FROM information_schema.table_constraints tc JOIN information_schema.key_column_usage kcu "
                + "ON tc.constraint_name=kcu.constraint_name AND tc.table_schema=kcu.table_schema WHERE tc.table_schema=current_schema() "
                + "AND tc.table_name='" + table + "' AND tc.constraint_type='UNIQUE' AND kcu.column_name='" + column + "'");
    }

    private Set<String> tablesWithTrigger() throws Exception {
        return querySet("SELECT event_object_table FROM information_schema.triggers WHERE trigger_schema=current_schema() "
                + "AND trigger_name='trg_public_id_immutable'");
    }

    private boolean exists(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {
            return result.next();
        }
    }

    private void assertForeignKeys(String specifications) throws Exception {
        for (String specification : specifications.split(",")) {
            String[] fields = specification.split(":");
            String sql = "SELECT 1 FROM information_schema.table_constraints tc JOIN information_schema.key_column_usage kcu "
                    + "ON tc.constraint_name=kcu.constraint_name AND tc.table_schema=kcu.table_schema JOIN information_schema.constraint_column_usage ccu "
                    + "ON tc.constraint_name=ccu.constraint_name AND tc.table_schema=ccu.table_schema WHERE tc.table_schema=current_schema() "
                    + "AND tc.table_name='" + fields[0] + "' AND tc.constraint_type='FOREIGN KEY' AND kcu.column_name='" + fields[1]
                    + "' AND ccu.table_name='" + fields[2] + "'";
            assertTrue(exists(sql), specification);
        }
    }

    private record Column(String type, String nullability, String defaultExpression, String identity, int precision, int scale) {}

    public static final class V001OnlyProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.flyway.migrate-at-start", "false", "quarkus.hibernate-orm.enabled", "false");
        }
    }
}
