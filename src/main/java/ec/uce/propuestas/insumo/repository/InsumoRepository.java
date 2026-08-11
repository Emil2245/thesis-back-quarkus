package ec.uce.propuestas.insumo.repository;

import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Acceso a {@code insumo}. Reglas de negocio en los services. */
@ApplicationScoped
public class InsumoRepository implements PanacheRepositoryBase<Insumo, Long> {

    public Optional<Insumo> findByBaseYcodigo(Long baseId, String codigo) {
        return find("baseId = :baseId and codigo = :codigo",
                Parameters.with("baseId", baseId).and("codigo", codigo))
                .firstResultOptional();
    }

    public Optional<Insumo> findByIdYBase(Long id, Long baseId) {
        return find("id = :id and baseId = :baseId",
                Parameters.with("id", id).and("baseId", baseId))
                .firstResultOptional();
    }

    public List<Insumo> listarDeBase(Long baseId) {
        return find("baseId = :baseId", Parameters.with("baseId", baseId)).list();
    }

    public long contarDeBase(Long baseId) {
        return count("baseId = :baseId", Parameters.with("baseId", baseId));
    }

    /** Listado con filtros (tipo, texto, desactualizados) y paginación. */
    public List<Insumo> listarDeBaseConFiltros(Long baseId, TipoInsumo tipo, String q,
                                               boolean desactualizadosOnly, long desactualizadoCorte,
                                               int pageIndex, int pageSize) {
        Filtros f = filtros(baseId, tipo, q, desactualizadosOnly, desactualizadoCorte);
        return find(f.sql, f.params)
                .page(Page.of(pageIndex, pageSize))
                .list();
    }

    public long contarDeBaseConFiltros(Long baseId, TipoInsumo tipo, String q,
                                       boolean desactualizadosOnly, long desactualizadoCorte) {
        Filtros f = filtros(baseId, tipo, q, desactualizadosOnly, desactualizadoCorte);
        return count(f.sql, f.params);
    }

    public List<Insumo> listarDeBases(List<Long> baseIds, String q, int pageIndex, int pageSize) {
        if (baseIds.isEmpty()) return List.of();
        StringBuilder ql = new StringBuilder("baseId in :baseIds");
        Parameters params = Parameters.with("baseIds", baseIds);
        if (q != null && !q.isBlank()) {
            ql.append(" and (lower(codigo) like :q or lower(str(descripcion)) like :q)");
            params = params.and("q", "%" + q.toLowerCase() + "%");
        }
        ql.append(" order by codigo");
        return find(ql.toString(), params)
                .page(Page.of(pageIndex, pageSize))
                .list();
    }

    public long contarDeBases(List<Long> baseIds, String q) {
        if (baseIds.isEmpty()) return 0L;
        StringBuilder ql = new StringBuilder("baseId in :baseIds");
        Parameters params = Parameters.with("baseIds", baseIds);
        if (q != null && !q.isBlank()) {
            ql.append(" and (lower(codigo) like :q or lower(str(descripcion)) like :q)");
            params = params.and("q", "%" + q.toLowerCase() + "%");
        }
        return count(ql.toString(), params);
    }

    private record Filtros(String sql, Parameters params) {
    }

    private static Filtros filtros(Long baseId, TipoInsumo tipo, String q,
                                   boolean desactualizadosOnly, long desactualizadoCorte) {
        StringBuilder ql = new StringBuilder("baseId = :baseId");
        Parameters params = Parameters.with("baseId", baseId);
        if (tipo != null) {
            ql.append(" and tipo = :tipo");
            params = params.and("tipo", tipo);
        }
        if (desactualizadosOnly) {
            ql.append(" and updatedAt < :corte");
            params = params.and("corte", Instant.ofEpochMilli(desactualizadoCorte));
        }
        if (q != null && !q.isBlank()) {
            ql.append(" and (lower(codigo) like :q or lower(str(descripcion)) like :q)");
            params = params.and("q", "%" + q.toLowerCase() + "%");
        }
        return new Filtros(ql.toString(), params);
    }
}