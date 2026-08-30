package ec.uce.propuestas.admin.repository;

import ec.uce.propuestas.admin.entity.LogActividad;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class LogActividadRepository implements PanacheRepositoryBase<LogActividad, Long> {

    public List<LogActividad> buscar(
            Long usuarioId, String evento, Instant desde, Instant hasta, int pageIndex, int pageSize) {
        StringBuilder hql = new StringBuilder("1=1");
        Map<String, Object> params = new HashMap<>();
        if (usuarioId != null) {
            hql.append(" and usuarioId = :uid");
            params.put("uid", usuarioId);
        }
        if (evento != null && !evento.isBlank()) {
            hql.append(" and evento = :ev");
            params.put("ev", evento);
        }
        if (desde != null) {
            hql.append(" and createdAt >= :desde");
            params.put("desde", desde);
        }
        if (hasta != null) {
            hql.append(" and createdAt <= :hasta");
            params.put("hasta", hasta);
        }
        return find(hql.toString(), Sort.descending("createdAt"), params)
                .page(Page.of(pageIndex, pageSize))
                .list();
    }

    public long contar(Long usuarioId, String evento, Instant desde, Instant hasta) {
        StringBuilder hql = new StringBuilder("1=1");
        Map<String, Object> params = new HashMap<>();
        if (usuarioId != null) {
            hql.append(" and usuarioId = :uid");
            params.put("uid", usuarioId);
        }
        if (evento != null && !evento.isBlank()) {
            hql.append(" and evento = :ev");
            params.put("ev", evento);
        }
        if (desde != null) {
            hql.append(" and createdAt >= :desde");
            params.put("desde", desde);
        }
        if (hasta != null) {
            hql.append(" and createdAt <= :hasta");
            params.put("hasta", hasta);
        }
        return count(hql.toString(), params);
    }
}
