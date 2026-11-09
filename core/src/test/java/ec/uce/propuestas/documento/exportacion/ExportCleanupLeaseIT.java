package ec.uce.propuestas.documento.exportacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
class ExportCleanupLeaseIT {
    private static final UUID KEY = UUID.fromString("01900000-0000-7000-8000-000000000014");

    @Inject
    DataSource dataSource;

    @Inject
    ExportReservationService reservations;

    @Inject
    ExportCleanupLeaseService cleanup;

    private long userId;
    private long projectId;
    private long budgetId;
    private UUID budgetPublicId;
    private ExportReservationService.Reservation uploaded;

    @BeforeEach
    void seed() throws SQLException {
        try (Connection db = dataSource.getConnection()) {
            try (PreparedStatement insert = db.prepareStatement("INSERT INTO usuario(nombre,email,password_hash)"
                    + " VALUES ('Cleanup test',?,'synthetic-hash') RETURNING id")) {
                insert.setString(1, "stage03-cleanup-" + UUID.randomUUID() + "@example.invalid");
                try (ResultSet row = insert.executeQuery()) {
                    row.next();
                    userId = row.getLong(1);
                }
            }
            try (PreparedStatement insert =
                    db.prepareStatement("INSERT INTO proyecto(usuario_id,nombre_proyecto,anio,direccion_institucional)"
                            + " VALUES (?,'Cleanup test',2026,'Test only') RETURNING id")) {
                insert.setLong(1, userId);
                try (ResultSet row = insert.executeQuery()) {
                    row.next();
                    projectId = row.getLong(1);
                }
            }
            try (PreparedStatement insert = db.prepareStatement(
                    "INSERT INTO presupuesto(proyecto_id,version) VALUES (?,1) RETURNING id,public_id")) {
                insert.setLong(1, projectId);
                try (ResultSet row = insert.executeQuery()) {
                    row.next();
                    budgetId = row.getLong(1);
                    budgetPublicId = (UUID) row.getObject(2);
                }
            }
        }
        uploaded = reservations.claim(userId, KEY, budgetPublicId, "presupuesto", "a".repeat(64), "b".repeat(64));
        reservations.confirmUpload(
                userId,
                uploaded.operationId(),
                uploaded.fence(),
                uploaded.objectKey(),
                "A".repeat(43) + "=",
                10,
                "application/pdf",
                "budget.pdf");
    }

    @AfterEach
    void cleanupRows() throws SQLException {
        try (Connection db = dataSource.getConnection()) {
            delete(db, "DELETE FROM documento_exportacion WHERE usuario_id = ?", userId);
            delete(db, "DELETE FROM presupuesto WHERE id = ?", budgetId);
            delete(db, "DELETE FROM proyecto WHERE id = ?", projectId);
            delete(db, "DELETE FROM usuario WHERE id = ?", userId);
        }
    }

    @Test
    void onlyExpiredConfirmedObjectCanBeClaimedAndMarkedAfterDeletion() throws SQLException {
        reservations.claim(
                userId,
                UUID.fromString("01900000-0000-7000-8000-000000000015"),
                budgetPublicId,
                "presupuesto",
                "c".repeat(64),
                "b".repeat(64));
        assertTrue(cleanup.claimNext().isEmpty());
        ageObject();
        var first = cleanup.claimNext().orElseThrow();
        assertEquals(uploaded.operationId(), first.operationId());
        assertEquals(uploaded.objectKey(), first.objectKey());
        assertEquals(1, first.fence());
        assertTrue(cleanup.claimNext().isEmpty());
        cleanup.markDeleted(first.operationId(), first.fence());
        cleanup.markDeleted(first.operationId(), first.fence());
        assertTrue(cleanup.claimNext().isEmpty());
        assertTrue(deleted());
        try (Connection db = dataSource.getConnection();
                PreparedStatement query = db.prepareStatement(
                        "SELECT objeto_sha256, objeto_bytes" + " FROM documento_exportacion WHERE operation_id = ?")) {
            query.setObject(1, uploaded.operationId());
            try (ResultSet row = query.executeQuery()) {
                assertTrue(row.next());
                assertEquals("A".repeat(43) + "=", row.getString(1));
                assertEquals(10, row.getLong(2));
            }
        }
    }

