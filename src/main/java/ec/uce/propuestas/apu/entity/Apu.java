package ec.uce.propuestas.apu.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

@Entity
@Table(name = "apu")
public class Apu extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** WU-03 — Identidad externa inmutable UUIDv7 generada por la columna {@code public_id}. */
    @Generated(event = EventType.INSERT)
    @Column(name = "public_id", insertable = false, updatable = false)
    public UUID publicId;

    @Column(name = "presupuesto_id", nullable = false)
    public Long presupuestoId;

    @Column(nullable = false, length = 20)
    public String codigo;

    @Column(nullable = false)
    public String descripcion;

    @Column(nullable = false, length = 10)
    public String unidad;

    @Column(name = "porcentaje_indirecto", precision = 5, scale = 4)
    public BigDecimal porcentajeIndirecto;

    @Column(name = "especificacion_tecnica", columnDefinition = "TEXT")
    public String especificacionTecnica;

    @Column(name = "costo_directo", nullable = false, precision = 14, scale = 6)
    public BigDecimal costoDirecto = BigDecimal.ZERO;

    @Column(name = "costo_indirecto", nullable = false, precision = 14, scale = 6)
    public BigDecimal costoIndirecto = BigDecimal.ZERO;

    @Column(name = "costo_total", nullable = false, precision = 14, scale = 6)
    public BigDecimal costoTotal = BigDecimal.ZERO;

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
