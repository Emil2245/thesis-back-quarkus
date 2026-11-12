package ec.uce.propuestas.documento.exportacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
class DownloadTicketIT {
    private static final UUID KEY = UUID.fromString("01900000-0000-7000-8000-000000000012");
    private static final String HASH = "a".repeat(64);
    private static final String CHECKSUM = "A".repeat(43) + "=";

    @Inject
    DataSource dataSource;

    @Inject
    ExportReservationService reservations;

    @Inject
    DownloadTicketService tickets;

    private long userId;
    private long projectId;
    private long budgetId;
    private UUID budgetPublicId;
    private ExportReservationService.Reservation uploaded;

    @BeforeEach
    void seed() throws SQLException {
        try (Connection db = dataSource.getConnection()) {
            try (PreparedStatement insert = db.prepareStatement("INSERT INTO usuario(nombre,email,password_hash)"
                    + " VALUES ('Ticket test',?,'synthetic-hash') RETURNING id")) {
                insert.setString(1, "stage03-ticket-" + UUID.randomUUID() + "@example.invalid");
                try (ResultSet row = insert.executeQuery()) {
                    row.next();
                    userId = row.getLong(1);
                }
            }
            try (PreparedStatement insert =
                    db.prepareStatement("INSERT INTO proyecto(usuario_id,nombre_proyecto,anio,direccion_institucional)"
                            + " VALUES (?,'Ticket test',2026,'Test only') RETURNING id")) {
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
        uploaded = reservations.claim(userId, KEY, budgetPublicId, "presupuesto", HASH, HASH);
        reservations.confirmUpload(
                userId,
                uploaded.operationId(),
                uploaded.fence(),
                uploaded.objectKey(),
                CHECKSUM,
                123,
                "application/pdf",
                "budget.pdf");
    }

    @AfterEach
    void cleanup() throws SQLException {
        try (Connection db = dataSource.getConnection()) {
            try (PreparedStatement delete = db.prepareStatement("DELETE FROM documento_descarga_ticket"
                    + " WHERE exportacion_id IN (SELECT id FROM documento_exportacion WHERE usuario_id = ?)")) {
                delete.setLong(1, userId);
                delete.executeUpdate();
            }
            delete(db, "DELETE FROM documento_exportacion WHERE usuario_id = ?", userId);
            delete(db, "DELETE FROM presupuesto WHERE id = ?", budgetId);
            delete(db, "DELETE FROM proyecto WHERE id = ?", projectId);
            delete(db, "DELETE FROM usuario WHERE id = ?", userId);
        }
    }

    private static void delete(Connection db, String sql, long id) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }

    @Test
    void storesOnlyHashAndConsumesOnceBeforeObjectRead() throws SQLException {
        var issued = tickets.issue(userId, uploaded.operationId());
        assertEquals(43, issued.token().length());
        assertTrue(issued.expiresAt().isAfter(java.time.Instant.now()));
        assertTrue(issued.expiresAt().isBefore(issued.objectExpiresAt()));
        assertTrue(issued.expiresAt().isAfter(issued.objectExpiresAt().minus(java.time.Duration.ofHours(24))));
        try (Connection db = dataSource.getConnection();
                PreparedStatement query = db.prepareStatement("SELECT token_sha256 FROM documento_descarga_ticket"
                        + " WHERE exportacion_id = (SELECT id FROM documento_exportacion WHERE operation_id = ?)")) {
            query.setObject(1, uploaded.operationId());
            try (ResultSet row = query.executeQuery()) {
                assertTrue(row.next());
                assertEquals(64, row.getString(1).length());
                assertNotEquals(issued.token(), row.getString(1));
                assertFalse(row.next());
            }
        }
        var consumed = tickets.consume(issued.token());
        assertEquals(uploaded.objectKey(), consumed.objectKey());
        assertEquals(CHECKSUM, consumed.sha256());
        assertEquals(123, consumed.sizeBytes());
        assertEquals("application/pdf", consumed.mediaType());
        assertEquals("budget.pdf", consumed.filename());
        assertEquals(
                404,
                assertThrows(ProblemaException.class, () -> tickets.consume(issued.token()))
                        .getResponse()
                        .getStatus());
        assertEquals(
                404,
                assertThrows(ProblemaException.class, () -> tickets.consume("invalid"))
                        .getResponse()
                        .getStatus());
    }

    @Test
    void twoSimultaneousConsumesHaveExactlyOneWinner() throws Exception {
        var issued = tickets.issue(userId, uploaded.operationId());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var ready = new java.util.concurrent.CountDownLatch(2);
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> consume = () -> {
                ready.countDown();
                start.await();
                try {
                    tickets.consume(issued.token());
                    return 200;
                } catch (ProblemaException absent) {
                    return absent.getResponse().getStatus();
                }
            };
            var first = pool.submit(consume);
            var second = pool.submit(consume);
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            assertEquals(
                    java.util.List.of(200, 404),
                    java.util.stream.Stream.of(first.get(), second.get())
                            .sorted()
                            .toList());
        }
    }

    @Test
    void objectExpiryRevokesUnusedTicketAndPreventsNewIssue() throws SQLException {
        var issued = tickets.issue(userId, uploaded.operationId());
        try (Connection db = dataSource.getConnection();
                PreparedStatement expire = db.prepareStatement("UPDATE documento_exportacion"
                        + " SET uploaded_at = now() - INTERVAL '25 hours' WHERE operation_id = ?")) {
            expire.setObject(1, uploaded.operationId());
            assertEquals(1, expire.executeUpdate());
        }
        assertEquals(
                404,
                assertThrows(ProblemaException.class, () -> tickets.consume(issued.token()))
                        .getResponse()
                        .getStatus());
        assertEquals(
                409,
                assertThrows(ProblemaException.class, () -> tickets.issue(userId, uploaded.operationId()))
                        .getResponse()
                        .getStatus());
    }
}