    @Test
    void expiredClaimCanBeReclaimedButStaleFenceCannotMarkDeletion() throws SQLException {
        ageObject();
        var first = cleanup.claimNext().orElseThrow();
        try (Connection db = dataSource.getConnection();
                PreparedStatement expire = db.prepareStatement("UPDATE documento_exportacion"
                        + " SET cleanup_claim_until = now() - INTERVAL '1 second' WHERE operation_id = ?")) {
            expire.setObject(1, uploaded.operationId());
            assertEquals(1, expire.executeUpdate());
        }
        assertEquals(
                409,
                assertThrows(ProblemaException.class, () -> cleanup.markDeleted(first.operationId(), first.fence()))
                        .getResponse()
                        .getStatus());
        assertEquals(
                409,
                assertThrows(ProblemaException.class, () -> cleanup.release(first.operationId(), first.fence()))
                        .getResponse()
                        .getStatus());
        assertFalse(deleted());
        var next = cleanup.claimNext().orElseThrow();
        assertEquals(first.fence() + 1, next.fence());
        assertEquals(
                409,
                assertThrows(ProblemaException.class, () -> cleanup.markDeleted(first.operationId(), first.fence()))
                        .getResponse()
                        .getStatus());
        assertFalse(deleted());
        cleanup.markDeleted(next.operationId(), next.fence());
        assertTrue(deleted());
    }

    @Test
    void failedDeleteReleasesOnlyCurrentClaimAndAllowsRetry() throws SQLException {
        ageObject();
        var first = cleanup.claimNext().orElseThrow();
        cleanup.release(first.operationId(), first.fence());
        assertFalse(deleted());
        assertEquals(
                409,
                assertThrows(ProblemaException.class, () -> cleanup.markDeleted(first.operationId(), first.fence()))
                        .getResponse()
                        .getStatus());
        assertFalse(deleted());
        var next = cleanup.claimNext().orElseThrow();
        assertEquals(first.fence() + 1, next.fence());
        assertEquals(
                409,
                assertThrows(ProblemaException.class, () -> cleanup.release(first.operationId(), first.fence()))
                        .getResponse()
                        .getStatus());
        assertFalse(deleted());
    }

    @Test
    void concurrentWorkersCannotBothClaimTheSameExpiredObject() throws Exception {
        ageObject();
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var ready = new java.util.concurrent.CountDownLatch(2);
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<java.util.Optional<ExportCleanupLeaseService.Claim>> call = () -> {
                ready.countDown();
                start.await();
                return cleanup.claimNext();
            };
            var first = pool.submit(call);
            var second = pool.submit(call);
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            var claims = java.util.List.of(first.get(), second.get());
            assertEquals(
                    1, claims.stream().filter(java.util.Optional::isPresent).count());
            assertEquals(
                    uploaded.operationId(),
                    claims.stream()
                            .flatMap(java.util.Optional::stream)
                            .findFirst()
                            .orElseThrow()
                            .operationId());
        }
    }

    private void ageObject() throws SQLException {
        try (Connection db = dataSource.getConnection();
                PreparedStatement age = db.prepareStatement("UPDATE documento_exportacion"
                        + " SET uploaded_at = now() - INTERVAL '25 hours' WHERE operation_id = ?")) {
            age.setObject(1, uploaded.operationId());
            assertEquals(1, age.executeUpdate());
        }
    }

    private boolean deleted() throws SQLException {
        try (Connection db = dataSource.getConnection();
                PreparedStatement query = db.prepareStatement(
                        "SELECT deleted_at IS NOT NULL" + " FROM documento_exportacion WHERE operation_id = ?")) {
            query.setObject(1, uploaded.operationId());
            try (ResultSet row = query.executeQuery()) {
                row.next();
                return row.getBoolean(1);
            }
        }
    }

    private static void delete(Connection db, String sql, long id) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }
}
