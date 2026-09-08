package ec.uce.propuestas.usuario.audit.repository;

import ec.uce.propuestas.usuario.audit.dto.LogActividadFiltros;
import ec.uce.propuestas.usuario.audit.entity.LogActividad;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Data access for the owner-agnostic SUPER_ADMIN activity-log listing. */
@ApplicationScoped
public class LogActividadRepository implements PanacheRepositoryBase<LogActividad, Long> {

    public ResultadoPagina listar(LogActividadFiltros filtros) {
        Map<String, Object> parametros = new LinkedHashMap<>();
        String where = construirWhere(filtros, parametros);

        TypedQuery<LogActividad> listado = getEntityManager()
                .createQuery(
                        "select l from LogActividad l left join fetch l.usuario u"
                                + where
                                + " order by l.createdAt desc, l.publicId desc",
                        LogActividad.class)
                .setFirstResult(filtros.page() * filtros.size())
                .setMaxResults(filtros.size());
        parametros.forEach(listado::setParameter);
        List<LogActividad> items = listado.getResultList();

        TypedQuery<Long> conteo = getEntityManager()
                .createQuery("select count(l) from LogActividad l left join l.usuario u" + where, Long.class);
        parametros.forEach(conteo::setParameter);
        return new ResultadoPagina(items, conteo.getSingleResult());
    }

    private String construirWhere(LogActividadFiltros filtros, Map<String, Object> parametros) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        if (filtros.usuarioId() != null) {
            where.append(" and u.publicId = :usuarioId");
            parametros.put("usuarioId", filtros.usuarioId());
        }
        if (filtros.evento() != null) {
            where.append(" and l.evento = :evento");
            parametros.put("evento", filtros.evento());
        }
        if (filtros.desde() != null) {
            where.append(" and l.createdAt >= :desde");
            parametros.put("desde", filtros.desde());
        }
        if (filtros.hasta() != null) {
            where.append(" and l.createdAt <= :hasta");
            parametros.put("hasta", filtros.hasta());
        }
        return where.toString();
    }

    public record ResultadoPagina(List<LogActividad> items, long total) {}
}
