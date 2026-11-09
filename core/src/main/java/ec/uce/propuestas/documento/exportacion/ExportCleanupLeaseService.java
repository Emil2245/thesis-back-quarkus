package ec.uce.propuestas.documento.exportacion;

import ec.uce.propuestas.common.ProblemaException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

/** DB-only fenced cleanup claims; the caller deletes from S3 before marking success. */
@ApplicationScoped
public class ExportCleanupLeaseService {
    @Inject
    DataSource dataSource;

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Optional<Claim> claimNext() {
        try (Connection db = dataSource.getConnection();
                PreparedStatement claim = db.prepareStatement("""
                        WITH candidate AS (
                            SELECT id FROM documento_exportacion
                            WHERE uploaded_at <= now() - INTERVAL '24 hours'
                              AND estado IN ('SUBIDA', 'AUDITADA') AND deleted_at IS NULL
                              AND (cleanup_claim_until IS NULL OR cleanup_claim_until <= now())
                            ORDER BY uploaded_at, id
                            FOR UPDATE SKIP LOCKED LIMIT 1
                        )
                        UPDATE documento_exportacion e
                        SET cleanup_claim_until = now() + INTERVAL '2 minutes',
                            cleanup_fence = cleanup_fence + 1, updated_at = now()
                        FROM candidate c WHERE e.id = c.id
                        RETURNING e.operation_id, e.cleanup_fence
                        """)) {
            try (ResultSet row = claim.executeQuery()) {
                if (!row.next()) return Optional.empty();
                UUID operation = (UUID) row.getObject(1);
                return Optional.of(new Claim(operation, row.getLong(2), "exports/" + operation));
            }
        } catch (SQLException unavailable) {
            throw new IllegalStateException("Export cleanup claim database operation failed", unavailable);
        }
    }

    /** Called only after a successful private DeleteObject; no audit/export metadata is erased. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void markDeleted(UUID operation, long fence) {
        validate(operation, fence);
        try (Connection db = dataSource.getConnection();
                PreparedStatement update = db.prepareStatement("""
                        UPDATE documento_exportacion
                        SET deleted_at = now(), cleanup_claim_until = NULL, updated_at = now()
                        WHERE operation_id = ? AND cleanup_fence = ? AND deleted_at IS NULL
                          AND cleanup_claim_until > now()
                          AND uploaded_at <= now() - INTERVAL '24 hours'
                        """)) {
            update.setObject(1, operation);
            update.setLong(2, fence);
            if (update.executeUpdate() == 1) return;
            // A lost response after the marker is committed may safely repeat the same confirmation.
            try (PreparedStatement already = db.prepareStatement("""
                    SELECT 1 FROM documento_exportacion
                    WHERE operation_id = ? AND cleanup_fence = ? AND deleted_at IS NOT NULL
                    """)) {
                already.setObject(1, operation);
                already.setLong(2, fence);
                try (ResultSet row = already.executeQuery()) {
                    if (row.next()) return;
                }
            }
        } catch (SQLException unavailable) {
            throw new IllegalStateException("Export cleanup confirmation database operation failed", unavailable);
        }
        throw stale();
    }

    /** On S3 failure, make only this worker's claim retryable without marking the object deleted. */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void release(UUID operation, long fence) {
        validate(operation, fence);
        try (Connection db = dataSource.getConnection();
                PreparedStatement update = db.prepareStatement("""
                        UPDATE documento_exportacion
                        SET cleanup_claim_until = now() - INTERVAL '1 second', updated_at = now()
                        WHERE operation_id = ? AND cleanup_fence = ? AND deleted_at IS NULL
                          AND cleanup_claim_until > now()
                        """)) {
            update.setObject(1, operation);
            update.setLong(2, fence);
            if (update.executeUpdate() != 1) throw stale();
        } catch (SQLException unavailable) {
            throw new IllegalStateException("Export cleanup release database operation failed", unavailable);
        }
    }

    private static void validate(UUID operation, long fence) {
        if (operation == null || operation.version() != 7 || operation.variant() != 2 || fence <= 0) {
            throw stale();
        }
    }

    private static ProblemaException stale() {
        return ProblemaException.conflicto(
                "limpieza-obsoleta", "La reserva de limpieza ya no pertenece a esta ejecución");
    }

    public record Claim(UUID operationId, long fence, String objectKey) {}
}
