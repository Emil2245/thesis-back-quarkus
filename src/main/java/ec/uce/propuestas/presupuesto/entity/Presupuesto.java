package ec.uce.propuestas.presupuesto.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/**
 * Versión de presupuesto (1..n por proyecto; una vigente). WU-03 — la
 * identidad externa inmutable {@code publicId} UUIDv7 se mapea con
 * {@code @Generated(event = EventType.INSERT)} + {@code insertable=false,
 * updatable=false}; el FK {@code proyectoId} permanece BIGINT (interno).
 */
@Entity
@Table(name = "presupuesto")
public class Presupuesto extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** WU-03 — Identidad externa inmutable UUIDv7 generada por la columna {@code public_id}. */
    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", insertable = false, updatable = false)
    public UUID publicId;

    @Column(name = "proyecto_id", nullable = false)
    public Long proyectoId;

    @Column(nullable = false)
    public Short version;

    @Column(name = "es_vigente", nullable = false)
    public boolean esVigente;

    @Column(name = "origen_id")
    public Long origenId;

    public String notas;

    @Column(name = "porcentaje_indirecto", precision = 5, scale = 4)
    public BigDecimal porcentajeIndirecto;

    @Column(nullable = false, precision = 14, scale = 6)
    public BigDecimal total = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void onInsert() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
