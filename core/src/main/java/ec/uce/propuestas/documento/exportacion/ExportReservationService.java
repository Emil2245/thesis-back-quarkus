package ec.uce.propuestas.documento.exportacion;

import ec.uce.propuestas.common.ProblemaException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;

/** Claims one durable export operation per user/key; only the current fence may later publish its result. */
@ApplicationScoped
public class ExportReservationService {
    @Inject
    DataSource dataSource;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Reservation claim(
            long userId,
            UUID idempotencyKey,
            UUID budgetPublicId,
            String document,
            String requestSha256,
            String revisionSha256) {
        if (userId <= 0
                || !v7(idempotencyKey)
                || !v7(budgetPublicId)
                || !("presupuesto".equals(document)
                        || "apus".equals(document)
                        || "especificaciones-tecnicas".equals(document))
                || !sha256(requestSha256)
                || !sha256(revisionSha256)) {
            throw ProblemaException.validacion("Invalid export reservation");
        }
        try (Connection db = dataSource.getConnection()) {
            long budgetId = ownedBudget(db, budgetPublicId, userId);
            try (PreparedStatement insert = db.prepareStatement("""
                    INSERT INTO documento_exportacion
                    (usuario_id, idempotency_key, presupuesto_id, presupuesto_public_id,
                     documento, request_sha256, revision_sha256)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (usuario_id, idempotency_key) DO NOTHING
                    RETURNING operation_id, fence
                    """)) {
                insert.setLong(1, userId);
                insert.setObject(2, idempotencyKey);
                insert.setLong(3, budgetId);
                insert.setObject(4, budgetPublicId);
                insert.setString(5, document);
                insert.setString(6, requestSha256);
                insert.setString(7, revisionSha256);
                try (ResultSet created = insert.executeQuery()) {
                    if (created.next()) {
                        return new Reservation((UUID) created.getObject(1), created.getLong(2), "RESERVADA");
                    }
                }
            }
            return existing(db, userId, idempotencyKey, budgetPublicId, document, requestSha256, revisionSha256);
        } catch (SQLException unavailable) {
            throw new IllegalStateException("Export reservation database operation failed", unavailable);
        }
    }

