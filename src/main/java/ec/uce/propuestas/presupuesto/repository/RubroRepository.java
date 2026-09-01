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

    /**
     * Plan 023 — devuelve los rubros de un presupuesto, opcionalmente filtrado
     * por capítulo. Si {@code capituloId} es {@code null} devuelve todos los
     * rubros del presupuesto (recorrido vía JOIN con {@code capitulo}).
     */
    public List<Rubro> listarPorPresupuesto(Long presupuestoId) {
        return getEntityManager()
                .createQuery(
                        "select r from Rubro r, Capitulo c "
                                + "where r.capituloId = c.id and c.presupuestoId = :presupuestoId",
                        Rubro.class)
                .setParameter("presupuestoId", presupuestoId)
                .getResultList();
    }

    /** Plan 023 — devuelve los rubros de un capítulo ordenados por {@code item} ascendente. */
    public List<Rubro> listarPorCapitulo(Long capituloId) {
        return find("capituloId = :capituloId order by item", Parameters.with("capituloId", capituloId))
                .list();
    }

    /**
     * Plan 023 — D-09: ¿el APU ya está vinculado a algún rubro del presupuesto?
     * Devuelve el rubro encontrado (cualquiera, no presupone unicidad porque la
     * constraint UNIQUE está en BD) o {@code null} si no hay vínculo. Esta
     * consulta es el «early 409» que prefiere Plan 023 sobre capturar la
     * {@code ConstraintViolationException} del INSERT.
     */
    public Optional<Rubro> findByApuId(Long apuId) {
        return find("apuId = :apuId", Parameters.with("apuId", apuId)).firstResultOptional();
    }

    /**
     * Plan 023 — resolución de un rubro por su {@code publicId} UUIDv7 restringido
     * al capítulo del path. Si pertenece a OTRO capítulo del mismo presupuesto
     * (cross-capítulo) se devuelve {@code empty}; el service lo traduce a
     * 400 {@code validacion}. Si pertenece a otro presupuesto del owner (otra
     * versión) también devuelve {@code empty}; el service lo diferencia vía
     * {@link #existeEnOtroPresupuestoDelOwner(UUID, Long, Long)}.
     */
    public Optional<Rubro> findByPublicIdEnCapitulo(UUID publicId, Long capituloId) {
        return find(
                        "publicId = :publicId and capituloId = :capituloId",
                        Parameters.with("publicId", publicId).and("capituloId", capituloId))
                .firstResultOptional();
    }

    /**
     * Plan 023 — ¿el {@code publicId} UUIDv7 del rubro existe en OTRO presupuesto
     * del mismo owner? Permite distinguir 400 (cross-version / cross-project del
     * caller) de 404 (inexistente o ajeno). La consulta viaja por la travesía
     * de owner (Rubro → Capitulo → Presupuesto → Proyecto → caller) precisamente
     * para no filtrar existencia ajena.
     */
    public boolean existeEnOtroPresupuestoDelOwner(UUID publicId, Long presupuestoId, Long callerUsuarioId) {
        return !getEntityManager()
                .createQuery(
                        "select r.id from Rubro r, Capitulo c, Presupuesto p, Proyecto pr "
                                + "where r.publicId = :publicId and r.capituloId = c.id "
                                + "and c.presupuestoId = p.id and p.id <> :presupuestoId "
                                + "and p.proyectoId = pr.id and pr.usuarioId = :caller",
                        Long.class)
                .setParameter("publicId", publicId)
                .setParameter("presupuestoId", presupuestoId)
                .setParameter("caller", callerUsuarioId)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }
}
