package ec.uce.propuestas.insumo.repository;

import ec.uce.propuestas.insumo.entity.BaseInsumos;
import ec.uce.propuestas.insumo.entity.TipoBase;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class BaseInsumosRepository implements PanacheRepositoryBase<BaseInsumos, Long> {

    public Optional<BaseInsumos> findByProyecto(Long proyectoId) {
        return find("proyectoId = ?1", proyectoId).firstResultOptional();
    }

    public List<BaseInsumos> listarCentralesActivas() {
        return find("tipo = ?1 and archivada = false", TipoBase.CENTRAL).list();
    }

    /**
     * WU-03 — Resolución por {@code public_id} (UUIDv7) con scope de owner.
     *
     * <p>Reglas de visibilidad:
     * <ul>
     *   <li>{@code PERSONAL} → caller == {@code usuarioId}</li>
     *   <li>{@code PROYECTO} → caller es dueño del proyecto padre</li>
     *   <li>{@code CENTRAL} → no visible desde el seam USUARIO (se atiende
     *       por la variante de Super-Admin en una iteración posterior)</li>
     * </ul>
     *
     * <p>Una fila de otro dueño devuelve {@link Optional#empty()} (mapeo a 404).
     */
    public Optional<BaseInsumos> findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId) {
        return getEntityManager()
                .createQuery(
                        "select b from BaseInsumos b, Proyecto p "
                                + "where b.publicId = :publicId "
                                + "and ((b.tipo = :tipoPersonal and b.usuarioId = :caller) "
                                + "  or (b.tipo = :tipoProyecto "
                                + "      and b.proyectoId = p.id and p.usuarioId = :caller))",
                        BaseInsumos.class)
                .setParameter("publicId", publicId)
                .setParameter("caller", callerUsuarioId)
                .setParameter("tipoPersonal", TipoBase.PERSONAL)
                .setParameter("tipoProyecto", TipoBase.PROYECTO)
                .getResultList()
                .stream()
                .findFirst();
    }
}
