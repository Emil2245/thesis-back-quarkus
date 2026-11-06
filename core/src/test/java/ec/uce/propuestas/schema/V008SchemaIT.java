package ec.uce.propuestas.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

/**
 * Plan 019 — focused invariants on V008 applied to the LATEST migration.
 *
 * <p>Deliberately distinct from {@link SchemaBaselineIT}: that test targets
 * V001 only to lock the structural baseline and must keep
 * {@code capitulo}/{@code rubro} in the {@code INTERNAL_TABLES} set (V001
 * does not yet declare {@code public_id} on them). This test instead runs
 * the latest migration (V001–V008) and verifies the WU-03 invariants that
 * V008 introduces on {@code capitulo}/{@code rubro}: {@code public_id UUID
 * NOT NULL UNIQUE DEFAULT uuidv7()}, a generic {@code
 * trg_public_id_immutable} trigger that reuses the V001 function, no
 * bespoke per-table functions, and UUIDv7 values on existing or freshly
 * inserted rows.
 */
@QuarkusTest
class V008SchemaIT {

    private static final String UUIDV7_REGEX =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

    @Inject
    DataSource dataSource;

    @Test
    void flyway_history_records_successful_v008() throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT version, description, success FROM flyway_schema_history WHERE version='008'");
                ResultSet rows = statement.executeQuery()) {
            assertTrue(rows.next(), "flyway_schema_history must contain version 008");
            assertEquals("008", rows.getString("version"));
            assertNotNull(rows.getString("description"), "V008 description must be recorded");
            assertTrue(rows.getBoolean("success"), "V008 must have applied successfully");
        }
    }

    @Test
    void capitulo_and_rubro_public_id_is_uuid_not_null_unique_with_uuidv7_default() throws Exception {
        for (String table : new String[] {"capitulo", "rubro"}) {
            Column publicId = column(table, "public_id");
            assertNotNull(publicId, table + " must declare public_id column after V008");
            assertEquals("uuid", publicId.type(), table + ".public_id type must be uuid");
            assertEquals("NO", publicId.nullability(), table + ".public_id must be NOT NULL");
            assertNotNull(publicId.defaultExpression(), table + ".public_id must declare a DEFAULT");
            assertTrue(
                    publicId.defaultExpression().contains("uuidv7()"),
                    table + ".public_id DEFAULT must call uuidv7() — got: " + publicId.defaultExpression());
            assertTrue(hasUniqueConstraint(table, "public_id"), table + ".public_id must have a UNIQUE constraint");
        }
    }

    @Test
    void capitulo_and_rubro_have_trigger_using_shared_function_from_v001() throws Exception {
        for (String table : new String[] {"capitulo", "rubro"}) {
            assertTrue(
                    tableHasTriggerNamed(table, "trg_public_id_immutable"),
                    table + " must declare trigger trg_public_id_immutable");
        }
        assertTrue(
                sharedImmutabilityFunctionExists(),
                "fn_assert_public_id_immutable() from V001 §5 must remain the single shared function");
    }

    @Test
    void no_bespoke_capitulo_or_rubro_public_id_function_was_introduced() throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT proname FROM pg_proc "
                        + "WHERE proname LIKE 'capitulo_%public_id%' "
                        + "OR proname LIKE 'rubro_%public_id%'");
                ResultSet rows = statement.executeQuery()) {
            StringBuilder functions = new StringBuilder();
            while (rows.next()) {
                if (functions.length() > 0) functions.append(", ");
                functions.append(rows.getString(1));
            }
            assertEquals(
                    "",
                    functions.toString(),
                    "V008 must not introduce capitulo_*public_id* or rubro_*public_id* functions");
        }
    }

    @Test
    void public_id_is_populated_with_uuidv7_for_existing_or_inserted_rows() throws Exception {
        // Quarkus test classes may share a Dev Services database. Rows that
        // V001-V007 left in capitulo/rubro normally prove the populated
        // migration path; if another test truncated them, create a minimal
        // parent graph so this assertion remains independent of class order.
        seedFixtureRowsIfNeeded();
        for (String table : new String[] {"capitulo", "rubro"}) {
            long totalRows = queryLong("SELECT count(*) FROM " + table);
            assertTrue(totalRows > 0, table + " must have at least one row after V008 (seed or fixture)");
            long nullPublicIds = queryLong("SELECT count(*) FROM " + table + " WHERE public_id IS NULL");
            assertEquals(0, nullPublicIds, table + " must have no NULL public_id");
            long malformedPublicIds =
                    queryLong("SELECT count(*) FROM " + table + " WHERE public_id::text !~ '" + UUIDV7_REGEX + "'");
            assertEquals(0, malformedPublicIds, table + " must have every public_id matching UUIDv7");
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void seedFixtureRowsIfNeeded() throws Exception {
        if (queryLong("SELECT count(*) FROM capitulo") > 0 && queryLong("SELECT count(*) FROM rubro") > 0) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            // Build a minimal parent graph so the FK chain (rubro → apu →
            // presupuesto → proyecto → usuario) accepts the inserts.
            statement.execute("INSERT INTO usuario (nombre, email, password_hash, rol, email_verificado, activo) "
                    + "VALUES ('V008 Fixture', 'v008-fixture@ex.com', 'placeholder-hash', "
                    + "'USUARIO', TRUE, TRUE)");
            long ownerId = queryLong("SELECT id FROM usuario WHERE email='v008-fixture@ex.com'");
            statement.execute("INSERT INTO proyecto (usuario_id, nombre_proyecto, codigo, descripcion, anio, "
                    + "estado, direccion_institucional) "
                    + "VALUES (" + ownerId + ", 'V008 FIXTURE', 'P-V008-FIX', '', 2026, "
                    + "'BORRADOR', 'GAD Fixture')");
            long proyectoId = queryLong("SELECT id FROM proyecto WHERE codigo='P-V008-FIX'");
            statement.execute("INSERT INTO presupuesto (proyecto_id, version, es_vigente, total) " + "VALUES ("
                    + proyectoId + ", 1, FALSE, 0)");
            long presupuestoId = queryLong("SELECT id FROM presupuesto WHERE proyecto_id=" + proyectoId);
            statement.execute("INSERT INTO apu (presupuesto_id, codigo, descripcion, unidad, "
                    + "porcentaje_descuento, costo_directo, costo_indirecto, costo_total) "
                    + "VALUES (" + presupuestoId + ", 'APU-V008-FIX', 'APU fixture', 'u', 0, 0, 0, 0)");
            long apuId = queryLong("SELECT id FROM apu WHERE presupuesto_id=" + presupuestoId);
            statement.execute("INSERT INTO capitulo (presupuesto_id, item, descripcion, orden) " + "VALUES ("
                    + presupuestoId + ", '1', 'cap fixture', 1)");
            long capituloId = queryLong("SELECT id FROM capitulo WHERE presupuesto_id=" + presupuestoId);
            statement.execute("INSERT INTO rubro (capitulo_id, apu_id, item, codigo, descripcion, "
                    + "unidad, cantidad, precio_unitario, precio_total) "
                    + "VALUES (" + capituloId + ", " + apuId + ", '1', 'R-V008-FIX', "
                    + "'rubro fixture', 'u', 1.0, 0, 0)");
        }
    }

    private boolean tableHasTriggerNamed(String table, String trigger) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM information_schema.triggers "
                        + "WHERE trigger_schema=current_schema() AND event_object_table=? "
                        + "AND trigger_name=?")) {
            statement.setString(1, table);
            statement.setString(2, trigger);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private boolean sharedImmutabilityFunctionExists() throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT 1 FROM pg_proc WHERE proname='fn_assert_public_id_immutable' "
                                + "AND prorettype='trigger'::regtype");
                ResultSet rows = statement.executeQuery()) {
            return rows.next();
        }
    }

    private boolean hasUniqueConstraint(String table, String column) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT 1 FROM information_schema.table_constraints tc "
                                + "JOIN information_schema.key_column_usage kcu "
                                + "ON tc.constraint_name=kcu.constraint_name AND tc.table_schema=kcu.table_schema "
                                + "WHERE tc.table_schema=current_schema() AND tc.table_name=? "
                                + "AND tc.constraint_type='UNIQUE' AND kcu.column_name=?")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private Column column(String table, String name) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT data_type,is_nullable,column_default,is_identity FROM information_schema.columns "
                                + "WHERE table_schema=current_schema() AND table_name=? AND column_name=?")) {
            statement.setString(1, table);
            statement.setString(2, name);
            try (ResultSet row = statement.executeQuery()) {
                return row.next()
                        ? new Column(row.getString(1), row.getString(2), row.getString(3), row.getString(4))
                        : null;
            }
        }
    }

    private long queryLong(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet rows = statement.executeQuery()) {
            assertTrue(rows.next(), sql);
            return rows.getLong(1);
        }
    }

    private record Column(String type, String nullability, String defaultExpression, String identity) {}
}
