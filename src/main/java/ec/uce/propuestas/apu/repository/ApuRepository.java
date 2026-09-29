package ec.uce.propuestas.apu.repository;

import ec.uce.propuestas.apu.entity.Apu;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acceso a {@code apu}. Reglas de negocio en los services. */
@ApplicationScoped
public class ApuRepository implements PanacheRepositoryBase<Apu, Long> {

    public Optional<Apu> findByIdYPresupuesto(Long id, Long presupuestoId) {
        return find("id = ?1 and presupuestoId = ?2", id, presupuestoId).firstResultOptional();
    }

    public Optional<Apu> findByPresupuestoYCodigo(Long presupuestoId, String codigo) {
        return find("presupuestoId = ?1 and codigo = ?2", presupuestoId, codigo).firstResultOptional();
    }

    /**
     * P-45 (N04 §ESP): APUs de una versión de presupuesto con ET no nula y no vacía
     * (Postgres: {@code <> ''}), ordenados por código. El service aplica el corte
     * adicional de whitespace-only en Java para cubrir líneas/tabs que el operador
     * SQL {@code trim()} estándar no recorta en todas las plataformas.
     */
    public List<Apu> listarConEspecificacionTecnica(Long presupuestoId) {
        return find(
                        "presupuestoId = ?1 and especificacionTecnica is not null and especificacionTecnica <> '' order by codigo",
                        presupuestoId)
                .list();
    }

    public List<Apu> listarDePresupuesto(Long presupuestoId, String q, int pageIndex, int pageSize) {
        StringBuilder ql = new StringBuilder("presupuestoId = ?1");
        if (q != null && !q.isBlank()) {
            ql.append(" and (lower(codigo) like ?2 or lower(descripcion) like ?2)");
            return find(ql.toString(), presupuestoId, "%" + q.toLowerCase() + "%")
                    .page(Page.of(pageIndex, pageSize))
                    .list();
        }
        return find(ql.toString(), presupuestoId)
                .page(Page.of(pageIndex, pageSize))
                .list();
    }

    public long contarDePresupuesto(Long presupuestoId, String q) {
        if (q != null && !q.isBlank()) {
            return count(
                    "presupuestoId = ?1 and (lower(codigo) like ?2 or lower(descripcion) like ?2)",
                    presupuestoId,
                    "%" + q.toLowerCase() + "%");
        }
        return count("presupuestoId = ?1", presupuestoId);
    }

    /** D-09: el APU está vinculado a un rubro del presupuesto (1:1) sí/no. */
    public boolean estaVinculado(Long apuId) {
        Long n = (Long) getEntityManager()
                .createNativeQuery("select count(*) from rubro where apu_id = ?1")
                .setParameter(1, apuId)
                .getSingleResult();
        return n != null && n > 0;
    }

    /** Resuelve el proyecto dueño de una versión de presupuesto (RNF-05). */
    public Optional<Long> proyectoDePresupuesto(Long presupuestoId) {
        return getEntityManager()
                .createNativeQuery("select proyecto_id from presupuesto where id = ?1")
                .setParameter(1, presupuestoId)
                .getResultStream()
                .map(o -> ((Number) o).longValue())
                .findFirst();
    }

    /** Resolves a budget's project only when that project belongs to the caller. */
    public Optional<Long> proyectoDePresupuestoOwnerScope(UUID presupuestoPublicId, Long callerUsuarioId) {
        return getEntityManager()
                .createNativeQuery("select pr.id from presupuesto p "
                        + "join proyecto pr on pr.id = p.proyecto_id "
                        + "where p.public_id = ?1 and pr.usuario_id = ?2")
                .setParameter(1, presupuestoPublicId)
                .setParameter(2, callerUsuarioId)
                .getResultStream()
                .map(o -> ((Number) o).longValue())
                .findFirst();
    }

    /** Serializes project CI saves and individual-APU CI changes on the project row. */
    public void lockProyectoCi(Long proyectoId) {
        getEntityManager()
                .createNativeQuery("select id from proyecto where id = ?1 for update")
                .setParameter(1, proyectoId)
                .getSingleResult();
    }

    /** Counts APUs with an explicit CI override in a project. */
    public long contarOverridesCiDeProyecto(Long proyectoId) {
        Number count = (Number) getEntityManager()
                .createNativeQuery("select count(*) from apu a "
                        + "join presupuesto p on p.id = a.presupuesto_id "
                        + "where p.proyecto_id = ?1 and a.porcentaje_indirecto is not null")
                .setParameter(1, proyectoId)
                .getSingleResult();
        return count.longValue();
    }

    /** Clears explicit CI overrides from every APU version belonging to a project. */
    public int limpiarOverridesCiDeProyecto(Long proyectoId) {
        return getEntityManager()
                .createNativeQuery("update apu set porcentaje_indirecto = null "
                        + "where porcentaje_indirecto is not null and presupuesto_id in "
                        + "(select id from presupuesto where proyecto_id = ?1)")
                .setParameter(1, proyectoId)
                .executeUpdate();
    }

    /** Resuelve el proyecto dueño de un APU (vía su versión). */
    public Optional<Long> proyectoDeApu(Long apuId) {
        return getEntityManager()
                .createNativeQuery(
                        "select p.proyecto_id from apu a join presupuesto p on p.id = a.presupuesto_id where a.id = ?1")
                .setParameter(1, apuId)
                .getResultStream()
                .map(o -> ((Number) o).longValue())
                .findFirst();
    }

    /**
     * Plan 036 — Resolución administrativa por {@code public_id} UUIDv7 sin owner-scope.
     * El caller debe haber superado {@code @RolesAllowed("SUPER_ADMIN")}; este método no
     * debe usarse desde recursos de usuario.
     */
    public Optional<Apu> findByPublicId(UUID publicId) {
        return find("publicId", publicId).firstResultOptional();
    }

    /**
     * WU-03 — Resolución por {@code public_id} (UUIDv7) con scope de owner. Travesía owner:
     * APU → Presupuesto → Proyecto → caller. Cualquier fila de un proyecto ajeno devuelve
     * {@link Optional#empty()}. Las juntas posteriores y el write-through usan el
     * {@code BIGINT} interno; el {@code public_id} nunca se reutiliza como FK.
     */
    public Optional<Apu> findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId) {
        return getEntityManager()
                .createQuery(
                        "select a from Apu a, Presupuesto p, Proyecto pr "
                                + "where a.publicId = :publicId and a.presupuestoId = p.id "
                                + "and p.proyectoId = pr.id and pr.usuarioId = :caller",
                        Apu.class)
                .setParameter("publicId", publicId)
                .setParameter("caller", callerUsuarioId)
                .getResultList()
                .stream()
                .findFirst();
    }
}
