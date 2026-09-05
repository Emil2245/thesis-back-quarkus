package ec.uce.propuestas.presupuesto.repository;

import ec.uce.propuestas.presupuesto.entity.Presupuesto;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class PresupuestoRepository implements PanacheRepositoryBase<Presupuesto, Long> {

    /**
     * WU-03 — Resolución por {@code public_id} (UUIDv7) con scope de owner. Travesía owner:
     * Presupuesto → Proyecto → caller. Cualquier fila de un proyecto ajeno devuelve
     * {@link Optional#empty()} (mapeo a 404 — nunca 403). El {@code public_id} nunca se
     * usa como FK ni como grant de autorización.
     */
    public Optional<Presupuesto> findByPublicIdAndOwnerScope(UUID publicId, Long callerUsuarioId) {
        return getEntityManager()
                .createQuery(
                        "select p from Presupuesto p, Proyecto pr "
                                + "where p.publicId = :publicId and p.proyectoId = pr.id "
                                + "and pr.usuarioId = :caller",
                        Presupuesto.class)
                .setParameter("publicId", publicId)
                .setParameter("caller", callerUsuarioId)
                .getResultList()
                .stream()
                .findFirst();
    }

    /** Variante con proyecto resuelto (sigue siendo owner-scoped). */
    public Optional<Presupuesto> findByPublicIdAndProyecto(Long proyectoId, UUID publicId, Long callerUsuarioId) {
        return getEntityManager()
                .createQuery(
                        "select p from Presupuesto p, Proyecto pr "
                                + "where p.publicId = :publicId and p.proyectoId = pr.id "
                                + "and pr.id = :proyectoId and pr.usuarioId = :caller",
                        Presupuesto.class)
                .setParameter("publicId", publicId)
                .setParameter("proyectoId", proyectoId)
                .setParameter("caller", callerUsuarioId)
                .getResultList()
                .stream()
                .findFirst();
    }

    public Optional<Presupuesto> findVigenteDeProyecto(Long proyectoId) {
        return find("proyectoId = :proyectoId and esVigente = true", Parameters.with("proyectoId", proyectoId))
                .firstResultOptional();
    }

    /**
     * Plan 021 — devuelve todas las versiones de un proyecto ordenadas por
     * {@code version} descendente (la más reciente primero). Sin paginación
     * (la práctica IESS muestra ≤ 5–10 versiones por proyecto; STOP (D) del plan
     * si se supera la marca de 200 versiones — no se introduce paginación).
     */
    public List<Presupuesto> listarVersiones(Long proyectoId) {
        return find("proyectoId = :proyectoId order by version desc", Parameters.with("proyectoId", proyectoId))
                .list();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Plan 024 (P-31) — versionado, vigente única y comparación
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Plan 024 — versión mayor registrada para el proyecto, o {@code null} si
     * el proyecto todavía no tiene ninguna versión. SQL nativo con
     * {@code LIMIT 1} para no cargar la lista completa (la práctica IESS
     * mantiene ≤ 5–10 versiones por proyecto).
     */
    public Short maxVersionDeProyecto(Long proyectoId) {
        Object value = getEntityManager()
                .createNativeQuery("select max(version) from presupuesto where proyecto_id = ?1")
                .setParameter(1, proyectoId)
                .getSingleResult();
        if (value == null) {
            return null;
        }
        return ((Number) value).shortValue();
    }

    /**
     * Plan 024 — bloqueo pesimista de la fila del proyecto
     * ({@code SELECT id FROM proyecto WHERE id = ?1 FOR UPDATE}) para
     * serializar la sección crítica «leer max(version) → insertar nueva
     * versión» del deep copy y evitar que dos POSTs concurrentes del mismo
     * proyecto elijan el mismo {@code version}. Sin esta guarda, dos
     * inserciones simultáneas podrían ambas computar {@code max + 1} y
     * chocar con el índice único {@code UNIQUE (proyecto_id, version)}
     * (V001 §2.8). La operación es data-access only y vive en este módulo
     * ({@code presupuesto/repository}) sin necesidad de modificar
     * {@code proyecto/*}.
     */
    public void lockProyectoRow(Long proyectoId) {
        getEntityManager()
                .createNativeQuery("select id from proyecto where id = ?1 for update")
                .setParameter(1, proyectoId)
                .getSingleResult();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Plan 028 (P-33) — sección crítica del alta/configuración de cronograma
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Plan 028 — bloqueo pesimista de la fila del presupuesto
     * ({@code SELECT id FROM presupuesto WHERE id = ?1 FOR UPDATE}) para
     * serializar «comprobar 1:1 → insertar cronograma» y la revalidación de
     * una reconfiguración. La fila del cronograma no sirve como lock en el
     * alta porque todavía no existe; la {@code UNIQUE (presupuesto_id)} de
     * V001 §2.13 sigue siendo la defensa final. Data-access only: no cambia
     * {@link #findRubrosCubiertosPorCronograma(Long)} ni el módulo
     * {@code proyecto}.
     */
    public void lockPresupuestoRow(Long presupuestoId) {
        getEntityManager()
                .createNativeQuery("select id from presupuesto where id = ?1 for update")
                .setParameter(1, presupuestoId)
                .getSingleResult();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Plan 025 (P-32) — validación de integridad: cobertura por cronograma
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Plan 025 — devuelve los IDs internos ({@code BIGINT}) de los rubros del
     * presupuesto indicado que están cubiertos por una actividad cuyo
     * cronograma pertenece al MISMO presupuesto. La consulta usa SQL nativo y
     * queda estrictamente contenida al esquema del presupuesto del path
     * (cruzando {@code actividad → cronograma → presupuesto_id}); un rubro
     * vinculado a una actividad de OTRO presupuesto (p. ej. versión hermana
     * del mismo proyecto) NO entra en el conjunto, preservando el aislamiento
     * cross-presupuesto del contrato P-32 (TC-P32-12).
     *
     * <p>El {@code UNIQUE (actividad.rubro_id)} de V001 §2.13 garantiza que un
     * rubro tiene a lo sumo una actividad; el {@code DISTINCT} es defensivo y
     * no cambia el resultado.</p>
     *
     * <p>Si el presupuesto no tiene cronograma, no hay filas en
     * {@code cronograma}, por lo que el conjunto devuelto es vacío: cualquier
     * rubro del presupuesto queda, por defecto, como {@code sinActividad}.</p>
     *
     * <p>Operación data-access only: no introduce entidades JPA
     * {@code Actividad}/{@code Cronograma} (diferidas a I-08) y no toca
     * {@code proyecto/*}.</p>
     */
    public Set<Long> findRubrosCubiertosPorCronograma(Long presupuestoId) {
        @SuppressWarnings("unchecked")
        List<Number> ids = getEntityManager()
                .createNativeQuery("select distinct a.rubro_id "
                        + "from actividad a "
                        + "join cronograma c on c.id = a.cronograma_id "
                        + "where c.presupuesto_id = ?1")
                .setParameter(1, presupuestoId)
                .getResultList();
        return ids.stream().map(Number::longValue).collect(Collectors.toUnmodifiableSet());
    }
}
