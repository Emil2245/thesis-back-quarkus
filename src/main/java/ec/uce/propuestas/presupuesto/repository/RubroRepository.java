package ec.uce.propuestas.presupuesto.repository;

import ec.uce.propuestas.presupuesto.entity.Rubro;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acceso a {@code rubro}. Reglas de negocio en los services. */
@ApplicationScoped
public class RubroRepository implements PanacheRepositoryBase<Rubro, Long> {

    /**
     * WU-03 — Resolución por {@code public_id} (UUIDv7) con scope de owner. Travesía owner:
     * Rubro → Capitulo → Presupuesto → Proyecto → caller. Cualquier fila de un proyecto
     * ajeno devuelve {@link Optional#empty()} (mapeo a 404 — nunca 403). El {@code public_id}
     * nunca se usa como FK ni como grant de autorización.
     */
    public Optional<Rubro> findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId) {
        return getEntityManager()
                .createQuery(
                        "select r from Rubro r, Capitulo c, Presupuesto p, Proyecto pr "
                                + "where r.publicId = :publicId and r.capituloId = c.id "
                                + "and c.presupuestoId = p.id and p.proyectoId = pr.id "
                                + "and pr.usuarioId = :caller",
                        Rubro.class)
                .setParameter("publicId", publicId)
                .setParameter("caller", callerUsuarioId)
                .getResultList()
                .stream()
                .findFirst();
    }

    /**
     * Plan 021 — devuelve los rubros cuyos capítulos están en la colección
     * indicada. La query preserva el orden de inserción (lectura por
     * {@code capituloId}); el orden por {@code item} se aplica en el mapper
     * al construir el read model.
     */
    public List<Rubro> listarPorCapitulos(Collection<Long> capituloIds) {
        if (capituloIds == null || capituloIds.isEmpty()) {
            return List.of();
        }
        return find("capituloId in :ids", Parameters.with("ids", capituloIds)).list();
    }
}
