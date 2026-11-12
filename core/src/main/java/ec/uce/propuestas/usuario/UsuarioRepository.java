package ec.uce.propuestas.usuario;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        return find("publicId = :publicId and id = :caller", Map.of("publicId", publicId, "caller", callerUsuarioId))
                .firstResultOptional();
    }

    /**
     * Plan 034 — Resolución admin por {@code public_id} UUIDv7 sin owner-scope
     * (es lectura administrativa; el caller ya pasó el filtro
     * {@code @RolesAllowed("SUPER_ADMIN")}). Devuelve {@link Optional#empty()}
     * si no existe (mapeo a 404).
     */
    public Optional<Usuario> findByPublicId(UUID publicId) {
        return find("publicId", publicId).firstResultOptional();
    }

    /**
     * Plan 034 — Listado paginado admin con filtros opcionales.
     *
     * <p>El filtro {@code q} aplica un ILIKE seguro (con escape de
     * {@code %} y {@code _}) sobre {@code nombre} y {@code email}. El
     * orden por defecto es {@code fechaCreacion DESC} con secundario
     * {@code id ASC} (orden estable entre invocaciones).
     *
     * @param q filtro de texto opcional (puede ser {@code null} o blank)
     * @param activo filtro opcional; {@code null} desactiva el filtro
     * @param page índice de página (>= 0)
     * @param size tamaño de página (1..200)
     */
    public List<Usuario> listar(String q, Boolean activo, int page, int size) {
        String where = construirWhere(q, activo);
        return find(where + " order by createdAt desc, id asc", construirParametros(q, activo))
                .page(Page.of(page, size))
                .list();
    }

    public long contar(String q, Boolean activo) {
        String where = construirWhere(q, activo);
        return count(where, construirParametros(q, activo));
    }

    private static String construirWhere(String q, Boolean activo) {
        StringBuilder where = new StringBuilder("1 = 1");
        if (q != null && !q.isBlank()) {
            // ILIKE con escape: usamos '!' como carácter de escape para evitar
            // ambigüedad de '\\' en el parser HQL/JPQL.
            where.append(" and (lower(nombre) like :q escape '!' or lower(email) like :q escape '!')");
        }
        if (activo != null) {
            where.append(" and activo = :activo");
        }
        return where.toString();
    }

    private static Map<String, Object> construirParametros(String q, Boolean activo) {
        Map<String, Object> params = new HashMap<>();
        if (q != null && !q.isBlank()) {
            // Escapar el carácter de escape '!' primero, luego % y _ se interpretan literalmente.
            String escaped = q.replace("!", "!!").replace("%", "!%").replace("_", "!_");
            params.put("q", "%" + escaped.toLowerCase() + "%");
        }
        if (activo != null) {
            params.put("activo", activo);
        }
        return params;
    }
}
