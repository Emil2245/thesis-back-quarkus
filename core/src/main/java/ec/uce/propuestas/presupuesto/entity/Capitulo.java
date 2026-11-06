package ec.uce.propuestas.presupuesto.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Capítulo del árbol presupuestario (V001 §2.9). Sin {@code created_at} /
 * {@code updated_at} ni callbacks de ciclo de vida — el DDL canónico no los
 * declara (la jerarquía se reconstruye por mutación explícita en planes 020+).
 * La identidad externa inmutable {@code publicId} UUIDv7 se mapea con
 * {@code @Generated(event = EventType.INSERT)} + {@code insertable=false,
 * updatable=false}; los FK {@code presupuestoId} y {@code parentId}
 * permanecen BIGINT (internos).
 */
@Entity
@Table(name = "capitulo")
public class Capitulo extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** WU-03 — Identidad externa inmutable UUIDv7 generada por la columna {@code public_id}. */
    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", insertable = false, updatable = false)
    public UUID publicId;

    @Column(name = "presupuesto_id", nullable = false)
    public Long presupuestoId;

    @Column(name = "parent_id")
    public Long parentId;

    @Column(nullable = false, length = 20)
    public String item;

    @Column(nullable = false, columnDefinition = "TEXT")
    public String descripcion;

    @Column(nullable = false)
    public Short orden;

    @Column(nullable = false, precision = 14, scale = 6)
    public BigDecimal total = BigDecimal.ZERO;
}
