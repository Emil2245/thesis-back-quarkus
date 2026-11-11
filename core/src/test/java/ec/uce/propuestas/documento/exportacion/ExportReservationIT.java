package ec.uce.propuestas.documento.exportacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ec.uce.propuestas.common.ProblemaException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class ExportReservationIT {
    private static final String BODY_HASH = "a".repeat(64);
    private static final String REVISION_HASH = "b".repeat(64);
    private static final String OBJECT_SHA256 = "A".repeat(43) + "=";
    private static final UUID KEY = UUID.fromString("01900000-0000-7000-8000-000000000011");

    @Inject
    DataSource dataSource;

    @Inject
    ExportReservationService reservations;

    private long userId;
    private long projectId;
    private long budgetId;
    private UUID budgetPublicId;

    @BeforeEach
    void seed() throws SQLException {
        try (Connection db = dataSource.getConnection()) {
            userId = insertId(
                    db,
                    "INSERT INTO usuario(nombre,email,password_hash) VALUES ('Isolated export','stage03-"
                            + UUID.randomUUID() + "@example.invalid','synthetic-hash') RETURNING id");
            projectId = insertId(
                    db,
                    "INSERT INTO proyecto(usuario_id,nombre_proyecto,anio,direccion_institucional)" + " VALUES ("
                            + userId + ",'Isolated export',2026,'Test only') RETURNING id");
            try (PreparedStatement insert = db.prepareStatement(
                    "INSERT INTO presupuesto(proyecto_id,version) VALUES (?,1) RETURNING id, public_id")) {
                insert.setLong(1, projectId);
                try (ResultSet rows = insert.executeQuery()) {
                    rows.next();
                    budgetId = rows.getLong(1);
                    budgetPublicId = (UUID) rows.getObject(2);
                }
            }
        }
    }

    @AfterEach
    void cleanup() throws SQLException {
        try (Connection db = dataSource.getConnection()) {
            execute(
                    db,
                    "DELETE FROM log_actividad WHERE usuario_id = " + userId
                            + " AND evento = 'documento.exportado' AND entidad_public_id = '" + budgetPublicId + "'");
            execute(db, "DELETE FROM documento_exportacion WHERE usuario_id = " + userId);
            execute(db, "DELETE FROM presupuesto WHERE id = " + budgetId);
            execute(db, "DELETE FROM proyecto WHERE id = " + projectId);
            execute(db, "DELETE FROM usuario WHERE id = " + userId);
        }
    }

    @Test
    void reservesOnceAndRejectsConcurrentOrDifferentRequestWithoutAnotherRow() throws SQLException {
        var reserved = reservations.claim(userId, KEY, budgetPublicId, "presupuesto", BODY_HASH, REVISION_HASH);
        assertEquals("RESERVADA", reserved.state());
        assertEquals(7, reserved.operationId().version());
        assertEquals("exports/" + reserved.operationId(), reserved.objectKey());
        assertEquals(1L, reserved.fence());

        var same = assertThrows(
                ProblemaException.class,
                () -> reservations.claim(userId, KEY, budgetPublicId, "presupuesto", BODY_HASH, REVISION_HASH));
        assertEquals(409, same.getResponse().getStatus());
        assertEquals(
                "exportacion-en-curso",
                ((ec.uce.propuestas.common.ErrorPayload) same.getResponse().getEntity()).codigo());
        var changed = assertThrows(
                ProblemaException.class,
                () -> reservations.claim(userId, KEY, budgetPublicId, "presupuesto", "c".repeat(64), REVISION_HASH));
        assertEquals(409, changed.getResponse().getStatus());
        assertEquals(1, countReservations());
    }

    @Test
    void confirmedMetadataIsAvailableForIdempotentTicketReissueOnlyToOwner() {
        var operation = reservations.claim(userId, KEY, budgetPublicId, "presupuesto", BODY_HASH, REVISION_HASH);
        assertEquals(
                409,
                assertThrows(
                                ProblemaException.class,
                                () -> reservations.confirmedMetadata(userId, operation.operationId()))
                        .getResponse()
                        .getStatus());
        reservations.confirmUpload(
                userId,
                operation.operationId(),
                operation.fence(),
                operation.objectKey(),
                OBJECT_SHA256,
                123,
                "application/pdf",
                "budget.pdf");
        var metadata = reservations.confirmedMetadata(userId, operation.operationId());
        assertEquals(OBJECT_SHA256, metadata.sha256());
        assertEquals(123, metadata.sizeBytes());
        assertEquals("application/pdf", metadata.mediaType());
        assertEquals("budget.pdf", metadata.filename());
        assertEquals(
                404,
                assertThrows(
                                ProblemaException.class,
                                () -> reservations.confirmedMetadata(userId + 1, operation.operationId()))
                        .getResponse()
                        .getStatus());
    }

    @Test
    void specificationMetadataRequiresAuditedState() {
        var operation =
                reservations.claim(userId, KEY, budgetPublicId, "especificaciones-tecnicas", BODY_HASH, REVISION_HASH);
        reservations.confirmUpload(
                userId,
                operation.operationId(),
                operation.fence(),
                operation.objectKey(),
                OBJECT_SHA256,
                123,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "specification.docx");
        assertEquals(
                409,
                assertThrows(
                                ProblemaException.class,
                                () -> reservations.confirmedMetadata(userId, operation.operationId()))
                        .getResponse()
                        .getStatus());
        reservations.finalizeAudit(userId, operation.operationId(), operation.fence());
        assertEquals(
                "specification.docx",
                reservations.confirmedMetadata(userId, operation.operationId()).filename());
    }

    @Test
    void expiredLeaseReclaimsTheSameObjectWithANewFence() throws SQLException {
        var first = reservations.claim(userId, KEY, budgetPublicId, "presupuesto", BODY_HASH, REVISION_HASH);
        try (Connection db = dataSource.getConnection();
                PreparedStatement expire =
                        db.prepareStatement("UPDATE documento_exportacion SET lease_until = now() - INTERVAL '1 second'"
                                + " WHERE usuario_id = ? AND idempotency_key = ?")) {
            expire.setLong(1, userId);
            expire.setObject(2, KEY);
            assertEquals(1, expire.executeUpdate());
        }
        var reclaimed = reservations.claim(userId, KEY, budgetPublicId, "presupuesto", BODY_HASH, REVISION_HASH);
        assertEquals(first.operationId(), reclaimed.operationId());
        assertEquals(first.objectKey(), reclaimed.objectKey());
        assertEquals(first.fence() + 1, reclaimed.fence());
        assertEquals(1, countReservations());
    }

    @Test
    void changedRevisionCannotReclaimAnExpiredKey() throws SQLException {
        reservations.claim(userId, KEY, budgetPublicId, "presupuesto", BODY_HASH, REVISION_HASH);
        try (Connection db = dataSource.getConnection();
                PreparedStatement expire =
                        db.prepareStatement("UPDATE documento_exportacion SET lease_until = now() - INTERVAL '1 second'"
                                + " WHERE usuario_id = ? AND idempotency_key = ?")) {
            expire.setLong(1, userId);
            expire.setObject(2, KEY);
            expire.executeUpdate();
        }
        var different = assertThrows(
                ProblemaException.class,
                () -> reservations.claim(userId, KEY, budgetPublicId, "presupuesto", BODY_HASH, "c".repeat(64)));
        assertEquals(409, different.getResponse().getStatus());
        assertEquals(1, countReservations());
    }

    @Test
    void onlyCurrentFenceCanConfirmAnUploadAfterLeaseReclamation() throws SQLException {
        var original = reservations.claim(userId, KEY, budgetPublicId, "presupuesto", BODY_HASH, REVISION_HASH);
        try (Connection db = dataSource.getConnection();
                PreparedStatement expire =
                        db.prepareStatement("UPDATE documento_exportacion SET lease_until = now() - INTERVAL '1 second'"
                                + " WHERE usuario_id = ? AND idempotency_key = ?")) {
            expire.setLong(1, userId);
            expire.setObject(2, KEY);
            expire.executeUpdate();
        }
        var successor = reservations.claim(userId, KEY, budgetPublicId, "presupuesto", BODY_HASH, REVISION_HASH);
        var stale = assertThrows(
                ProblemaException.class,
                () -> reservations.confirmUpload(
                        userId,
                        original.operationId(),
                        original.fence(),
                        original.objectKey(),
                        OBJECT_SHA256,
                        123,
                        "application/pdf",
                        "budget.pdf"));
        assertEquals(409, stale.getResponse().getStatus());
        assertEquals("RESERVADA", storedState());
        var confirmed = reservations.confirmUpload(
                userId,
                successor.operationId(),
                successor.fence(),
                successor.objectKey(),
                OBJECT_SHA256,
                123,
                "application/pdf",
                "budget.pdf");
        assertEquals("SUBIDA", confirmed.state());
        var retried = reservations.claim(userId, KEY, budgetPublicId, "presupuesto", BODY_HASH, REVISION_HASH);
        assertEquals("SUBIDA", retried.state());
        assertEquals(original.operationId(), retried.operationId());
        assertEquals(1, countReservations());
    }

    @Test
    void rejectedUploadMetadataNeverChangesReservationState() throws SQLException {
        var reserved = reservations.claim(userId, KEY, budgetPublicId, "presupuesto", BODY_HASH, REVISION_HASH);
        var invalid = assertThrows(
                ProblemaException.class,
                () -> reservations.confirmUpload(
                        userId,
                        reserved.operationId(),
                        reserved.fence(),
                        "exports/01900000-0000-7000-8000-000000000009",
                        OBJECT_SHA256,
                        123,
                        "application/pdf",
                        "budget.pdf"));
        assertEquals(409, invalid.getResponse().getStatus());
        assertEquals("RESERVADA", storedState());
        assertEquals(1, countReservations());
    }

    @Test
    void specificationAuditAndMarkerCommitOnceOnlyAfterConfirmedUpload() throws SQLException {
        var reserved =
                reservations.claim(userId, KEY, budgetPublicId, "especificaciones-tecnicas", BODY_HASH, REVISION_HASH);
        assertEquals(
                409,
                assertThrows(
                                ProblemaException.class,
                                () -> reservations.finalizeAudit(userId, reserved.operationId(), reserved.fence()))
                        .getResponse()
                        .getStatus());
        assertEquals("RESERVADA", storedState());
        assertEquals(0, countAuditEvents());
        reservations.confirmUpload(
                userId,
                reserved.operationId(),
                reserved.fence(),
                reserved.objectKey(),
                OBJECT_SHA256,
                123,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "technical.docx");
        assertEquals(
                "AUDITADA",
                reservations
                        .finalizeAudit(userId, reserved.operationId(), reserved.fence())
                        .state());
        assertEquals(
                "AUDITADA",
                reservations
                        .finalizeAudit(userId, reserved.operationId(), reserved.fence())
                        .state());
        assertEquals("AUDITADA", storedState());
        assertEquals(1, countAuditEvents());
    }

    @Test
    void obsoleteFenceCannotAuditOrChangeConfirmedResult() throws SQLException {
        var first =
                reservations.claim(userId, KEY, budgetPublicId, "especificaciones-tecnicas", BODY_HASH, REVISION_HASH);
        try (Connection db = dataSource.getConnection();
                PreparedStatement expire = db.prepareStatement("UPDATE documento_exportacion"
                        + " SET lease_until = now() - INTERVAL '1 second' WHERE operation_id = ?")) {
            expire.setObject(1, first.operationId());
            assertEquals(1, expire.executeUpdate());
        }
        var second =
                reservations.claim(userId, KEY, budgetPublicId, "especificaciones-tecnicas", BODY_HASH, REVISION_HASH);
        assertEquals(
                409,
                assertThrows(
                                ProblemaException.class,
                                () -> reservations.finalizeAudit(userId, first.operationId(), first.fence()))
                        .getResponse()
                        .getStatus());
        assertEquals("RESERVADA", storedState());
        assertEquals(0, countAuditEvents());
        reservations.confirmUpload(
                userId,
                second.operationId(),
                second.fence(),
                second.objectKey(),
                OBJECT_SHA256,
                123,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "technical.docx");
        assertEquals(
                "AUDITADA",
                reservations
                        .finalizeAudit(userId, second.operationId(), second.fence())
                        .state());
        assertEquals(1, countAuditEvents());
    }

    @Test
    void budgetExportHasNoNewAuditEvent() throws SQLException {
        var reserved = reservations.claim(userId, KEY, budgetPublicId, "presupuesto", BODY_HASH, REVISION_HASH);
        reservations.confirmUpload(
                userId,
                reserved.operationId(),
                reserved.fence(),
                reserved.objectKey(),
                OBJECT_SHA256,
                123,
                "application/pdf",
                "budget.pdf");
        assertEquals(
                "SUBIDA",
                reservations
                        .finalizeAudit(userId, reserved.operationId(), reserved.fence())
                        .state());
        assertEquals("SUBIDA", storedState());
        assertEquals(0, countAuditEvents());
    }

    @Test
    void cronogramaXmlIsAuditedOnceOnlyAfterConfirmation() throws SQLException {
        var operation = reservations.claim(userId, KEY, budgetPublicId, "cronograma", BODY_HASH, REVISION_HASH);
        assertThrows(
                ProblemaException.class,
                () -> reservations.finalizeCronogramaAudit(
                        userId, operation.operationId(), operation.fence(), "MSPDI", true));
        assertEquals(0, countAuditEvents());
        reservations.confirmUpload(
                userId,
                operation.operationId(),
                operation.fence(),
                operation.objectKey(),
                OBJECT_SHA256,
                123,
                "application/xml",
                "schedule.xml");
        assertEquals(
                "AUDITADA",
                reservations
                        .finalizeCronogramaAudit(userId, operation.operationId(), operation.fence(), "MSPDI", true)
                        .state());
        assertEquals(
                "AUDITADA",
                reservations
                        .finalizeCronogramaAudit(userId, operation.operationId(), operation.fence(), "MSPDI", true)
                        .state());
        assertEquals(1, countAuditEvents());
        assertEquals(
                "schedule.xml",
                reservations.confirmedMetadata(userId, operation.operationId()).filename());
    }

    @Test
    void xmlCannotConfirmBudget() throws SQLException {
        var operation = reservations.claim(userId, KEY, budgetPublicId, "presupuesto", BODY_HASH, REVISION_HASH);
        assertThrows(
                ProblemaException.class,
                () -> reservations.confirmUpload(
                        userId,
                        operation.operationId(),
                        operation.fence(),
                        operation.objectKey(),
                        OBJECT_SHA256,
                        123,
                        "application/xml",
                        "schedule.xml"));
        assertEquals("RESERVADA", storedState());
    }

    private int countAuditEvents() throws SQLException {
        try (Connection db = dataSource.getConnection();
                PreparedStatement query = db.prepareStatement("SELECT count(*) FROM log_actividad"
                        + " WHERE usuario_id = ? AND evento = 'documento.exportado' AND entidad_public_id = ?")) {
            query.setLong(1, userId);
            query.setObject(2, budgetPublicId);
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private String storedState() throws SQLException {
        try (Connection db = dataSource.getConnection();
                PreparedStatement query = db.prepareStatement(
                        "SELECT estado FROM documento_exportacion WHERE usuario_id = ? AND idempotency_key = ?")) {
            query.setLong(1, userId);
            query.setObject(2, KEY);
            try (ResultSet rows = query.executeQuery()) {
                assertTrue(rows.next());
                return rows.getString(1);
            }
        }
    }

    @Test
    void rejectsForeignBudgetWithoutPersistingReservation() throws SQLException {
        var rejected = assertThrows(
                ProblemaException.class,
                () -> reservations.claim(userId + 1, KEY, budgetPublicId, "presupuesto", BODY_HASH, REVISION_HASH));
        assertEquals(404, rejected.getResponse().getStatus());
        assertEquals(0, countReservations());
    }

    @Test
    void eachUserHasAnIndependentKeyNamespace() throws SQLException {
        long other = -1;
        try (Connection db = dataSource.getConnection()) {
            other = insertId(
                    db,
                    "INSERT INTO usuario(nombre,email,password_hash) VALUES ('Second export','stage03-"
                            + UUID.randomUUID() + "@example.invalid','synthetic-hash') RETURNING id");
            long otherProject = insertId(
                    db,
                    "INSERT INTO proyecto(usuario_id,nombre_proyecto,anio,direccion_institucional)" + " VALUES ("
                            + other + ",'Second export',2026,'Test only') RETURNING id");
            try {
                UUID otherBudget = insertUuid(
                        db,
                        "INSERT INTO presupuesto(proyecto_id,version) VALUES (" + otherProject
                                + ",1) RETURNING public_id");
                var first = reservations.claim(userId, KEY, budgetPublicId, "presupuesto", BODY_HASH, REVISION_HASH);
                var second = reservations.claim(other, KEY, otherBudget, "presupuesto", BODY_HASH, REVISION_HASH);
                assertNotEquals(first.operationId(), second.operationId());
                assertEquals(1, countReservations());
                assertEquals(1, countReservations(other));
            } finally {
                execute(db, "DELETE FROM documento_exportacion WHERE usuario_id = " + other);
                execute(db, "DELETE FROM presupuesto WHERE proyecto_id = " + otherProject);
                execute(db, "DELETE FROM proyecto WHERE id = " + otherProject);
                execute(db, "DELETE FROM usuario WHERE id = " + other);
            }
        }
    }

    private int countReservations() throws SQLException {
        return countReservations(userId);
    }

    private int countReservations(long ownerId) throws SQLException {
        try (Connection db = dataSource.getConnection();
                PreparedStatement query =
                        db.prepareStatement("SELECT count(*) FROM documento_exportacion WHERE usuario_id = ?")) {
            query.setLong(1, ownerId);
            try (ResultSet rows = query.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private static long insertId(Connection db, String sql) throws SQLException {
        try (PreparedStatement query = db.prepareStatement(sql);
                ResultSet rows = query.executeQuery()) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static UUID insertUuid(Connection db, String sql) throws SQLException {
        try (PreparedStatement query = db.prepareStatement(sql);
                ResultSet rows = query.executeQuery()) {
            rows.next();
            return (UUID) rows.getObject(1);
        }
    }

    private static void execute(Connection db, String sql) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            statement.executeUpdate();
        }
    }
}
