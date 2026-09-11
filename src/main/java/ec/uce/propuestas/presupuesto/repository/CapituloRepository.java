package ec.uce.propuestas.presupuesto.repository;

import ec.uce.propuestas.presupuesto.entity.Capitulo;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acceso a {@code capitulo}. Reglas de negocio en los services. */
@ApplicationScoped
public class CapituloRepository implements PanacheRepositoryBase<Capitulo, Long> {

    /**
     * WU-03 — Resolución por {@code public_id} (UUIDv7) con scope de owner. Travesía owner:
     * Capitulo → Presupuesto → Proyecto → caller. Cualquier fila de un proyecto ajeno devuelve
     * {@link Optional#empty()} (mapeo a 404 — nunca 403). El {@code public_id} nunca se
     * usa como FK ni como grant de autorización.
     */
    public Optional<Capitulo> findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId) {
        return getEntityManager()
                .createQuery(
                        "select c from Capitulo c, Presupuesto p, Proyecto pr "
                                + "where c.publicId = :publicId and c.presupuestoId = p.id "
                                + "and p.proyectoId = pr.id and pr.usuarioId = :caller",
                        Capitulo.class)
                .setParameter("publicId", publicId)
                .setParameter("caller", callerUsuarioId)
                .getResultList()
                .stream()
                .findFirst();
    }

    /**
     * Plan 022 — resolución de un capítulo por UUID público restringido al
     * presupuesto del path. El chequeo de owner vive en el caller
     * ({@code CapituloService} resuelve primero el presupuesto con su propio
     * owner-scope); este método sólo acota la búsqueda al {@code presupuestoId}
     * para que un UUIDv7 de OTRA versión del mismo proyecto no se cuele como si
     * perteneciera al árbol del path.
     */
    public Optional<Capitulo> findByPublicIdEnPresupuesto(UUID publicId, Long presupuestoId) {
        return find(
                        "publicId = :publicId and presupuestoId = :presupuestoId",
                        Parameters.with("publicId", publicId).and("presupuestoId", presupuestoId))
                .firstResultOptional();
    }

    /**
     * Plan 022 — ¿el UUIDv7 existe en OTRO presupuesto del mismo owner? Sirve
     * para distinguir los dos errores del contrato cuando el capítulo no está en
     * el presupuesto del path:
     * <ul>
     *   <li>{@code true} → 400 {@code validacion} («pertenece a otro
     *       presupuesto», caso cross-version del mismo proyecto o de otro
     *       proyecto del caller).</li>
     *   <li>{@code false} → 404 {@code no-encontrado} (inexistente o de otro
     *       usuario — nunca se revela la existencia de filas ajenas, RNF-05).</li>
     * </ul>
     * La consulta viaja por la travesía de owner (Capitulo → Presupuesto →
     * Proyecto → caller) precisamente para no filtrar existencia ajena.
     */
    public boolean existeEnOtroPresupuestoDelOwner(UUID publicId, Long presupuestoId, Long callerUsuarioId) {
        return !getEntityManager()
                .createQuery(
                        "select c.id from Capitulo c, Presupuesto p, Proyecto pr "
                                + "where c.publicId = :publicId and c.presupuestoId = p.id "
                                + "and p.id <> :presupuestoId and p.proyectoId = pr.id "
                                + "and pr.usuarioId = :caller",
                        Long.class)
                .setParameter("publicId", publicId)
                .setParameter("presupuestoId", presupuestoId)
                .setParameter("caller", callerUsuarioId)
                .setMaxResults(1)
                .getResultList()
                .isEmpty();
    }

    /**
     * Plan 021 — devuelve todos los capítulos (planos, sin jerarquía) de una
     * versión de presupuesto. La recomposición recursiva del árbol vive en
     * {@link ec.uce.propuestas.presupuesto.mapper.PresupuestoMapper#toPresupuestoResponseConInyecciones}.
     *
     * <p>Plan 022 la reutiliza como base de la renumeración atómica: devuelve
     * entidades gestionadas, de modo que reasignar {@code orden}/{@code item}
     * sobre ellas y hacer {@code flush()} deja la sesión y la BD coherentes (no
     * se usan UPDATE masivos, que dejarían la caché de primer nivel stale).</p>
     */
    /** Returns the highest-order direct child, or root when parentId is null. */
    public Optional<Capitulo> ultimoHijo(Long presupuestoId, Long parentId) {
        if (parentId == null) {
            return find(
                            "presupuestoId = :pid and parentId is null order by orden desc",
                            Parameters.with("pid", presupuestoId))
                    .firstResultOptional();
        }
        return find(
                        "presupuestoId = :pid and parentId = :parentId order by orden desc",
                        Parameters.with("pid", presupuestoId).and("parentId", parentId))
                .firstResultOptional();
    }

    public List<Capitulo> listarPorPresupuesto(Long presupuestoId) {
        return find("presupuestoId = :presupuestoId", Parameters.with("presupuestoId", presupuestoId))
                .list();
    }

    /**
     * Plan 030 (P-35/P-36) — variante acotada y ordenada por {@code item}
     * ascendente del listado de capítulos. La unicidad de
     * {@code (presupuesto_id, item)} (V001 §2.9) garantiza un orden total
     * estable: el árbol se reconstruye en memoria sin colisiones ni
     * desempates arbitrarios. Se usa exclusivamente desde
     * {@code VistasCronogramaService} para componer la jerarquía recursiva
     * de la respuesta única de {@code GET /cronogramas/{id}/vistas}.
     */
    public List<Capitulo> listarPorPresupuestoOrdenado(Long presupuestoId) {
        return find("presupuestoId = :presupuestoId order by item", Parameters.with("presupuestoId", presupuestoId))
                .list();
    }

    /**
     * Plan 022 — devuelve los hermanos directos de un padre en un presupuesto,
     * ordenados por {@code orden} ascendente. Pasar {@code parentId = null}
     * lista los capítulos raíz. La lista (entidades gestionadas) es la secuencia
     * sobre la que crear / mover insertan el nodo en su posición destino antes de
     * la renumeración atómica.
     */
    public List<Capitulo> listarHermanosEnPresupuesto(Long presupuestoId, Long parentId) {
        if (parentId == null) {
            return find(
                            "presupuestoId = :pid and parentId is null order by orden",
                            Parameters.with("pid", presupuestoId))
                    .list();
        }
        return find(
                        "presupuestoId = :pid and parentId = :parentId order by orden",
                        Parameters.with("pid", presupuestoId).and("parentId", parentId))
                .list();
    }

    /**
     * Plan 022 — previene ciclos al mover: detecta si {@code candidateParentId}
     * es descendiente (en cualquier nivel) de {@code movingId}, incluyendo el
     * caso trivial {@code candidateParentId == movingId}. Sube por la cadena de
     * padres del candidato hasta encontrar {@code movingId} o agotar la rama.
     *
     * <p>Simplificación documentada (plan 022 STOP (A)): en lugar de
     * {@code WITH RECURSIVE} se recorre la cadena de ancestros con
     * {@code findById}, que además ve el estado en memoria de la sesión (la
     * jerarquía real IESS tiene ≤ 4 niveles y las escrituras no son el camino
     * caliente). El tope defensivo evita colgarse si la BD tuviera un ciclo
     * accidental.</p>
     *
     * <p>Devuelve {@code true} si la mudanza crearía un ciclo (incluido el
     * auto-ciclo).</p>
     */
    public boolean esDescendienteOigual(Long movingId, Long candidateParentId) {
        if (movingId == null || candidateParentId == null) {
            return false;
        }
        Long cursor = candidateParentId;
        int tope = 1024;
        while (cursor != null && tope-- > 0) {
            if (movingId.equals(cursor)) {
                return true;
            }
            Capitulo padre = findById(cursor);
            if (padre == null) {
                return false;
            }
            cursor = padre.parentId;
        }
        return false;
    }
}
