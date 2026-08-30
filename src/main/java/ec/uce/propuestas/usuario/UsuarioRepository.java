package ec.uce.propuestas.usuario;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UsuarioRepository implements PanacheRepositoryBase<Usuario, Long> {

    public Optional<Usuario> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }

    public boolean isEmailTaken(String email) {
        return count("email", email) > 0;
    }

    /**
     * WU-03 — Resolución por {@code public_id} (UUIDv7) con scope de owner.
     * Para SUPER_ADMIN el caller se mantiene; la política de visibilidad por
     * rol se aplica en capas superiores. Una fila de otro usuario devuelve
     * {@link Optional#empty()} (mapeo a 404) — nunca se usa {@code public_id}
     * como autorización.
     */
    public Optional<Usuario> findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId) {
        return find(
                        "publicId = :publicId and id = :caller",
                        Parameters.with("publicId", publicId).and("caller", callerUsuarioId))
                .firstResultOptional();
    }
}
