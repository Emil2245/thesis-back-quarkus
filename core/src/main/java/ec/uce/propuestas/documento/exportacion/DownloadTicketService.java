package ec.uce.propuestas.documento.exportacion;

import ec.uce.propuestas.common.ProblemaException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.sql.DataSource;

/** Core-only capability issuance and atomic consumption; no S3 read happens in this service. */
@ApplicationScoped
public class DownloadTicketService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration OBJECT_LIFETIME = Duration.ofHours(24);
    private static final Duration TICKET_LIFETIME = Duration.ofMinutes(5);

    @Inject
    DataSource dataSource;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public IssuedTicket issue(long userId, UUID operationId) {
        if (userId <= 0 || operationId == null || operationId.version() != 7 || operationId.variant() != 2) {
            throw ProblemaException.noEncontrado("Exportación no disponible");
        }
        try (Connection db = dataSource.getConnection();
                PreparedStatement query = db.prepareStatement("""
                        SELECT id, estado, documento, uploaded_at, now() AS db_now
                        FROM documento_exportacion
                        WHERE usuario_id = ? AND operation_id = ? FOR UPDATE
                        """)) {
            query.setLong(1, userId);
            query.setObject(2, operationId);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) throw ProblemaException.noEncontrado("Exportación no disponible");
                String state = rows.getString(2);
                String document = rows.getString(3);
                boolean confirmed = "especificaciones-tecnicas".equals(document)
                        ? "AUDITADA".equals(state)
                        : "SUBIDA".equals(state);
                OffsetDateTime uploaded = rows.getObject(4, OffsetDateTime.class);
                if (!confirmed || uploaded == null) {
                    throw ProblemaException.conflicto("exportacion-no-confirmada", "Exportación no confirmada");
                }
                Instant now = rows.getObject(5, OffsetDateTime.class).toInstant();
                Instant objectExpiresAt = uploaded.toInstant().plus(OBJECT_LIFETIME);
                if (!objectExpiresAt.isAfter(now)) {
                    throw ProblemaException.conflicto("exportacion-expirada", "Objeto expirado");
                }
                Instant expiresAt = now.plus(TICKET_LIFETIME);
                if (expiresAt.isAfter(objectExpiresAt)) expiresAt = objectExpiresAt;
                byte[] secret = new byte[32];
                RANDOM.nextBytes(secret);
                String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
                try (PreparedStatement insert = db.prepareStatement("""
                        INSERT INTO documento_descarga_ticket(exportacion_id, token_sha256, issued_at, expires_at)
                        VALUES (?, ?, ?, ?)
                        """)) {
                    insert.setLong(1, rows.getLong(1));
                    insert.setString(2, digest(token));
                    insert.setObject(3, OffsetDateTime.ofInstant(now, java.time.ZoneOffset.UTC));
                    insert.setObject(4, OffsetDateTime.ofInstant(expiresAt, java.time.ZoneOffset.UTC));
                    insert.executeUpdate();
                }
                return new IssuedTicket(token, expiresAt, objectExpiresAt);
            }
        } catch (SQLException unavailable) {
            throw new IllegalStateException("Download ticket issuance database operation failed", unavailable);
        }
    }

    /** The UPDATE consumes before the caller can attempt private GetObject; failure does not restore the ticket. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public ConfirmedObject consume(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw ProblemaException.noEncontrado("Descarga no disponible");
        }
        try (Connection db = dataSource.getConnection();
                PreparedStatement consume = db.prepareStatement("""
                        UPDATE documento_descarga_ticket t SET consumed_at = now()
                        FROM documento_exportacion e
                        WHERE t.exportacion_id = e.id AND t.token_sha256 = ?
                          AND t.consumed_at IS NULL AND t.expires_at > now()
                          AND e.uploaded_at + INTERVAL '24 hours' > now()
                          AND ((e.documento = 'especificaciones-tecnicas' AND e.estado = 'AUDITADA')
                            OR (e.documento <> 'especificaciones-tecnicas' AND e.estado = 'SUBIDA'))
                        RETURNING e.operation_id, e.objeto_sha256, e.objeto_bytes, e.objeto_mime, e.objeto_nombre
                        """)) {
            consume.setString(1, digest(token));
            try (ResultSet row = consume.executeQuery()) {
                if (!row.next()) throw ProblemaException.noEncontrado("Descarga no disponible");
                UUID operationId = (UUID) row.getObject(1);
                return new ConfirmedObject(
                        "exports/" + operationId, row.getString(2), row.getLong(3), row.getString(4), row.getString(5));
            }
        } catch (SQLException unavailable) {
            throw new IllegalStateException("Download ticket consumption database operation failed", unavailable);
        }
    }

    private static String digest(String token) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public record IssuedTicket(String token, Instant expiresAt, Instant objectExpiresAt) {}

    public record ConfirmedObject(String objectKey, String sha256, long sizeBytes, String mediaType, String filename) {}
}
