package ec.uce.propuestas.proyecto.repository;

import ec.uce.propuestas.proyecto.entity.EstadoProyecto;
import ec.uce.propuestas.proyecto.entity.Proyecto;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a {@code proyecto}. Las consultas de negocio (listado por propietario,
 * conteo, búsqueda) viven aquí para que el service no mezcle query con reglas.
 */
@ApplicationScoped
public class ProyectoRepository implements PanacheRepositoryBase<Proyecto, Long> {

    public List<Proyecto> listarDePropietario(Long usuarioId, int pageIndex, int pageSize) {
        return find("usuarioId = :usuarioId order by updatedAt desc", Parameters.with("usuarioId", usuarioId))
                .page(Page.of(pageIndex, pageSize))
                .list();
    }

    public List<Proyecto> buscarDePropietario(
            Long usuarioId, String q, EstadoProyecto estado, int pageIndex, int pageSize) {
        StringBuilder ql = new StringBuilder("usuarioId = :usuarioId");
        Parameters params = Parameters.with("usuarioId", usuarioId);
        if (estado != null) {
            ql.append(" and estado = :estado");
            params = params.and("estado", estado);
        }
        if (q != null && !q.isBlank()) {
            ql.append(" and (lower(str(nombreProyecto)) like :q or lower(str(codigo)) like :q)");
            params = params.and("q", "%" + q.toLowerCase() + "%");
        }
        ql.append(" order by updatedAt desc");
        return find(ql.toString(), params).page(Page.of(pageIndex, pageSize)).list();
    }

    public long contarDePropietario(Long usuarioId) {
        return count("usuarioId = :usuarioId", Parameters.with("usuarioId", usuarioId));
    }

    /** Valida propiedad (RNF-05): devuelve vacío si el proyecto no es del usuario. */
    public Optional<Proyecto> findByIdYPropietario(Long id, Long usuarioId) {
        return find(
                        "id = :id and usuarioId = :usuarioId",
                        Parameters.with("id", id).and("usuarioId", usuarioId))
                .firstResultOptional();
    }

    /**
     * WU-03 — Resolución por {@code public_id} (UUIDv7) con scope de owner. El caller debe ser
     * el {@code usuarioId} del proyecto; cualquier otro caller devuelve
     * {@link Optional#empty()} (mapeo a 404 — nunca 403). El {@code public_id} es identidad
     * externa opaca y NUNCA se usa como grant de autorización.
     */
    public Optional<Proyecto> findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId) {
        return find(
                        "publicId = :publicId and usuarioId = :caller",
                        Parameters.with("publicId", publicId).and("caller", callerUsuarioId))
                .firstResultOptional();
    }
}