    /** Persist upload only after Documentos has confirmed S3; a stale owner cannot publish its result. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Reservation confirmUpload(
            long userId,
            UUID operationId,
            long fence,
            String objectKey,
            String checksum,
            long bytes,
            String mimeType,
            String filename) {
        if (userId <= 0
                || !v7(operationId)
                || fence <= 0
                || !(("exports/" + operationId).equals(objectKey))
                || checksum == null
                || !checksum.matches("[A-Za-z0-9+/]{43}=")
                || bytes <= 0
                || bytes > 64L * 1024 * 1024
                || mimeType == null
                || mimeType.isBlank()
                || filename == null
                || !filename.matches("[A-Za-z0-9._-]{1,128}")) {
            throw ProblemaException.conflicto("exportacion-no-confirmada", "Metadatos de subida inválidos");
        }
        try (Connection db = dataSource.getConnection();
                PreparedStatement query = db.prepareStatement("""
                        SELECT id, fence, estado, lease_until > now() AS lease_live,
                               documento, objeto_sha256, objeto_bytes, objeto_mime, objeto_nombre
                        FROM documento_exportacion
                        WHERE operation_id = ? AND usuario_id = ? FOR UPDATE
                        """)) {
            query.setObject(1, operationId);
            query.setLong(2, userId);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next() || rows.getLong(2) != fence) {
                    throw ProblemaException.conflicto(
                            "exportacion-obsoleta", "La reserva ya no pertenece a esta ejecución");
                }
                long id = rows.getLong(1);
                String state = rows.getString(3);
                String document = rows.getString(5);
                boolean specification = "especificaciones-tecnicas".equals(document);
                boolean validFormat = specification
                        ? mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                                && filename.endsWith(".docx")
                        : (mimeType.equals("application/pdf") && filename.endsWith(".pdf"))
                                || (mimeType.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                                        && filename.endsWith(".xlsx"));
                if (!validFormat) {
                    throw ProblemaException.conflicto("exportacion-no-confirmada", "Formato de objeto inválido");
                }
                if (!"RESERVADA".equals(state)) {
                    if (checksum.equals(rows.getString(6))
                            && bytes == rows.getLong(7)
                            && mimeType.equals(rows.getString(8))
                            && filename.equals(rows.getString(9))) {
                        return new Reservation(operationId, fence, state);
                    }
                    throw ProblemaException.conflicto("exportacion-conflicto", "Objeto confirmado distinto");
                }
                if (!rows.getBoolean(4)) {
                    throw ProblemaException.conflicto("exportacion-obsoleta", "El lease de la reserva caducó");
                }
                try (PreparedStatement update = db.prepareStatement("""
                        UPDATE documento_exportacion
                        SET estado = 'SUBIDA', objeto_sha256 = ?, objeto_bytes = ?, objeto_mime = ?,
                            objeto_nombre = ?, uploaded_at = now(), updated_at = now()
                        WHERE id = ? AND fence = ? AND estado = 'RESERVADA'
                        """)) {
                    update.setString(1, checksum);
                    update.setLong(2, bytes);
                    update.setString(3, mimeType);
                    update.setString(4, filename);
                    update.setLong(5, id);
                    update.setLong(6, fence);
                    if (update.executeUpdate() != 1) {
                        throw ProblemaException.conflicto(
                                "exportacion-obsoleta", "La reserva cambió durante la subida");
                    }
                }
                return new Reservation(operationId, fence, "SUBIDA");
            }
        } catch (SQLException unavailable) {
            throw new IllegalStateException("Export upload confirmation database operation failed", unavailable);
        }
    }

    private static long ownedBudget(Connection db, UUID budgetPublicId, long userId) throws SQLException {
        try (PreparedStatement query = db.prepareStatement("""
                SELECT b.id FROM presupuesto b JOIN proyecto p ON p.id = b.proyecto_id
                WHERE b.public_id = ? AND p.usuario_id = ?
                """)) {
            query.setObject(1, budgetPublicId);
            query.setLong(2, userId);
            try (ResultSet rows = query.executeQuery()) {
                if (rows.next()) return rows.getLong(1);
            }
        }
        throw ProblemaException.noEncontrado("Presupuesto no encontrado");
    }

    private static Reservation existing(
            Connection db,
            long userId,
            UUID key,
            UUID budgetId,
            String document,
            String requestHash,
            String revisionHash)
            throws SQLException {
        try (PreparedStatement query = db.prepareStatement("""
                SELECT id, operation_id, fence, estado, lease_until > now() AS lease_live,
                       presupuesto_public_id, documento, request_sha256, revision_sha256
                FROM documento_exportacion WHERE usuario_id = ? AND idempotency_key = ? FOR UPDATE
                """)) {
            query.setLong(1, userId);
            query.setObject(2, key);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) throw new IllegalStateException("Export reservation disappeared after conflict");
                long id = rows.getLong(1);
                UUID operationId = (UUID) rows.getObject(2);
                long fence = rows.getLong(3);
                String state = rows.getString(4);
                boolean leaseLive = rows.getBoolean(5);
                if (!budgetId.equals(rows.getObject(6))
                        || !document.equals(rows.getString(7))
                        || !requestHash.equals(rows.getString(8))
                        || !revisionHash.equals(rows.getString(9))) {
                    throw ProblemaException.conflicto(
                            "idempotencia-conflicto", "La clave ya pertenece a otra exportación");
                }
                if (!"RESERVADA".equals(state)) return new Reservation(operationId, fence, state);
                if (leaseLive) throw ProblemaException.conflicto("exportacion-en-curso", "Exportación en curso");
                try (PreparedStatement reclaim = db.prepareStatement("""
                        UPDATE documento_exportacion
                        SET fence = fence + 1, lease_until = now() + INTERVAL '2 minutes', updated_at = now()
                        WHERE id = ? RETURNING fence
                        """)) {
                    reclaim.setLong(1, id);
                    try (ResultSet renewed = reclaim.executeQuery()) {
                        if (!renewed.next()) throw new IllegalStateException("Export reservation lease disappeared");
                        return new Reservation(operationId, renewed.getLong(1), "RESERVADA");
                    }
                }
            }
        }
    }

    private static boolean sha256(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static boolean v7(UUID value) {
        return value != null && value.version() == 7 && value.variant() == 2;
    }

    public record Reservation(UUID operationId, long fence, String state) {
        public String objectKey() {
            return "exports/" + operationId;
        }
    }
}
