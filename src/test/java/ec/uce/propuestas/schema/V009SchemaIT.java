package ec.uce.propuestas.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Plan 027 — focused, order-independent invariants for V009. */
@QuarkusTest
class V009SchemaIT {

    private static final String UUIDV7_REGEX =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-7[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$";

    @Inject
    DataSource dataSource;

    @BeforeEach
    void reset() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE cronograma, actividad, apu_detalle, apu_seccion, apu, "
                    + "rubro, capitulo, presupuesto, insumo, base_insumos, parametros_proyecto, "
                    + "firmante, proyecto, token_usuario, refresh_token, usuario RESTART IDENTITY CASCADE");
        }
    }

    @Test
    void flyway_history_records_successful_v009() throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT version, description, success FROM flyway_schema_history WHERE version='009'");
                ResultSet rows = statement.executeQuery()) {
            assertTrue(rows.next(), "flyway_schema_history must contain version 009");
            assertEquals("009", rows.getString("version"));
            assertNotNull(rows.getString("description"));
            assertTrue(rows.getBoolean("success"));
        }
    }

    @Test
    void cronograma_and_actividad_public_id_is_uuid_not_null_unique_with_uuidv7_default() throws Exception {
        for (String table : new String[] {"cronograma", "actividad"}) {
            Column publicId = column(table, "public_id");
            assertNotNull(publicId, table + " must declare public_id");
            assertEquals("uuid", publicId.type());
            assertEquals("NO", publicId.nullability());
            assertNotNull(publicId.defaultExpression());
            assertTrue(publicId.defaultExpression().contains("uuidv7()"));
            assertTrue(hasUniqueConstraint(table, "public_id"));
        }
    }

    @Test
    void cronograma_and_actividad_have_shared_immutability_trigger() throws Exception {
        for (String table : new String[] {"cronograma", "actividad"}) {
            assertTrue(tableHasTriggerNamed(table, "trg_public_id_immutable"));
        }
        assertTrue(sharedImmutabilityFunctionExists());
    }

    @Test
    void fresh_rows_receive_unique_uuidv7_public_ids() throws Exception {
        Fixture fixture = seedFixture();
        long cronogramaId = insertCronograma(fixture.presupuestoId(), "SEMANA", 12, null);
        long actividadId = insertActividad(cronogramaId, fixture.rubroId());

        UUID cronogramaPublicId = queryUuid("SELECT public_id FROM cronograma WHERE id = ?", cronogramaId);
        UUID actividadPublicId = queryUuid("SELECT public_id FROM actividad WHERE id = ?", actividadId);
        assertTrue(cronogramaPublicId.toString().matches(UUIDV7_REGEX));
        assertTrue(actividadPublicId.toString().matches(UUIDV7_REGEX));
        assertNotEquals(cronogramaPublicId, actividadPublicId);
    }

    @Test
    void direct_public_id_updates_are_rejected_and_original_values_remain() throws Exception {
        Fixture fixture = seedFixture();
        long cronogramaId = insertCronograma(fixture.presupuestoId(), "SEMANA", 12, null);
        long actividadId = insertActividad(cronogramaId, fixture.rubroId());

        assertPublicIdUpdateRejected("cronograma", cronogramaId);
        assertPublicIdUpdateRejected("actividad", actividadId);
    }

    @Test
    void cronograma_fingerprint_column_is_char_64_nullable() throws Exception {
        Column fingerprint = column("cronograma", "presupuesto_fingerprint_revisado");
        assertNotNull(fingerprint);
        assertTrue(fingerprint.type().equals("bpchar") || fingerprint.type().equals("character"));
        assertEquals("YES", fingerprint.nullability());
        assertEquals(
                64,
                queryLong("SELECT character_maximum_length FROM information_schema.columns "
                        + "WHERE table_schema=current_schema() AND table_name='cronograma' "
                        + "AND column_name='presupuesto_fingerprint_revisado'"));
    }

    @Test
    void cronograma_fingerprint_check_accepts_canonical_and_null_and_rejects_invalid_forms() throws Exception {
        String valid = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        long validId = insertCronograma(seedFixture().presupuestoId(), "SEMANA", 12, valid);
        long nullId = insertCronograma(seedFixture().presupuestoId(), "SEMANA", 12, null);
        assertEquals(
                valid,
                queryString("SELECT btrim(presupuesto_fingerprint_revisado) FROM cronograma WHERE id = ?", validId));
        assertEquals(null, queryString("SELECT presupuesto_fingerprint_revisado FROM cronograma WHERE id = ?", nullId));

        assertFingerprintRejected(
                seedFixture().presupuestoId(), "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF");
        assertFingerprintRejected(
                seedFixture().presupuestoId(), "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcde");
        assertFingerprintRejected(seedFixture().presupuestoId(), "g".repeat(64));
    }

    @Test
    void cronograma_period_check_enforces_unit_dependent_boundaries() throws Exception {
        insertCronograma(seedFixture().presupuestoId(), "SEMANA", 1, null);
        insertCronograma(seedFixture().presupuestoId(), "SEMANA", 520, null);
        insertCronograma(seedFixture().presupuestoId(), "MES", 1, null);
        insertCronograma(seedFixture().presupuestoId(), "MES", 120, null);

        assertPeriodRejected(seedFixture().presupuestoId(), "SEMANA", 0);
        assertPeriodRejected(seedFixture().presupuestoId(), "SEMANA", 521);
        assertPeriodRejected(seedFixture().presupuestoId(), "MES", 0);
        assertPeriodRejected(seedFixture().presupuestoId(), "MES", 121);
    }

    @Test
    void duplicate_cronograma_for_same_presupuesto_is_rejected() throws Exception {
        Fixture fixture = seedFixture();
        insertCronograma(fixture.presupuestoId(), "SEMANA", 12, null);
        assertThrows(SQLException.class, () -> insertCronograma(fixture.presupuestoId(), "MES", 6, null));
        assertEquals(1, queryLong("SELECT count(*) FROM cronograma WHERE presupuesto_id = " + fixture.presupuestoId()));
    }

    @Test
    void duplicate_actividad_for_same_rubro_is_rejected() throws Exception {
        Fixture fixture = seedFixture();
        long cronogramaId = insertCronograma(fixture.presupuestoId(), "SEMANA", 12, null);
        insertActividad(cronogramaId, fixture.rubroId());
        assertThrows(SQLException.class, () -> insertActividad(cronogramaId, fixture.rubroId()));
        assertEquals(1, queryLong("SELECT count(*) FROM actividad WHERE rubro_id = " + fixture.rubroId()));
    }

    @Test
    void actividad_rejects_invalid_cronograma_and_rubro_foreign_keys() throws Exception {
        Fixture invalidCronogramaFixture = seedFixture();
        assertThrows(SQLException.class, () -> insertActividad(Long.MAX_VALUE, invalidCronogramaFixture.rubroId()));
        assertEquals(0, queryLong("SELECT count(*) FROM actividad"));

        Fixture invalidRubroFixture = seedFixture();
        long cronogramaId = insertCronograma(invalidRubroFixture.presupuestoId(), "SEMANA", 12, null);
        assertThrows(SQLException.class, () -> insertActividad(cronogramaId, Long.MAX_VALUE));
        assertEquals(0, queryLong("SELECT count(*) FROM actividad"));
    }

    @Test
    void cronograma_and_actividad_retain_bigint_identity_and_one_to_one_uniqueness() throws Exception {
        for (String table : new String[] {"cronograma", "actividad"}) {
            Column id = column(table, "id");
            assertNotNull(id);
            assertEquals("bigint", id.type());
            assertEquals("YES", id.identity());
        }
        assertTrue(hasUniqueConstraint("cronograma", "presupuesto_id"));
        assertTrue(hasUniqueConstraint("actividad", "rubro_id"));
    }

    @Test
    void cronograma_actividad_inert_seam_remains_intact() throws Exception {
        Column id = column("cronograma_actividad", "id");
        assertNotNull(id);
        assertEquals("bigint", id.type());
        assertEquals("YES", id.identity());
        assertTrue(hasUniqueConstraint("cronograma_actividad", "presupuesto_id"));
    }

    @Test
    void v009_applies_over_populated_v008_state_assigning_distinct_uuidv7_public_ids() throws Exception {
        // Plan 027 — migration-compatibility replay evidence. Self-contained:
        // creates a unique temporary schema, migrates it only through V008
        // via Flyway, populates cronograma/actividad rows, migrates the same
        // schema through V009, asserts the migration transformed existing
        // rows (distinct UUIDv7 public_ids, NULL fingerprint, unchanged
        // PK/FKs/map) and left the inert `cronograma_actividad` seam
        // untouched, then drops the schema in finally. Order-independent:
        // uses the Quarkus-managed DataSource but never mutates `public`.
        String tempSchema = "v009_replay_" + Long.toHexString(System.nanoTime());
        try {
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA \"" + tempSchema + "\"");
            }

            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(tempSchema)
                    .defaultSchema(tempSchema)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("8"))
                    .load()
                    .migrate();

            long ourCronogramaId;
            long ourActividadId;
            long ourCronogramaFkPresupuestoId;
            long ourActividadFkCronogramaId;
            long ourActividadFkRubroId;
            long seededCronogramaActividadCount;
            String ourActividadAvanceMap;
            try (Connection connection = dataSource.getConnection()) {
                connection.setSchema(tempSchema);
                try {
                    seededCronogramaActividadCount =
                            singleLongOnConnection(connection, "SELECT count(*) FROM cronograma_actividad");
                    String suffix = UUID.randomUUID().toString().substring(0, 8);
                    long ownerId = insertReturningId(
                            connection,
                            "INSERT INTO usuario (nombre, email, password_hash, rol, email_verificado, activo) "
                                    + "VALUES ('V009 Replay', ?, 'hash', 'USUARIO', TRUE, TRUE) RETURNING id",
                            "v009-replay-" + suffix + "@ex.com");
                    long proyectoId = insertReturningId(
                            connection,
                            "INSERT INTO proyecto (usuario_id, nombre_proyecto, codigo, descripcion, anio, estado, "
                                    + "direccion_institucional) VALUES (?, 'V009 REPLAY', ?, '', 2026, "
                                    + "'BORRADOR', 'GAD Replay') RETURNING id",
                            ownerId,
                            "P-V009-RPL-" + suffix);
                    long presupuestoId = insertReturningId(
                            connection,
                            "INSERT INTO presupuesto (proyecto_id, version, es_vigente, total) "
                                    + "VALUES (?, 1, FALSE, 0) RETURNING id",
                            proyectoId);
                    long apuId = insertReturningId(
                            connection,
                            "INSERT INTO apu (presupuesto_id, codigo, descripcion, unidad, costo_directo, "
                                    + "costo_indirecto, costo_total) "
                                    + "VALUES (?, ?, 'V009 APU Replay', 'u', 0, 0, 0) RETURNING id",
                            presupuestoId,
                            "APU-RPL-" + suffix);
                    long capituloId = insertReturningId(
                            connection,
                            "INSERT INTO capitulo (presupuesto_id, item, descripcion, orden, total) "
                                    + "VALUES (?, ?, 'V009 Cap Replay', 1, 0) RETURNING id",
                            presupuestoId,
                            "CAP-RPL-" + suffix);
                    long rubroId = insertReturningId(
                            connection,
                            "INSERT INTO rubro (capitulo_id, apu_id, item, codigo, descripcion, unidad, "
                                    + "cantidad, precio_unitario, precio_total) "
                                    + "VALUES (?, ?, ?, ?, 'V009 Rubro Replay', 'u', 1, 0, 0) RETURNING id",
                            capituloId,
                            apuId,
                            "1",
                            "R-RPL-" + suffix);
                    ourCronogramaFkPresupuestoId = presupuestoId;
                    ourCronogramaId = insertReturningId(
                            connection,
                            "INSERT INTO cronograma (presupuesto_id, unidad_tiempo, numero_periodos, "
                                    + "total_general_revisado, fecha_revision) "
                                    + "VALUES (?, 'SEMANA', 12, NULL, NULL) RETURNING id",
                            presupuestoId);
                    ourActividadFkCronogramaId = ourCronogramaId;
                    ourActividadFkRubroId = rubroId;
                    // Non-consecutive map (no key 2) to detect silent
                    // reordering by JSONB casting on the read path.
                    ourActividadAvanceMap = "{\"1\":\"0.5000\",\"3\":\"0.5000\"}";
                    ourActividadId = insertReturningId(
                            connection,
                            "INSERT INTO actividad (cronograma_id, rubro_id, peso_ponderado, "
                                    + "avance_por_periodo) "
                                    + "VALUES (?, ?, 0.5000, ?::jsonb) RETURNING id",
                            ourCronogramaId,
                            rubroId,
                            ourActividadAvanceMap);
                } finally {
                    resetSchemaQuietly(connection);
                }
            }

            Flyway.configure()
                    .dataSource(dataSource)
                    .schemas(tempSchema)
                    .defaultSchema(tempSchema)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            try (Connection connection = dataSource.getConnection()) {
                connection.setSchema(tempSchema);
                try {
                    assertV009ReplayedOverPopulatedV008(
                            connection,
                            ourCronogramaId,
                            ourCronogramaFkPresupuestoId,
                            ourActividadId,
                            ourActividadFkCronogramaId,
                            ourActividadFkRubroId,
                            ourActividadAvanceMap,
                            seededCronogramaActividadCount);
                } finally {
                    resetSchemaQuietly(connection);
                }
            }
        } finally {
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA \"" + tempSchema + "\" CASCADE");
            }
        }
    }

    private void assertV009ReplayedOverPopulatedV008(
            Connection connection,
            long ourCronogramaId,
            long ourCronogramaFkPresupuestoId,
            long ourActividadId,
            long ourActividadFkCronogramaId,
            long ourActividadFkRubroId,
            String ourActividadAvanceMap,
            long seededCronogramaActividadCount)
            throws Exception {
        assertEquals(
                1,
                singleLongOnConnection(
                        connection, "SELECT count(*) FROM flyway_schema_history WHERE version='009' AND success"),
                "V009 must be recorded as successful in the temp schema's flyway_schema_history");

        long cronogramaRows = singleLongOnConnection(connection, "SELECT count(*) FROM cronograma");
        long cronogramaWithPublicId =
                singleLongOnConnection(connection, "SELECT count(*) FROM cronograma WHERE public_id IS NOT NULL");
        assertEquals(
                cronogramaRows,
                cronogramaWithPublicId,
                "every cronograma row must have a public_id after V009 "
                        + "(existing rows filled by ALTER TABLE DEFAULT uuidv7())");

        long actividadRows = singleLongOnConnection(connection, "SELECT count(*) FROM actividad");
        long actividadWithPublicId =
                singleLongOnConnection(connection, "SELECT count(*) FROM actividad WHERE public_id IS NOT NULL");
        assertEquals(
                actividadRows,
                actividadWithPublicId,
                "every actividad row must have a public_id after V009 "
                        + "(existing rows filled by ALTER TABLE DEFAULT uuidv7())");

        assertEquals(
                cronogramaRows,
                singleLongOnConnection(connection, "SELECT count(DISTINCT public_id) FROM cronograma"),
                "cronograma public_ids must be pairwise distinct after V009");
        assertEquals(
                actividadRows,
                singleLongOnConnection(connection, "SELECT count(DISTINCT public_id) FROM actividad"),
                "actividad public_ids must be pairwise distinct after V009");

        assertEquals(
                0,
                singleLongOnConnection(
                        connection, "SELECT count(*) FROM cronograma WHERE public_id::text !~ '" + UUIDV7_REGEX + "'"),
                "every cronograma public_id must match the UUIDv7 regex");
        assertEquals(
                0,
                singleLongOnConnection(
                        connection, "SELECT count(*) FROM actividad WHERE public_id::text !~ '" + UUIDV7_REGEX + "'"),
                "every actividad public_id must match the UUIDv7 regex");

        assertEquals(
                0,
                singleLongOnConnection(
                        connection,
                        "SELECT count(*) FROM cronograma WHERE presupuesto_fingerprint_revisado IS NOT NULL"),
                "every existing cronograma row must keep a NULL fingerprint after V009 (no backfill)");

        assertEquals(
                1,
                singleLongOnConnection(connection, "SELECT count(*) FROM cronograma WHERE id = " + ourCronogramaId),
                "our pre-V009 cronograma row must still exist by its original PK");
        assertEquals(
                ourCronogramaFkPresupuestoId,
                singleLongOnConnection(
                        connection, "SELECT presupuesto_id FROM cronograma WHERE id = " + ourCronogramaId),
                "our cronograma FK presupuesto_id must be unchanged");
        assertEquals(
                1,
                singleLongOnConnection(connection, "SELECT count(*) FROM actividad WHERE id = " + ourActividadId),
                "our pre-V009 actividad row must still exist by its original PK");
        assertEquals(
                ourActividadFkCronogramaId,
                singleLongOnConnection(connection, "SELECT cronograma_id FROM actividad WHERE id = " + ourActividadId),
                "our actividad FK cronograma_id must be unchanged");
        assertEquals(
                ourActividadFkRubroId,
                singleLongOnConnection(connection, "SELECT rubro_id FROM actividad WHERE id = " + ourActividadId),
                "our actividad FK rubro_id must be unchanged");

        assertJsonbEquals(
                connection,
                ourActividadId,
                ourActividadAvanceMap,
                "our actividad avance_por_periodo JSONB must remain semantically identical (no key reordering)");

        assertEquals(
                seededCronogramaActividadCount,
                singleLongOnConnection(connection, "SELECT count(*) FROM cronograma_actividad"),
                "cronograma_actividad row count must remain unchanged after V009");
        assertEquals(
                0,
                singleLongOnConnection(
                        connection,
                        "SELECT count(*) FROM information_schema.columns "
                                + "WHERE table_schema=current_schema() AND table_name='cronograma_actividad' "
                                + "AND column_name='public_id'"),
                "cronograma_actividad must not gain a public_id column under V009");
        assertEquals(
                1,
                singleLongOnConnection(
                        connection,
                        "SELECT count(*) FROM information_schema.table_constraints tc "
                                + "JOIN information_schema.key_column_usage kcu "
                                + "ON tc.constraint_name=kcu.constraint_name "
                                + "AND tc.table_schema=kcu.table_schema "
                                + "WHERE tc.table_schema=current_schema() AND tc.table_name='cronograma_actividad' "
                                + "AND tc.constraint_type='UNIQUE' AND kcu.column_name='presupuesto_id'"),
                "cronograma_actividad UNIQUE(presupuesto_id) must remain under V009");
    }

    private long singleLongOnConnection(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next(), sql);
            long value = rows.getLong(1);
            assertFalse(rows.next(), "expected a single row for: " + sql);
            return value;
        }
    }

    private void assertJsonbEquals(Connection connection, long actividadId, String expectedJson, String message)
            throws Exception {
        // PostgreSQL's JSONB text output normalises whitespace around colons,
        // so a byte-identical text compare is the wrong tool. Compare via the
        // JSONB equality operator instead: this verifies semantic identity of
        // keys and values (in particular, the non-consecutive order of keys 1
        // and 3) without depending on text rendering.
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT (avance_por_periodo = ?::jsonb) FROM actividad WHERE id = ?")) {
            statement.setString(1, expectedJson);
            statement.setLong(2, actividadId);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next(), "expected one row for actividad id " + actividadId);
                assertTrue(rows.getBoolean(1), message);
                assertFalse(rows.next(), "expected a single row for actividad id " + actividadId);
            }
        }
    }

    private void resetSchemaQuietly(Connection connection) {
        try {
            connection.setSchema("public");
        } catch (Exception ignore) {
            // best effort reset before returning the connection to the pool;
            // if it fails the next reader will setSchema explicitly anyway.
        }
    }

    private void assertPublicIdUpdateRejected(String table, long id) throws Exception {
        UUID original = queryUuid("SELECT public_id FROM " + table + " WHERE id = ?", id);
        UUID replacement = UUID.fromString("0192f6c4-7c8a-7abc-8def-feedfacedead");
        assertNotEquals(original, replacement);
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("UPDATE " + table + " SET public_id = ? WHERE id = ?")) {
            statement.setObject(1, replacement);
            statement.setLong(2, id);
            assertThrows(SQLException.class, statement::executeUpdate, table + " direct public_id update must fail");
        }
        assertEquals(original, queryUuid("SELECT public_id FROM " + table + " WHERE id = ?", id));
    }

    private void assertPeriodRejected(long presupuestoId, String unidad, int numeroPeriodos) {
        assertThrows(SQLException.class, () -> insertCronograma(presupuestoId, unidad, numeroPeriodos, null));
    }

    private void assertFingerprintRejected(long presupuestoId, String fingerprint) {
        assertThrows(SQLException.class, () -> insertCronograma(presupuestoId, "SEMANA", 12, fingerprint));
    }

    private long insertCronograma(long presupuestoId, String unidad, int numeroPeriodos, String fingerprint)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO cronograma (presupuesto_id, unidad_tiempo, numero_periodos, "
                                + "presupuesto_fingerprint_revisado) VALUES (?, ?, ?, ?::char(64)) RETURNING id")) {
            statement.setLong(1, presupuestoId);
            statement.setString(2, unidad);
            statement.setInt(3, numeroPeriodos);
            if (fingerprint == null) {
                statement.setNull(4, Types.CHAR);
            } else {
                statement.setString(4, fingerprint);
            }
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private long insertActividad(long cronogramaId, long rubroId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO actividad (cronograma_id, rubro_id, peso_ponderado, avance_por_periodo) "
                                + "VALUES (?, ?, 0.5000, '{\"1\":\"0.5000\"}'::jsonb) RETURNING id")) {
            statement.setLong(1, cronogramaId);
            statement.setLong(2, rubroId);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private Fixture seedFixture() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        try (Connection connection = dataSource.getConnection()) {
            long ownerId = insertReturningId(
                    connection,
                    "INSERT INTO usuario (nombre, email, password_hash, rol, email_verificado, activo) "
                            + "VALUES ('V009 Fixture', ?, 'placeholder-hash', 'USUARIO', TRUE, TRUE) RETURNING id",
                    "v009-" + suffix + "@ex.com");
            long proyectoId = insertReturningId(
                    connection,
                    "INSERT INTO proyecto (usuario_id, nombre_proyecto, codigo, descripcion, anio, estado, "
                            + "direccion_institucional) VALUES (?, 'V009 FIXTURE', ?, '', 2026, 'BORRADOR', "
                            + "'GAD Fixture') RETURNING id",
                    ownerId,
                    "P-V009-" + suffix);
            long presupuestoId = insertReturningId(
                    connection,
                    "INSERT INTO presupuesto (proyecto_id, version, es_vigente, total) "
                            + "VALUES (?, 1, FALSE, 0) RETURNING id",
                    proyectoId);
            long apuId = insertReturningId(
                    connection,
                    "INSERT INTO apu (presupuesto_id, codigo, descripcion, unidad, costo_directo, costo_indirecto, "
                            + "costo_total) VALUES (?, ?, 'V009 APU', 'u', 0, 0, 0) RETURNING id",
                    presupuestoId,
                    "APU-" + suffix);
            long capituloId = insertReturningId(
                    connection,
                    "INSERT INTO capitulo (presupuesto_id, item, descripcion, orden, total) "
                            + "VALUES (?, ?, 'V009 Capitulo', 1, 0) RETURNING id",
                    presupuestoId,
                    "CAP-" + suffix);
            long rubroId = insertReturningId(
                    connection,
                    "INSERT INTO rubro (capitulo_id, apu_id, item, codigo, descripcion, unidad, cantidad, "
                            + "precio_unitario, precio_total) VALUES (?, ?, ?, ?, 'V009 Rubro', 'u', 1, 0, 0) "
                            + "RETURNING id",
                    capituloId,
                    apuId,
                    "R-" + suffix,
                    "R-" + suffix);
            return new Fixture(presupuestoId, rubroId);
        }
    }

    private long insertReturningId(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private UUID queryUuid(String sql, long id) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getObject(1, UUID.class);
            }
        }
    }

    private String queryString(String sql, long id) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getString(1);
            }
        }
    }

    private boolean tableHasTriggerNamed(String table, String trigger) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM information_schema.triggers "
                        + "WHERE trigger_schema=current_schema() AND event_object_table=? AND trigger_name=?")) {
            statement.setString(1, table);
            statement.setString(2, trigger);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private boolean sharedImmutabilityFunctionExists() throws Exception {
        return queryLong("SELECT count(*) FROM pg_proc WHERE proname='fn_assert_public_id_immutable' "
                        + "AND prorettype='trigger'::regtype")
                > 0;
    }

    private boolean hasUniqueConstraint(String table, String column) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT 1 FROM information_schema.table_constraints tc "
                                + "JOIN information_schema.key_column_usage kcu ON tc.constraint_name=kcu.constraint_name "
                                + "AND tc.table_schema=kcu.table_schema WHERE tc.table_schema=current_schema() "
                                + "AND tc.table_name=? AND tc.constraint_type='UNIQUE' AND kcu.column_name=?")) {
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

    private record Fixture(long presupuestoId, long rubroId) {}

    private record Column(String type, String nullability, String defaultExpression, String identity) {}
}
