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
     * Plan 05 — Listado administrativo de bases CENTRALES. Cuando
     * {@code incluirArchivadas = false} (default para el catálogo normal de
     * usuarios) oculta las archivadas; cuando es {@code true} las expone para
     * que el administrador mantenga la visibilidad del catálogo completo.
     */
    public List<BaseInsumos> listarCentralesAdmin(boolean incluirArchivadas) {
        if (incluirArchivadas) {
            return find("tipo = ?1 order by nombre", TipoBase.CENTRAL).list();
        }
        return find("tipo = ?1 and archivada = false order by nombre", TipoBase.CENTRAL)
                .list();
    }

    /**
     * Plan 05 — Resolución administrativa por {@code public_id} (UUIDv7). No
     * aplica scope de owner: el caller ya pasó el guard
     * {@code @RolesAllowed("SUPER_ADMIN")}. Una fila que no existe o no es
     * CENTRAL devuelve {@link Optional#empty()} (mapeo a 404).
     */
    public Optional<BaseInsumos> findCentralByPublicId(UUID publicId) {
        return find("publicId = ?1 and tipo = ?2", publicId, TipoBase.CENTRAL).firstResultOptional();
    }

    /**
     * Plan 05 — Unicidad lógica del nombre dentro del segmento CENTRAL. Las
     * bases CENTRALES comparten el namespace global (no hay dueño), por lo que
     * dos activas con el mismo nombre no se permiten.
     */
    public long contarCentralPorNombre(String nombre) {
        return count("tipo = ?1 and nombre = ?2", TipoBase.CENTRAL, nombre);
    }

    /**
     * WU-05 — Bases PERSONALES de un usuario, sin incluir CENTRAL ni
     * PROYECTO. Orden estable por nombre para el catálogo del titular.
     */
    public List<BaseInsumos> listarPersonalesDeUsuario(Long usuarioId) {
        return find("tipo = ?1 and usuarioId = ?2 order by nombre", TipoBase.PERSONAL, usuarioId)
                .list();
    }

    /** WU-05 — Unicidad lógica del nombre dentro del segmento PERSONAL por usuario. */
    public long contarPorUsuarioYTipoYNombre(Long usuarioId, TipoBase tipo, String nombre) {
        return count("usuarioId = ?1 and tipo = ?2 and nombre = ?3", usuarioId, tipo, nombre);
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
                        "select b from BaseInsumos b "
                                + "where b.publicId = :publicId "
                                + "and ((b.tipo = :tipoPersonal and b.usuarioId = :caller) "
                                + "  or (b.tipo = :tipoProyecto and exists "
                                + "      (select 1 from Proyecto p "
                                + "       where p.id = b.proyectoId and p.usuarioId = :caller)))",
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
