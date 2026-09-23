package ec.uce.propuestas.plantilla.repository;

import ec.uce.propuestas.plantilla.entity.PlantillaApu;
import ec.uce.propuestas.plantilla.entity.PlantillaProyecto;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PlantillaProyectoRepository implements PanacheRepositoryBase<PlantillaProyecto, Long> {

    /**
     * WU-03 — Resolución por {@code public_id} (UUIDv7) con scope de owner. El caller debe
     * ser el {@code usuarioId} de la plantilla. Cualquier otra fila —incluidas las
     * SISTEMA, que no tienen dueño— devuelve {@link Optional#empty()} (mapeo a 404).
     */
    public Optional<PlantillaProyecto> findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId) {
        return find(
                        "publicId = :publicId and usuarioId = :caller",
                        Parameters.with("publicId", publicId).and("caller", callerUsuarioId))
                .firstResultOptional();
    }

    /**
     * Plan 044 — lo que el caller puede leer y aplicar: las SISTEMA y sus
     * PERSONALES. Una PERSONAL ajena devuelve {@link Optional#empty()} (RNF-05).
     */
    public Optional<PlantillaProyecto> findVisibleByPublicId(UUID publicId, Long callerUsuarioId) {
        return find(
                        "publicId = :publicId and (tipo = :sistema or usuarioId = :caller)",
                        Parameters.with("publicId", publicId)
                                .and("sistema", PlantillaApu.Tipo.SISTEMA)
                                .and("caller", callerUsuarioId))
                .firstResultOptional();
    }

    /**
     * Plan 044 — listado visible al caller: SISTEMA primero (por nombre) y
     * después sus PERSONALES (más recientes primero, el orden que ya tenía
     * el listado owner-only de Plan 06).
     */
    public List<PlantillaProyecto> listarVisibles(Long callerUsuarioId) {
        List<PlantillaProyecto> sistema = find(
                        "tipo = :sistema order by nombre, id", Parameters.with("sistema", PlantillaApu.Tipo.SISTEMA))
                .list();
        List<PlantillaProyecto> propias = find(
                        "usuarioId = :caller order by fechaCreacion desc", Parameters.with("caller", callerUsuarioId))
                .list();
        return java.util.stream.Stream.concat(sistema.stream(), propias.stream())
                .toList();
    }

    public Optional<PlantillaProyecto> findSistemaByPublicId(UUID publicId) {
        return find("publicId = ?1 and tipo = ?2", publicId, PlantillaApu.Tipo.SISTEMA)
                .firstResultOptional();
    }

    public List<PlantillaProyecto> listarSistemaAdmin(String q, int page, int size) {
        if (q != null && !q.isBlank()) {
            return find(
                            "tipo = ?1 and (lower(nombre) like ?2 escape '!' or "
                                    + "lower(coalesce(descripcion, '')) like ?2 escape '!') order by nombre, id",
                            PlantillaApu.Tipo.SISTEMA,
                            filtroLike(q))
                    .page(Page.of(page, size))
                    .list();
        }
        return find("tipo = ?1 order by nombre, id", PlantillaApu.Tipo.SISTEMA)
                .page(Page.of(page, size))
                .list();
    }

    public long contarSistemaAdmin(String q) {
        if (q != null && !q.isBlank()) {
            return count(
                    "tipo = ?1 and (lower(nombre) like ?2 escape '!' or "
                            + "lower(coalesce(descripcion, '')) like ?2 escape '!')",
                    PlantillaApu.Tipo.SISTEMA,
                    filtroLike(q));
        }
        return count("tipo", PlantillaApu.Tipo.SISTEMA);
    }

    private static String filtroLike(String q) {
        String escapado = q.toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escapado + "%";
    }
}
