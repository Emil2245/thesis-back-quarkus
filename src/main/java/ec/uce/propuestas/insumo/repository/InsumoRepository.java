package ec.uce.propuestas.insumo.repository;

import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.entity.TipoBase;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acceso a {@code insumo}. Reglas de negocio en los services. */
@ApplicationScoped
public class InsumoRepository implements PanacheRepositoryBase<Insumo, Long> {

    public Optional<Insumo> findByBaseYcodigo(Long baseId, String codigo) {
        return find(
                        "baseId = :baseId and codigo = :codigo",
                        Parameters.with("baseId", baseId).and("codigo", codigo))
                .firstResultOptional();
    }

    /**
     * Variante con filtro adicional de tipo de insumo (compatible con la
     * sección destino del APU). Usada por la carga de plantillas APU para
     * garantizar que un código MO no se reutilice como EQUIPO, etc.
     */
    public Optional<Insumo> findByBaseYcodigoYTipo(Long baseId, String codigo, TipoInsumo tipo) {
        return find(
                        "baseId = :baseId and codigo = :codigo and tipo = :tipo",
                        Parameters.with("baseId", baseId).and("codigo", codigo).and("tipo", tipo))
                .firstResultOptional();
    }

    public Optional<Insumo> findByIdYBase(Long id, Long baseId) {
        return find("id = :id and baseId = :baseId", Parameters.with("id", id).and("baseId", baseId))
                .firstResultOptional();
    }

    /**
     * Plan 07 — lookup de insumo por {@code publicId} UUIDv7 dentro de una base
     * especificada por su {@code BIGINT} interno. La autorización (owner-to-404)
     * se cierra en la capa superior (resource o {@code BaseInsumosService}).
     */
    public Optional<Insumo> findByPublicIdAndBase(UUID publicId, Long baseId) {
        return find(
                        "publicId = :publicId and baseId = :baseId",
                        Parameters.with("publicId", publicId).and("baseId", baseId))
                .firstResultOptional();
    }

    public List<Insumo> listarDeBase(Long baseId) {
        return find("baseId = :baseId", Parameters.with("baseId", baseId)).list();
    }

    public long contarDeBase(Long baseId) {
        return count("baseId = :baseId", Parameters.with("baseId", baseId));
    }

    /** Listado con filtros (tipo, texto, desactualizados) y paginación. */
    public List<Insumo> listarDeBaseConFiltros(
            Long baseId,
            TipoInsumo tipo,
            String q,
            boolean desactualizadosOnly,
            long desactualizadoCorte,
            int pageIndex,
            int pageSize) {
        Filtros f = filtros(baseId, tipo, q, desactualizadosOnly, desactualizadoCorte);
        return find(f.sql, f.params).page(Page.of(pageIndex, pageSize)).list();
    }

    public long contarDeBaseConFiltros(
            Long baseId, TipoInsumo tipo, String q, boolean desactualizadosOnly, long desactualizadoCorte) {
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
        return find(ql.toString(), params).page(Page.of(pageIndex, pageSize)).list();
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

    /**
     * WU-03 — Resolución por {@code public_id} (UUIDv7) con scope de owner. Travesía owner:
     * Insumo → BaseInsumos → caller (PERSONAL o dueño del proyecto PROYECTO). Bases CENTRALES
     * quedan fuera de este seam de USUARIO; una fila de otro dueño devuelve
     * {@link Optional#empty()} (mapeo a 404). El {@code public_id} nunca se usa como FK ni
     * como grant de autorización.
     */
    public Optional<Insumo> findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId) {
        return getEntityManager()
                .createQuery(
                        "select i from Insumo i, BaseInsumos b, Proyecto p "
                                + "where i.publicId = :publicId and i.baseId = b.id "
                                + "and ((b.tipo = :tipoPersonal and b.usuarioId = :caller) "
                                + "  or (b.tipo = :tipoProyecto "
                                + "      and b.proyectoId = p.id and p.usuarioId = :caller))",
                        Insumo.class)
                .setParameter("publicId", publicId)
                .setParameter("caller", callerUsuarioId)
                .setParameter("tipoPersonal", TipoBase.PERSONAL)
                .setParameter("tipoProyecto", TipoBase.PROYECTO)
                .getResultList()
                .stream()
                .findFirst();
    }

    /** Resuelve por UUID público únicamente cuando el insumo pertenece a una base CENTRAL. */
    public Optional<Insumo> findCentralByPublicId(UUID publicId) {
        return getEntityManager()
                .createQuery(
                        "select i from Insumo i, BaseInsumos b "
                                + "where i.publicId = :publicId and i.baseId = b.id and b.tipo = :tipoCentral",
                        Insumo.class)
                .setParameter("publicId", publicId)
                .setParameter("tipoCentral", TipoBase.CENTRAL)
                .getResultList()
                .stream()
                .findFirst();
    }

    private record Filtros(String sql, Parameters params) {}

    private static Filtros filtros(
            Long baseId, TipoInsumo tipo, String q, boolean desactualizadosOnly, long desactualizadoCorte) {
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
