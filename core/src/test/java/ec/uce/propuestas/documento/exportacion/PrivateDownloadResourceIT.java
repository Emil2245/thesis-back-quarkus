package ec.uce.propuestas.documento.exportacion;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
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
class PrivateDownloadResourceIT {
    private static final UUID KEY = UUID.fromString("01900000-0000-7000-8000-000000000013");
    private static final byte[] BYTES = "private pdf".getBytes(StandardCharsets.UTF_8);

    @Inject
    DataSource dataSource;

    @Inject
    ExportReservationService reservations;

    @Inject
    DownloadTicketService tickets;

    @InjectMock
    CorePrivateObjectReader reader;

    private long userId;
    private long projectId;
    private long budgetId;
    private UUID budgetPublicId;
    private ExportReservationService.Reservation uploaded;

    @BeforeEach
    void seed() throws SQLException {
        try (Connection db = dataSource.getConnection()) {
            try (PreparedStatement insert = db.prepareStatement("INSERT INTO usuario(nombre,email,password_hash)"
                    + " VALUES ('Proxy test',?,'synthetic-hash') RETURNING id")) {
                insert.setString(1, "stage03-proxy-" + UUID.randomUUID() + "@example.invalid");
                try (ResultSet row = insert.executeQuery()) {
                    row.next();
                    userId = row.getLong(1);
                }
            }
            try (PreparedStatement insert =
                    db.prepareStatement("INSERT INTO proyecto(usuario_id,nombre_proyecto,anio,direccion_institucional)"
                            + " VALUES (?,'Proxy test',2026,'Test only') RETURNING id")) {
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
                BYTES.length,
                "application/pdf",
                "budget.pdf");
    }

    @AfterEach
    void cleanup() throws SQLException {
        try (Connection db = dataSource.getConnection()) {
            delete(
                    db,
                    "DELETE FROM documento_descarga_ticket WHERE exportacion_id IN"
                            + " (SELECT id FROM documento_exportacion WHERE usuario_id = ?)",
                    userId);
            delete(db, "DELETE FROM documento_exportacion WHERE usuario_id = ?", userId);
            delete(db, "DELETE FROM presupuesto WHERE id = ?", budgetId);
            delete(db, "DELETE FROM proyecto WHERE id = ?", projectId);
            delete(db, "DELETE FROM usuario WHERE id = ?", userId);
        }
    }

    @Test
    void anonymousNavigationConsumesTicketBeforeReturningPrivateBytes() {
        when(reader.read(any())).thenReturn(BYTES);
        String token = tickets.issue(userId, uploaded.operationId()).token();
        String path = "/api/v1/documentos/descargas/" + token;
        var firstResponse = given().accept("application/pdf").when().get(path);
        org.junit.jupiter.api.Assertions.assertEquals(200, firstResponse.statusCode(), firstResponse.asString());
        byte[] downloaded = firstResponse
                .then()
                .header("Content-Type", "application/pdf")
                .header("Cache-Control", "no-store")
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", "attachment; filename=\"budget.pdf\"")
                .extract()
                .asByteArray();
        assertArrayEquals(BYTES, downloaded);
        given().when().get(path).then().statusCode(404);
        verify(reader, times(1)).read(any());
    }

    @Test
    void confirmedCronogramaXmlTicketIsPublicAndOneUseWithXmlAccept() {
        var cronograma = reservations.claim(
                userId,
                UUID.fromString("01900000-0000-7000-8000-000000000014"),
                budgetPublicId,
                "cronograma",
                "c".repeat(64),
                "d".repeat(64));
        reservations.confirmUpload(
                userId,
                cronograma.operationId(),
                cronograma.fence(),
                cronograma.objectKey(),
                "A".repeat(43) + "=",
                BYTES.length,
                "application/xml",
                "schedule.xml");
        reservations.finalizeCronogramaAudit(userId, cronograma.operationId(), cronograma.fence(), "MSPDI", false);
        when(reader.read(any())).thenReturn(BYTES);
        String path = "/api/v1/documentos/descargas/"
                + tickets.issue(userId, cronograma.operationId()).token();
        byte[] downloaded = given().accept("application/xml")
                .when()
                .get(path)
                .then()
                .statusCode(200)
                .header("Content-Type", "application/xml")
                .header("Content-Disposition", "attachment; filename=\"schedule.xml\"")
                .header("Cache-Control", "no-store")
                .header("Referrer-Policy", "no-referrer")
                .header("X-Content-Type-Options", "nosniff")
                .extract()
                .asByteArray();
        assertArrayEquals(BYTES, downloaded);
        given().accept("application/xml").when().get(path).then().statusCode(404);
        verify(reader, times(1)).read(any());
    }

    @Test
    void existingXlsxAndDocxTicketsRemainDownloadable() {
        String[][] formats = {
            {
                "01900000-0000-7000-8000-000000000015",
                "presupuesto",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "budget.xlsx"
            },
            {
                "01900000-0000-7000-8000-000000000016",
                "especificaciones-tecnicas",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "spec.docx"
            }
        };
        when(reader.read(any())).thenReturn(BYTES);
        for (String[] format : formats) {
            var reservation = reservations.claim(
                    userId, UUID.fromString(format[0]), budgetPublicId, format[1], "e".repeat(64), "f".repeat(64));
            reservations.confirmUpload(
                    userId,
                    reservation.operationId(),
                    reservation.fence(),
                    reservation.objectKey(),
                    "A".repeat(43) + "=",
                    BYTES.length,
                    format[2],
                    format[3]);
            if ("especificaciones-tecnicas".equals(format[1])) {
                reservations.finalizeAudit(userId, reservation.operationId(), reservation.fence());
            }
            String path = "/api/v1/documentos/descargas/"
                    + tickets.issue(userId, reservation.operationId()).token();
            byte[] downloaded = given().accept(format[2])
                    .when()
                    .get(path)
                    .then()
                    .statusCode(200)
                    .header("Content-Type", format[2])
                    .header("Content-Disposition", "attachment; filename=\"" + format[3] + "\"")
                    .extract()
                    .asByteArray();
            assertArrayEquals(BYTES, downloaded);
            given().accept(format[2]).when().get(path).then().statusCode(404);
        }
        verify(reader, times(2)).read(any());
    }

    @Test
    void storageFailureDoesNotRestoreConsumedTicket() {
        when(reader.read(any())).thenThrow(new IllegalStateException("private-test-bucket secret"));
        String path = "/api/v1/documentos/descargas/"
                + tickets.issue(userId, uploaded.operationId()).token();
        var firstResponse = given().when().get(path);
        org.junit.jupiter.api.Assertions.assertEquals(500, firstResponse.statusCode(), firstResponse.asString());
        String error = firstResponse.asString();
        org.junit.jupiter.api.Assertions.assertFalse(error.contains("private-test-bucket"));
        firstResponse.then().header("Cache-Control", "no-store").header("Referrer-Policy", "no-referrer");
        given().when().get(path).then().statusCode(404);
        verify(reader, times(1)).read(any());
    }

    @Test
    void invalidTicketNeverReadsStorage() {
        given().when()
                .get("/api/v1/documentos/descargas/invalid")
                .then()
                .statusCode(404)
                .header("Cache-Control", "no-store")
                .header("Referrer-Policy", "no-referrer");
        verify(reader, times(0)).read(any());
    }

    private static void delete(Connection db, String sql, long id) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        }
    }
}
