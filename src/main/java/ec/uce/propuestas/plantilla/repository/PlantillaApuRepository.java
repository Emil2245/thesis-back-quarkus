package ec.uce.propuestas.plantilla.repository;

import ec.uce.propuestas.plantilla.entity.PlantillaApu;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PlantillaApuRepository implements PanacheRepositoryBase<PlantillaApu, Long> {

    /**
     * WU-03 — Resolución por {@code public_id} (UUIDv7) con scope de owner. Reglas de
     * visibilidad:
     * <ul>
     *   <li>{@code SISTEMA} → visible para todos los usuarios autenticados (no lleva owner)</li>
     *   <li>{@code PERSONAL} → caller == {@code usuarioId}</li>
     * </ul>
     * Una fila ajena devuelve {@link Optional#empty()} (mapeo a 404).
     */
    public Optional<PlantillaApu> findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId) {
        return getEntityManager()
                .createQuery(
                        "select pl from PlantillaApu pl "
                                + "where pl.publicId = :publicId "
                                + "and (pl.tipo = :tipoSistema or "
                                + "     (pl.tipo = :tipoPersonal and pl.usuarioId = :caller))",
                        PlantillaApu.class)
                .setParameter("publicId", publicId)
                .setParameter("caller", callerUsuarioId)
                .setParameter("tipoSistema", PlantillaApu.Tipo.SISTEMA)
                .setParameter("tipoPersonal", PlantillaApu.Tipo.PERSONAL)
                .getResultList()
                .stream()
                .findFirst();
    }

    /** Plan 036 — lookup administrativo restringido a plantillas SISTEMA. */
    public Optional<PlantillaApu> findSistemaByPublicId(UUID publicId) {
        return find("publicId = ?1 and tipo = ?2", publicId, PlantillaApu.Tipo.SISTEMA)
                .firstResultOptional();
    }

    /**
     * Plan 04 (P-26) — Listado de plantillas por tipo, orden estable por nombre.
     */
    public List<PlantillaApu> listarPorTipo(PlantillaApu.Tipo tipo) {
        return find("tipo = :tipo order by nombre", Parameters.with("tipo", tipo))
                .list();
    }

    /** Plan 036 — listado administrativo paginado y estable de plantillas SISTEMA. */
    public List<PlantillaApu> listarSistemaAdmin(String q, int page, int size) {
        if (q != null && !q.isBlank()) {
            String filtro = "%" + escaparLike(q.toLowerCase()) + "%";
            return find(
                            "tipo = ?1 and (lower(nombre) like ?2 escape '!' or "
                                    + "lower(coalesce(descripcionRubro, '')) like ?2 escape '!') order by nombre, id",
                            PlantillaApu.Tipo.SISTEMA,
                            filtro)
                    .page(Page.of(page, size))
                    .list();
        }
        return find("tipo = ?1 order by nombre, id", PlantillaApu.Tipo.SISTEMA)
                .page(Page.of(page, size))
                .list();
    }

    public long contarSistemaAdmin(String q) {
        if (q != null && !q.isBlank()) {
            String filtro = "%" + escaparLike(q.toLowerCase()) + "%";
            return count(
                    "tipo = ?1 and (lower(nombre) like ?2 escape '!' or "
                            + "lower(coalesce(descripcionRubro, '')) like ?2 escape '!')",
                    PlantillaApu.Tipo.SISTEMA,
                    filtro);
        }
        return count("tipo", PlantillaApu.Tipo.SISTEMA);
    }

    private static String escaparLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /** Plan 001 — paginated PostgreSQL FTS search with owner scope. */
    public List<PlantillaApu> buscar(
            Long callerUsuarioId, List<PlantillaApu.Tipo> tipos, String q, int page, int size) {
        boolean fts = q != null && !q.isBlank();
        String sql = "select p.* from plantilla_apu p "
                + "where p.tipo in (:tipos) and (p.tipo = 'SISTEMA' "
                + "or (p.tipo = 'PERSONAL' and p.usuario_id = :caller)) ";
        if (fts) {
            sql += "and p.busqueda_fts @@ websearch_to_tsquery('public.spanish_unaccent', :q) "
                    + "order by ts_rank_cd(p.busqueda_fts, "
                    + "websearch_to_tsquery('public.spanish_unaccent', :q)) desc, "
                    + "case p.tipo when 'SISTEMA' then 0 else 1 end, p.nombre, p.public_id";
        } else {
            sql += "order by case p.tipo when 'SISTEMA' then 0 else 1 end, p.nombre, p.public_id";
        }
        var query = getEntityManager()
                .createNativeQuery(sql, PlantillaApu.class)
                .setParameter("tipos", tipos.stream().map(Enum::name).toList())
                .setParameter("caller", callerUsuarioId)
                .setFirstResult(page * size)
                .setMaxResults(size);
        if (fts) query.setParameter("q", q.trim());
        @SuppressWarnings("unchecked")
        List<PlantillaApu> result = query.getResultList();
        return result;
    }

    /** Plan 001 — count using exactly the same owner/type/FTS filters. */
    public long contarBusqueda(Long callerUsuarioId, List<PlantillaApu.Tipo> tipos, String q) {
        boolean fts = q != null && !q.isBlank();
        String sql = "select count(*) from plantilla_apu p "
                + "where p.tipo in (:tipos) and (p.tipo = 'SISTEMA' "
                + "or (p.tipo = 'PERSONAL' and p.usuario_id = :caller)) ";
        if (fts) {
            sql += "and p.busqueda_fts @@ websearch_to_tsquery('public.spanish_unaccent', :q)";
        }
        var query = getEntityManager()
                .createNativeQuery(sql)
                .setParameter("tipos", tipos.stream().map(Enum::name).toList())
                .setParameter("caller", callerUsuarioId);
        if (fts) query.setParameter("q", q.trim());
        return ((Number) query.getSingleResult()).longValue();
    }

    /**
     * Plan 04 (P-26) — Listado de plantillas PERSONAL del caller, orden estable
     * por nombre.
     */
    public List<PlantillaApu> listarPorTipoYDuenno(PlantillaApu.Tipo tipo, Long usuarioId) {
        return find(
                        "tipo = :tipo and usuarioId = :uid order by nombre",
                        Parameters.with("tipo", tipo).and("uid", usuarioId))
                .list();
    }
}
