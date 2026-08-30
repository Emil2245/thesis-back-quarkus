package ec.uce.propuestas.apu.repository;

import ec.uce.propuestas.apu.entity.ApuDetalle;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acceso a {@code apu_detalle}. Reglas de negocio en los services. */
@ApplicationScoped
public class ApuDetalleRepository implements PanacheRepositoryBase<ApuDetalle, Long> {

    public List<ApuDetalle> listarDeSeccion(Long seccionId) {
        return find("seccionId = :seccionId order by orden", Parameters.with("seccionId", seccionId))
                .list();
    }

    public Optional<ApuDetalle> findByIdYSeccionDeApu(Long detalleId, Long apuId) {
        return find(
                        "select d from ApuDetalle d where d.id = :id and d.seccionId in (select s.id from ApuSeccion s where s.apuId = :apuId)",
                        Parameters.with("id", detalleId).and("apuId", apuId))
                .firstResultOptional();
    }

    public List<ApuDetalle> findByInsumoId(Long insumoId) {
        return find("insumoId", insumoId).list();
    }

    public long countByInsumoId(Long insumoId) {
        return count("insumoId", insumoId);
    }

    public short maxOrdenEnSeccion(Long seccionId) {
        List<ApuDetalle> items =
                find("seccionId = ?1 order by orden desc", seccionId).list();
        return items.isEmpty() ? (short) 0 : items.get(0).orden;
    }

    /**
     * WU-03 — Resolución por {@code public_id} (UUIDv7) con scope de owner. Travesía owner:
     * ApuDetalle → ApuSeccion → Apu → Presupuesto → Proyecto → caller. Cualquier fila de
     * un proyecto ajeno devuelve {@link Optional#empty()}. La junta al {@code BIGINT}
     * interno es la única ruta usada para escribir/editar a partir de este punto.
     */
    public Optional<ApuDetalle> findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId) {
        return getEntityManager()
                .createQuery(
                        "select d from ApuDetalle d, ApuSeccion s, Apu a, Presupuesto p, Proyecto pr "
                                + "where d.publicId = :publicId and d.seccionId = s.id and s.apuId = a.id "
                                + "and a.presupuestoId = p.id and p.proyectoId = pr.id "
                                + "and pr.usuarioId = :caller",
                        ApuDetalle.class)
                .setParameter("publicId", publicId)
                .setParameter("caller", callerUsuarioId)
                .getResultList()
                .stream()
                .findFirst();
    }
}
