package ec.uce.propuestas.insumo.repository;

import ec.uce.propuestas.apu.entity.ApuDetalle;
import ec.uce.propuestas.insumo.entity.Insumo;
import ec.uce.propuestas.insumo.entity.TipoBase;
import ec.uce.propuestas.insumo.entity.TipoInsumo;
import ec.uce.propuestas.motor.SeccionTipo;
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

    public long contarUsosEnApuDetalle(Long insumoId) {
        return getEntityManager()
                .createQuery("select count(d) from ApuDetalle d where d.insumoId = :insumoId", Long.class)
                .setParameter("insumoId", insumoId)
                .getSingleResult();
    }

    /**
     * Plan 032 (P-18) — una fila de APU que referencia el insumo, con el APU al
     * que pertenece y el tipo de su sección. El {@link ApuDetalle} viaja entero
     * porque el override se resuelve con
     * {@code ApuCalculoService.overrideDeDetalle(detalle, tipo)}; duplicar aquí
     * ese {@code switch} sería una segunda tabla capaz de divergir.
     */
    public record UsoEnApu(UUID apuPublicId, String codigo, String descripcion, SeccionTipo tipo, ApuDetalle detalle) {}

    /**
     * Plan 032 (P-18) — filas de APU de <b>este proyecto</b> que referencian el
     * insumo, con su sección (bloque M/N/O/P) y el APU al que pertenecen.
     *
     * <p>El filtro por {@code proyectoId} no es decorativo: {@code apu_detalle}
     * no lleva proyecto encima, y un insumo referenciado desde dos proyectos
     * enseñaría los APUs del otro (RNF-05).</p>
     *
     * <p>{@code ApuDetalle} / {@code ApuSeccion} / {@code Apu} /
     * {@code Presupuesto} sólo declaran columnas {@code Long}, sin asociaciones
     * JPA, así que los joins van por condición explícita. Todo se trae en una
     * sola consulta: un APU puede tener decenas de filas y el N+1 se nota.</p>
     *
     * <p>No se filtra por {@code es_vigente}: el recuento que bloquea el
     * borrado ({@link #contarUsosEnApuDetalle}) tampoco lo hace, y ambas cifras
     * deben coincidir.</p>
     */
    public List<UsoEnApu> listarUsosEnApuDetalle(Long insumoId, Long proyectoId) {
        return getEntityManager()
                .createQuery("""
                        select a.publicId, a.codigo, a.descripcion, s.tipo, d
                        from ApuDetalle d
                        join ApuSeccion s on s.id = d.seccionId
                        join Apu a on a.id = s.apuId
                        join Presupuesto p on p.id = a.presupuestoId
                        where d.insumoId = :insumoId and p.proyectoId = :proyectoId
                        order by a.codigo, s.orden, d.orden
                        """, Object[].class)
                .setParameter("insumoId", insumoId)
                .setParameter("proyectoId", proyectoId)
                .getResultList()
                .stream()
                .map(f ->
                        new UsoEnApu((UUID) f[0], (String) f[1], (String) f[2], (SeccionTipo) f[3], (ApuDetalle) f[4]))
                .toList();
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
